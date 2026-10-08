package dev.cobra.client.fabric;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.module.Features;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public final class CobraFabric implements ClientModInitializer {
    public static KeyBinding MENU, ZOOM, FREELOOK, WAYPOINT;
    public static final FabricRender RENDER = new FabricRender();
    private static Perspective savedPerspective;
    private static Boolean savedSmooth;

    @Override
    public void onInitializeClient() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Cobra.init(new FabricPlatform());

        // cosmetics (wings, halo, cat ears, …) drawn on every player model
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, renderer, helper, context) -> {
                    if (renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer player) {
                        registerCosmetics(helper, player);
                    }
                });

        MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cobra.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
        ZOOM = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cobra.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));
        FREELOOK = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cobra.freelook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY));
        WAYPOINT = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cobra.waypoint", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                tick(client);
            } catch (Throwable t) {                 // a Cobra bug must never stop the game
                if (!tickErrorShown) {
                    tickErrorShown = true;
                    System.err.println("[Abyss] error in the client tick (kept running):");
                    t.printStackTrace();
                }
            }
        });

        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.util.Identifier.of("cobra", "hud"), (ctx, tickCounter) -> {
            try {
                pollClicks(mc);
                Cobra.renderHud(RENDER.with(ctx));
            } catch (Throwable t) {
                if (!hudErrorShown) {
                    hudErrorShown = true;
                    t.printStackTrace();
                }
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() && player == mc.player) {
                double reach = mc.crosshairTarget instanceof EntityHitResult ehr
                        ? ehr.getPos().distanceTo(player.getEyePos())
                        : player.getEyePos().distanceTo(entity.getBoundingBox().getCenter());
                Cobra.onAttack(entity, reach);
            }
            return ActionResult.PASS;
        });

        ClientLifecycleEvents.CLIENT_STARTED.register(c -> applyWindowIcon(c));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            if (FabricPlatform.savedGamma != null) Cobra.platform.setFullbright(false);
            Cobra.save();
        });
    }

    /** Set by the VANILLA MENU button; cleared once you're in a world, so Cobra's menu comes back. */
    public static boolean vanillaTitle;
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(net.minecraft.util.Identifier.of("cobra", "cobra"));
    private static boolean leftDown, rightDown;

    /** Mouse clicks for CPS / Keystrokes, polled every frame (the mouse hook changed in 1.21.9). */
    private static void pollClicks(MinecraftClient mc) {
        long w = mc.getWindow().getHandle();
        boolean l = GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (mc.currentScreen == null) {
            if (l && !leftDown) Cobra.onMouseButton(0);
            if (r && !rightDown) Cobra.onMouseButton(1);
        }
        leftDown = l;
        rightDown = r;
    }

    private static boolean flagged;

    private static void tick(MinecraftClient mc) {
        if (!flagged) {
            flagged = true;
            // not built for 1.21.11: kept out of the menu instead of showing modules that do nothing
            for (dev.cobra.client.core.module.Module m : new dev.cobra.client.core.module.Module[]{
                    }) {
                m.hidden = true;
                if (m.isEnabled()) m.toggle();
            }
        }
        if (mc.world != null) vanillaTitle = false;
        while (MENU.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new CobraMenuScreen(null));
        }
        while (WAYPOINT.wasPressed()) {
            Features.Waypoints wp = Cobra.get(Features.Waypoints.class);
            if (wp.isEnabled()) wp.addFromKey();
        }
        Features.Waypoints wpName = Cobra.get(Features.Waypoints.class);
        if (wpName.toName != null) {                       // just added with the key: type its name
            Features.Waypoints.Point pt = wpName.toName;
            wpName.toName = null;
            if (mc.currentScreen == null && mc.world != null) {
                CobraMenuScreen screen = new CobraMenuScreen(null);
                screen.nameWaypoint(pt);
                mc.setScreen(screen);
            }
        }

        // Freelook: third-person camera that turns independently of the player
        boolean edge = false;
        while (FREELOOK.wasPressed()) edge = true;
        Features.Freelook fl = Cobra.get(Features.Freelook.class);
        boolean was = fl.active;
        boolean flKey = FREELOOK.isPressed() || Cobra.platform.rawKeyDown(Cobra.platform.bindCode("freelook"));   // Alt can be eaten by key clashes
        if (mc.player != null) fl.update(flKey && mc.currentScreen == null, edge, mc.player.getYaw(), mc.player.getPitch());
        else fl.active = false;
        if (fl.active && !was) {
            savedPerspective = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (!fl.active && was && savedPerspective != null) {
            mc.options.setPerspective(savedPerspective);
            savedPerspective = null;
        }

        // Zoom
        Features.Zoom zoom = Cobra.get(Features.Zoom.class);
        // read the key directly too: C is also vanilla's "Save Toolbar" key, and a clash can leave isPressed() false
        boolean zoomKey = ZOOM.isPressed() || Cobra.platform.rawKeyDown(Cobra.platform.bindCode("zoom"));
        boolean held = zoom.isEnabled() && zoomKey && mc.currentScreen == null && mc.player != null;
        if (held && !zoom.held && zoom.cinematic.on()) {
            savedSmooth = mc.options.smoothCameraEnabled;
            mc.options.smoothCameraEnabled = true;
        } else if (!held && zoom.held && savedSmooth != null) {
            mc.options.smoothCameraEnabled = savedSmooth;
            savedSmooth = null;
        }
        zoom.held = held;

        updateHitColor(mc);
        syncDebugToggles(mc);
        syncMotionBlur(mc);
        tickCount++;
        syncSky(mc);
        syncMenuBlur(mc);
        packKey(mc);
        worldLabels(mc);
        killEffect(mc);
        if (mc.world != null || mc.currentScreen != null) syncGlint(mc);
        Cobra.tick();
    }

    // ------------------------------------------------------------------ sky packs

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerCosmetics(net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper helper,
                                          net.minecraft.client.render.entity.PlayerEntityRenderer renderer) {
        helper.register(new dev.cobra.client.fabric.cosmetics.CosmeticsFeature((net.minecraft.client.render.entity.feature.FeatureRendererContext) renderer));
    }

    private static boolean tickErrorShown, hudErrorShown, packKeyDown;

    /**
     * Cosmetic particle effects: sparkles, hearts, flames, snow… drifting off the wings (or around
     * the halo / body), and an optional trail at your feet while you move. For you and for other
     * Cobra players close by.
     */
    private static void cosmeticParticles(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || mc.isPaused() || tickCount % 2 != 0) return;
        java.util.Random rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (net.minecraft.entity.player.PlayerEntity pl : mc.world.getPlayers()) {
            if (pl.isInvisible() || pl.squaredDistanceTo(mc.player) > 48 * 48) continue;
            if (pl == mc.player && mc.options.getPerspective().isFirstPerson()) continue;   // not in your own face
            java.util.Map<String, String> wear = dev.cobra.client.fabric.cosmetics.CosmeticsFeature.wearingOf(pl);
            String fx = wear.get("fx");
            if (fx == null) continue;
            net.minecraft.particle.ParticleEffect effect = switch (fx) {
                case "hearts" -> net.minecraft.particle.ParticleTypes.HEART;
                case "flames" -> net.minecraft.particle.ParticleTypes.FLAME;
                case "soulfire" -> net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME;
                case "snow" -> net.minecraft.particle.ParticleTypes.SNOWFLAKE;
                case "magic" -> net.minecraft.particle.ParticleTypes.WITCH;
                case "petals" -> net.minecraft.particle.ParticleTypes.CHERRY_LEAVES;
                case "notes" -> net.minecraft.particle.ParticleTypes.NOTE;
                default -> net.minecraft.particle.ParticleTypes.END_ROD;
            };
            boolean slowOnes = fx.equals("hearts") || fx.equals("notes");
            if (slowOnes && tickCount % 8 != 0) continue;
            double yaw = Math.toRadians(pl.bodyYaw);
            double bx = -Math.sin(yaw), bz = Math.cos(yaw);              // facing direction
            double sx = Math.cos(yaw), sz = Math.sin(yaw);               // to the side
            double x = pl.getX(), y = pl.getY(), z = pl.getZ();
            double px, py, pz;
            if (wear.containsKey("wings")) {                             // off a wing tip
                double side = rnd.nextBoolean() ? 1 : -1, out = 0.5 + rnd.nextDouble() * 0.8;
                px = x - bx * 0.45 + sx * side * out;
                py = y + 1.1 + rnd.nextDouble() * 0.9;
                pz = z - bz * 0.45 + sz * side * out;
            } else if (wear.containsKey("halo")) {                       // round the halo
                double a = rnd.nextDouble() * Math.PI * 2;
                px = x + Math.cos(a) * 0.35;
                py = y + 2.15;
                pz = z + Math.sin(a) * 0.35;
            } else {                                                     // around the body
                double a = rnd.nextDouble() * Math.PI * 2;
                px = x + Math.cos(a) * 0.55;
                py = y + 0.3 + rnd.nextDouble() * 1.6;
                pz = z + Math.sin(a) * 0.55;
            }
            double vy = fx.equals("snow") || fx.equals("petals") ? -0.02 : 0.02;
            mc.world.addParticleClient(effect, false, false, px, py, pz, (rnd.nextDouble() - 0.5) * 0.02, vy, (rnd.nextDouble() - 0.5) * 0.02);
            // the trail: a few at your feet while you move
            if (wear.containsKey("trail") && pl.getVelocity().horizontalLengthSquared() > 0.002) {
                mc.world.addParticleClient(effect, false, false, x + (rnd.nextDouble() - 0.5) * 0.4, y + 0.1, z + (rnd.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
            }
        }
    }

    /** Entities we gave a label (TNT / item timers), so it can be taken off again. */
    private static final java.util.Set<Integer> LABELLED = new java.util.HashSet<>();

    /**
     * TNT Timer and Despawn Timer: a label over lit TNT (seconds left) and over dropped items (time
     * until they despawn). Shown only on your screen; nothing is sent to the server.
     */
    private static void worldLabels(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            LABELLED.clear();
            return;
        }
        Features.TntTimer tnt = Cobra.get(Features.TntTimer.class);
        Features.DespawnTimer items = Cobra.get(Features.DespawnTimer.class);
        java.util.Set<Integer> now = new java.util.HashSet<>();
        if (tnt.isEnabled() || items.isEnabled()) {
            double r2 = Math.pow(items.range.f(), 2);
            for (net.minecraft.entity.Entity e : mc.world.getEntities()) {
                if (tnt.isEnabled() && e instanceof net.minecraft.entity.TntEntity t) {
                    t.setCustomName(net.minecraft.text.Text.literal(tnt.label(t.getFuse())));
                    t.setCustomNameVisible(true);
                    now.add(e.getId());
                } else if (items.isEnabled() && e instanceof net.minecraft.entity.ItemEntity it && it.squaredDistanceTo(mc.player) <= r2) {
                    String time = items.label(it.getItemAge());
                    String name = items.showName.on() ? "\u00a7f" + it.getStack().getName().getString() + "\u00a77 x" + it.getStack().getCount() + "  " : "";
                    it.setCustomName(net.minecraft.text.Text.literal(name + time));
                    it.setCustomNameVisible(true);
                    now.add(e.getId());
                }
            }
        }
        // take the labels off things that no longer need one (module off, out of range)
        for (Integer id : LABELLED) {
            if (now.contains(id)) continue;
            net.minecraft.entity.Entity e = mc.world.getEntityById(id);
            if (e != null) {
                e.setCustomName(null);
                e.setCustomNameVisible(false);
            }
        }
        LABELLED.clear();
        LABELLED.addAll(now);
    }

    /** Kill Effect: when someone you hit in the last 3 s dies, a (harmless, client-only) lightning bolt there. */
    private static void killEffect(MinecraftClient mc) {
        Features.KillEffect k = Cobra.get(Features.KillEffect.class);
        if (!k.isEnabled() || mc.world == null) return;
        Object t = k.recentTarget();
        if (!(t instanceof net.minecraft.entity.LivingEntity le)) return;
        if (k.playersOnly.on() && !(le instanceof net.minecraft.entity.player.PlayerEntity)) return;
        if (!le.isDead() && le.getHealth() > 0) return;
        k.consumed();
        net.minecraft.entity.Entity bolt = net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(mc.world, net.minecraft.entity.SpawnReason.EVENT);
        if (!(bolt instanceof net.minecraft.entity.LightningEntity l)) return;
        l.setCosmetic(true);
        l.refreshPositionAfterTeleport(le.getX(), le.getY(), le.getZ());
        mc.world.addEntity(l);
        if (k.sound.on()) mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1f);
    }

    /** Menu Blur: keeps the game's menu blur at the chosen strength while the module is on. */
    private static void syncMenuBlur(MinecraftClient mc) {
        Features.MenuBlur b = Cobra.get(Features.MenuBlur.class);
        if (!b.isEnabled()) return;
        int want = Math.round(b.strength.f());
        var opt = mc.options.getMenuBackgroundBlurriness();
        if (opt.getValue() != want) opt.setValue(want);
    }

    /** Pack Organizer: its key opens the resource packs screen from anywhere. */
    private static void packKey(MinecraftClient mc) {
        Features.PackOrganizer po = Cobra.get(Features.PackOrganizer.class);
        int code = po.open.code();
        boolean down = po.isEnabled() && code >= 0 && mc.currentScreen == null && Cobra.platform.rawKeyDown(code);
        if (down && !packKeyDown) {
            mc.setScreen(new net.minecraft.client.gui.screen.pack.PackScreen(mc.getResourcePackManager(),
                    manager -> mc.options.refreshResourcePacks(manager), mc.getResourcePackDir(),
                    net.minecraft.text.Text.translatable("resourcePack.title")));
        }
        packKeyDown = down;
    }
    private static String skyApplied;
    private static int tickCount, skyRetries;

    /** Sky module: turns the chosen cobra-sky pack on (others off) and reloads resources once. */
    private static void syncSky(MinecraftClient mc) {
        if (mc.getResourcePackManager() == null || mc.getOverlay() != null) return;
        String want = Cobra.get(Features.Sky.class).packFile();
        String key = want == null ? "" : want;
        if (key.equals(skyApplied)) {
            // the Resource Packs screen can switch it off behind our back: check every 2 s
            if (key.isEmpty() || tickCount % 40 != 0) return;
            if (mc.getResourcePackManager().getEnabledIds().contains("file/" + want) || skyRetries >= 2) return;
            skyRetries++;
        } else {
            skyRetries = 0;
        }
        if (skyApplied == null) {                   // first tick: remember what's on, don't reload
            skyApplied = "";
            for (String id : mc.getResourcePackManager().getEnabledIds()) if (id.contains("cobra-sky-")) skyApplied = id.substring(id.indexOf("cobra-sky-"));
            if (key.equals(skyApplied)) return;
        }
        try {
            var pm = mc.getResourcePackManager();
            pm.scanPacks();
            for (String id : new java.util.ArrayList<>(pm.getEnabledIds())) if (id.contains("cobra-sky-")) pm.disable(id);
            if (want != null) pm.enable("file/" + want);
            mc.options.refreshResourcePacks(pm);    // saves and reloads (a short loading screen)
            skyApplied = key;
        } catch (Throwable t) {
            Cobra.get(Features.Sky.class).problem = "Couldn't switch the sky";
            skyApplied = key;
        }
    }

    // ------------------------------------------------------------------ glint colorizer

    private static final String[] GLINTS = {"textures/misc/enchanted_glint_item.png", "textures/misc/enchanted_glint_armor.png"};
    private static int glintApplied;                       // colour we tinted to (0 = vanilla)
    private static final java.util.Map<String, Object> GLINT_TEX = new java.util.HashMap<>();

    /**
     * Glint Colorizer: paints a tinted copy of the glint picture straight into Minecraft's own glint
     * textures on the GPU. The texture object (with its repeat/scroll settings) stays vanilla's,
     * which is why swapping in a new texture didn't really work. Re-applied after resource reloads;
     * turned off, the original picture is written back.
     */
    private static void syncGlint(MinecraftClient mc) {
        dev.cobra.client.core.module.Extras.GlintColorizer gc = Cobra.get(dev.cobra.client.core.module.Extras.GlintColorizer.class);
        int want = gc.isEnabled() ? gc.color.argb() | 0xFF000000 : 0;
        try {
            boolean reloaded = false;
            for (String path : GLINTS) {
                Object t = mc.getTextureManager().getTexture(net.minecraft.util.Identifier.ofVanilla(path));
                if (GLINT_TEX.containsKey(path) && GLINT_TEX.get(path) != t) reloaded = true;
            }
            if (want == glintApplied && !(reloaded && want != 0)) return;
            for (String path : GLINTS) {
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.ofVanilla(path);
                net.minecraft.client.texture.AbstractTexture tex = mc.getTextureManager().getTexture(id);
                var res = mc.getResourceManager().getResource(id);
                if (res.isEmpty() || tex == null || tex.getGlTexture() == null) continue;
                net.minecraft.client.texture.NativeImage img;
                try (java.io.InputStream in = res.get().getInputStream()) {
                    img = net.minecraft.client.texture.NativeImage.read(in);
                }
                try {
                    if (want != 0) {
                        int tr = want >> 16 & 255, tg = want >> 8 & 255, tb = want & 255;
                        for (int y = 0; y < img.getHeight(); y++) {
                            for (int x = 0; x < img.getWidth(); x++) {
                                int c = img.getColorArgb(x, y);
                                int lum = Math.max(c >> 16 & 255, Math.max(c >> 8 & 255, c & 255));   // keep the shimmer's brightness
                                img.setColorArgb(x, y, (c >>> 24) << 24 | (lum * tr / 255) << 16 | (lum * tg / 255) << 8 | lum * tb / 255);
                            }
                        }
                    }
                    com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().writeToTexture(tex.getGlTexture(), img);
                } finally {
                    img.close();
                }
                GLINT_TEX.put(path, tex);
            }
            glintApplied = want;
            gc.problem = null;
        } catch (Throwable t) {
            gc.problem = "Couldn't hook in";
            glintApplied = want;   // don't retry every tick
        }
    }

    // ------------------------------------------------------------------ screen recorder

    private static final java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger();
    private static long nextCapture, lastRequest, frameSeq;
    private static final java.util.concurrent.ExecutorService FRAME_COPY = java.util.concurrent.Executors.newFixedThreadPool(2, run -> {
        Thread t = new Thread(run, "cobra-recorder-copy");
        t.setDaemon(true);
        return t;
    });

    /**
     * Once per frame: if recording and a frame is due, ask for an async GPU read-back of the finished
     * frame (the game doesn't wait for it). Up to three read-backs overlap, so a slow GPU fence can't
     * throttle the video to a few frames per second; a watchdog clears requests that never came back.
     */
    public static void captureFrame() {
        if (Cobra.platform == null) return;
        Features.Recorder r = Cobra.get(Features.Recorder.class);
        if (!r.isEnabled() || !r.rec.wantsFrames()) return;
        long now = System.nanoTime();
        if (inFlight.get() > 0 && now - lastRequest > 1_000_000_000L) inFlight.set(0);   // lost callbacks
        if (inFlight.get() >= 3) return;
        long every = 1_000_000_000L / Math.max(1, Integer.parseInt(r.fps.get()));
        if (now < nextCapture) return;
        nextCapture = Math.max(nextCapture + every, now - every);
        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.client.gl.Framebuffer fb = mc.getFramebuffer();
        if (fb == null) return;
        int down = fb.textureHeight >= 2160 && r.resolution.is("1080p") ? 2 : 1;   // 4K screens: halve on the GPU
        inFlight.incrementAndGet();
        lastRequest = now;
        final long seq = ++frameSeq;                    // frames can finish copying out of order
        try {
            net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(fb, down, image -> FRAME_COPY.execute(() -> {
                // the 8 MB copy happens here, off the render thread, so recording doesn't cost FPS
                try {
                    int[] px = image.copyPixelsArgb();
                    r.rec.offer(px, image.getWidth(), image.getHeight(), r.ffmpeg(), r.folder(), r.resolution.get(), r.quality.get(), seq);
                } finally {
                    image.close();
                    inFlight.updateAndGet(v -> Math.max(0, v - 1));
                }
            }));
        } catch (Throwable t) {
            inFlight.updateAndGet(v -> Math.max(0, v - 1));
            r.problem = "Couldn't capture frames";
        }
    }

    // ------------------------------------------------------------------ velocity motion blur

    private static net.minecraft.client.texture.NativeImageBackedTexture motionTex, colorTex;
    private static int colorLast = -1;

    /** Colors module: writes contrast / saturation / brightness into the effect's colour texture. */
    private static void syncColorData() {
        if (colorTex == null) return;
        int argb = Cobra.get(Features.Colors.class).argb();
        if (argb == colorLast) return;
        try {
            net.minecraft.client.texture.NativeImage img = colorTex.getImage();
            if (img == null) return;
            img.setColorArgb(0, 0, argb);
            colorTex.upload();
            colorLast = argb;
        } catch (Throwable ignored) {}
    }
    private static int motionLast = -1;

    /** The 1x1 texture the blur shader reads the frame's motion from (registered before the effect loads). */
    private static void ensureMotionTexture(MinecraftClient mc) {
        if (motionTex != null) return;
        motionTex = new net.minecraft.client.texture.NativeImageBackedTexture("cobra motion blur", 1, 1, false);
        net.minecraft.client.texture.NativeImage img = motionTex.getImage();
        if (img != null) img.setColorArgb(0, 0, 0x00808000);
        motionTex.upload();
        // Colors module data (contrast / saturation / brightness), read by the same effect
        colorTex = new net.minecraft.client.texture.NativeImageBackedTexture("cobra colors", 1, 1, false);
        net.minecraft.client.texture.NativeImage ci = colorTex.getImage();
        if (ci != null) ci.setColorArgb(0, 0, 0x00808080);
        colorTex.upload();
        mc.getTextureManager().registerTexture(net.minecraft.util.Identifier.of("cobra", "textures/effect/color_data.png"), colorTex);
        mc.getTextureManager().registerTexture(net.minecraft.util.Identifier.of("cobra", "color_data"), colorTex);
        // both spellings, whichever way the post-effect loader resolves "cobra:motion_data"
        mc.getTextureManager().registerTexture(net.minecraft.util.Identifier.of("cobra", "textures/effect/motion_data.png"), motionTex);
        mc.getTextureManager().registerTexture(net.minecraft.util.Identifier.of("cobra", "motion_data"), motionTex);

    }

    /** Called from getFov once per rendered frame: how far the view turned → blur direction and length. */
    public static void motionFrame(net.minecraft.client.render.Camera camera, double fov) {
        if (motionTex == null || camera == null || Cobra.platform == null) return;
        Features.MotionBlur mb = Cobra.get(Features.MotionBlur.class);
        if (!mb.isEnabled() || MinecraftClient.getInstance().world == null || MinecraftClient.getInstance().currentScreen != null) {
            mb.reset();
            if (motionLast != 0x00808000) {                       // no leftover blur (Colors may keep the effect on)
                try {
                    net.minecraft.client.texture.NativeImage img = motionTex.getImage();
                    if (img != null) {
                        img.setColorArgb(0, 0, 0x00808000);
                        motionTex.upload();
                    }
                } catch (Throwable ignored) {}
                motionLast = 0x00808000;
            }
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        double aspect = mc.getWindow().getFramebufferWidth() / (double) Math.max(1, mc.getWindow().getFramebufferHeight());
        double speed = 0;
        if (mc.player != null) {
            net.minecraft.util.math.Vec3d v = mc.player.getVelocity();
            speed = Math.sqrt(v.x * v.x + v.z * v.z);
        }
        int argb = mb.frame(camera.getYaw(), camera.getPitch(), fov, aspect, speed);
        if (argb == motionLast) return;
        try {
            net.minecraft.client.texture.NativeImage img = motionTex.getImage();
            if (img == null) return;
            img.setColorArgb(0, 0, argb);
            motionTex.upload();
            motionLast = argb;
            mb.problem = null;
        } catch (Throwable t) { mb.problem = "Motion data upload failed: " + t.getClass().getSimpleName(); }
    }

    /**
     * Motion Blur: Cobra's post effect (cobra:post_effect/motion_blur.json) smears each frame along the view's motion.
     * Only takes the slot when no vanilla effect (spectating a creeper,
     * spider, …) is using it, and gives it back when turned off.
     */
    private static void syncMotionBlur(MinecraftClient mc) {
        if (mc.gameRenderer == null) return;
        try {
            dev.cobra.client.fabric.mixin.GameRendererInvoker gr = (dev.cobra.client.fabric.mixin.GameRendererInvoker) mc.gameRenderer;
            Features.MotionBlur mb = Cobra.get(Features.MotionBlur.class);
            boolean colors = Cobra.get(Features.Colors.class).isEnabled();
            net.minecraft.util.Identifier cur = gr.cobra$getPostProcessorId();
            boolean ours = cur != null && cur.getNamespace().equals("cobra");
            if ((mb.isEnabled() || colors) && mc.world != null && (mc.currentScreen == null || colors && !mb.isEnabled())) {
                ensureMotionTexture(mc);
                syncColorData();
                net.minecraft.util.Identifier want = net.minecraft.util.Identifier.of("cobra", "motion_blur");
                if (cur == null || ours && !cur.equals(want)) gr.cobra$setPostProcessor(want);
            } else if (ours) {
                gr.cobra$clearPostProcessor();
            }
        } catch (Throwable t) {
            Cobra.get(Features.MotionBlur.class).problem = "Couldn't start the effect";
        }
    }

    private static Boolean lastHitboxes, lastChunkBorders;

    /**
     * Hitboxes / Chunk Borders drive vanilla's own debug views (F3+B / F3+G) when the module is
     * switched; F3+B and F3+G still work on their own in between.
     */
    private static void syncDebugToggles(MinecraftClient mc) {
        boolean hb = Cobra.get(dev.cobra.client.core.module.Extras.Hitboxes.class).isEnabled();
        boolean cb = Cobra.get(dev.cobra.client.core.module.Extras.ChunkBorders.class).isEnabled();
        try {
            if (lastHitboxes == null || hb != lastHitboxes) setDebug(mc, "entity_hitboxes", hb);
            if (lastChunkBorders == null || cb != lastChunkBorders) setDebug(mc, "chunk_borders", cb);
        } catch (Throwable t) {
            Cobra.get(dev.cobra.client.core.module.Extras.Hitboxes.class).problem = "Couldn't hook in";
            Cobra.get(dev.cobra.client.core.module.Extras.ChunkBorders.class).problem = "Couldn't hook in";
        }
        lastHitboxes = hb;
        lastChunkBorders = cb;
    }

    private static void setDebug(MinecraftClient mc, String entry, boolean on) {
        if (mc.debugHudEntryList == null) return;
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.ofVanilla(entry);
        if (mc.debugHudEntryList.isEntryVisible(id) != on) mc.debugHudEntryList.toggleVisibility(id);
    }

    /** Vanilla's hurt tint: red, 70% of the entity's own colour kept (ARGB, alpha = colour kept). */
    private static final int VANILLA_HIT = 0xB3FF0000;
    private static int appliedHit = VANILLA_HIT;

    /** Hit Color: recolours the red hurt flash (rows 0-7 of the 16x16 overlay texture). Render thread. */
    private static void updateHitColor(MinecraftClient mc) {
        try {
            Features.HitColor hc = Cobra.get(Features.HitColor.class);
            int want;
            if (hc.isEnabled()) {
                int c = hc.argb();
                int keep = 255 - (c >>> 24);          // opacity setting = how strong the tint is
                want = keep << 24 | (c & 0xFFFFFF);
            } else want = VANILLA_HIT;
            if (want == appliedHit || mc.gameRenderer == null) return;
            net.minecraft.client.texture.NativeImageBackedTexture tex =
                    ((dev.cobra.client.fabric.mixin.OverlayTextureAccessor) mc.gameRenderer.getOverlayTexture()).cobra$texture();
            net.minecraft.client.texture.NativeImage img = tex.getImage();
            if (img == null) return;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) img.setColorArgb(x, y, want);
            tex.upload();
            appliedHit = want;
        } catch (Throwable t) {
            Cobra.get(Features.HitColor.class).problem = "Couldn't hook in";
        }
    }



    private static void applyWindowIcon(MinecraftClient mc) {
        int[] sizes = {16, 32, 48, 128};
        List<ByteBuffer> pixels = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWImage.Buffer images = GLFWImage.malloc(sizes.length, stack);
            for (int i = 0; i < sizes.length; i++) {
                byte[] bytes;
                try (InputStream in = CobraFabric.class.getResourceAsStream("/assets/cobra/icon/icon_" + sizes[i] + ".png")) {
                    if (in == null) return;
                    bytes = in.readAllBytes();
                }
                ByteBuffer file = MemoryUtil.memAlloc(bytes.length);
                file.put(bytes).flip();
                IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
                ByteBuffer rgba = STBImage.stbi_load_from_memory(file, w, h, c, 4);
                MemoryUtil.memFree(file);
                if (rgba == null) return;
                pixels.add(rgba);
                images.position(i);
                images.width(w.get(0));
                images.height(h.get(0));
                images.pixels(rgba);
            }
            images.position(0);
            GLFW.glfwSetWindowIcon(mc.getWindow().getHandle(), images);
        } catch (Throwable ignored) {
            // Wayland-native GLFW doesn't support window icons; the .desktop icon is used instead
        } finally {
            for (ByteBuffer b : pixels) STBImage.stbi_image_free(b);
        }
    }
}

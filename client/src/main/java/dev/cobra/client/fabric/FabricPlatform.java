package dev.cobra.client.fabric;

import org.lwjgl.glfw.GLFW;

import dev.cobra.client.core.Platform;
import dev.cobra.client.fabric.mixin.BossBarHudAccessor;
import dev.cobra.client.fabric.mixin.SimpleOptionAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FabricPlatform implements Platform {
    private static final Comparator<ScoreboardEntry> ORDER = Comparator.comparing(ScoreboardEntry::value, Comparator.reverseOrder())
            .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);

    /** Last world FOV returned by GameRenderer#getFov (after zoom), for waypoint projection. */
    public static volatile double lastFov = 70;
    /** Gamma to restore when fullbright turns off (null when fullbright is off). */
    public static Double savedGamma;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    @Override public String version() { return "1.21.11"; }
    @Override public File gameDir() { return mc.runDirectory; }
    @Override public boolean inWorld() { return mc.player != null && mc.world != null; }
    @Override public boolean screenOpen() { return mc.currentScreen != null; }
    /**
     * F1 hides the HUD. F3 only hides it while the real debug screen is open (and only if you want
     * that): since 1.21.9 "always on" debug views such as hitboxes (F3+B) and chunk borders (F3+G)
     * make shouldShowDebugHud() true too, which used to make the whole Cobra HUD vanish.
     */
    @Override
    public boolean hudHidden() {
        if (mc.options.hudHidden) return true;
        boolean f3Screen;
        try {
            f3Screen = mc.debugHudEntryList.isF3Enabled();
        } catch (Throwable t) {
            f3Screen = false;
        }
        return f3Screen && dev.cobra.client.core.Cobra.get(dev.cobra.client.core.module.Features.Client.class).hideHudOnF3.on();
    }
    @Override public int fps() { return mc.getCurrentFps(); }

    @Override
    public int ping() {
        if (mc.player == null || mc.getNetworkHandler() == null) return 0;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return e == null ? 0 : e.getLatency();
    }

    @Override public double x() { return mc.player == null ? 0 : mc.player.getX(); }
    @Override public double y() { return mc.player == null ? 0 : mc.player.getY(); }
    @Override public double z() { return mc.player == null ? 0 : mc.player.getZ(); }
    @Override public float yaw() { return mc.player == null ? 0 : mc.player.getYaw(); }

    @Override
    public String dimension() {
        return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
    }

    @Override
    public String server() {
        ServerInfo e = mc.getCurrentServerEntry();
        if (e != null) return e.address;
        return mc.isInSingleplayer() ? "singleplayer" : "";
    }

    @Override public String playerName() { return mc.getSession().getUsername(); }

    private KeyBinding binding(Key k) {
        return switch (k) {
            case FORWARD -> mc.options.forwardKey;
            case LEFT -> mc.options.leftKey;
            case BACK -> mc.options.backKey;
            case RIGHT -> mc.options.rightKey;
            case JUMP -> mc.options.jumpKey;
            case SNEAK -> mc.options.sneakKey;
            case SPRINT -> mc.options.sprintKey;
            case ATTACK -> mc.options.attackKey;
            case USE -> mc.options.useKey;
        };
    }

    @Override public boolean key(Key k) { return binding(k).isPressed(); }
    @Override public boolean wasPressed(Key k) { return binding(k).wasPressed(); }
    @Override public void setKey(Key k, boolean down) { binding(k).setPressed(down); }

    @Override
    public List<Object> equipment() {
        List<Object> out = new ArrayList<>();
        if (mc.player == null) return out;
        for (int i = 3; i >= 0; i--) {
            ItemStack s = mc.player.getEquippedStack(new net.minecraft.entity.EquipmentSlot[]{net.minecraft.entity.EquipmentSlot.FEET, net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.CHEST, net.minecraft.entity.EquipmentSlot.HEAD}[i]);
            out.add(s.isEmpty() ? null : s);
        }
        ItemStack hand = mc.player.getMainHandStack();
        out.add(hand.isEmpty() ? null : hand);
        return out;
    }

    @Override
    public String durability(Object stack) {
        if (!(stack instanceof ItemStack s)) return "";
        if (s.isDamageable()) return String.valueOf(s.getMaxDamage() - s.getDamage());
        return s.getCount() > 1 ? String.valueOf(s.getCount()) : "";
    }

    @Override
    public List<Effect> effects() {
        List<Effect> out = new ArrayList<>();
        if (mc.player == null) return out;
        for (StatusEffectInstance e : mc.player.getStatusEffects()) {
            String time;
            if (e.isInfinite()) time = "\u221e";
            else {
                int secs = e.getDuration() / 20;
                time = secs / 60 + ":" + String.format("%02d", secs % 60);
            }
            out.add(new Effect(e.getEffectType(), e.getEffectType().value().getName().getString(), e.getAmplifier(), time));
        }
        return out;
    }

    @Override
    public List<Boss> bosses() {
        List<Boss> out = new ArrayList<>();
        for (ClientBossBar b : ((BossBarHudAccessor) mc.inGameHud.getBossBarHud()).cobra$bossBars().values()) {
            Integer c = b.getColor().getTextFormat().getColorValue();
            out.add(new Boss(b.getName(), b.getPercent(), c == null ? 0xFFFFFF : c));
        }
        return out;
    }

    @Override
    public Sidebar sidebar() {
        if (mc.world == null || mc.player == null) return null;
        Scoreboard sb = mc.world.getScoreboard();
        ScoreboardObjective obj = null;
        Team team = sb.getScoreHolderTeam(mc.player.getNameForScoreboard());
        if (team != null) {
            ScoreboardDisplaySlot slot = ScoreboardDisplaySlot.fromFormatting(team.getColor());
            if (slot != null) obj = sb.getObjectiveForSlot(slot);
        }
        if (obj == null) obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (obj == null) return null;
        NumberFormat fmt = obj.getNumberFormatOr(StyledNumberFormat.RED);
        List<Object> names = new ArrayList<>(), scores = new ArrayList<>();
        List<ScoreboardEntry> entries = sb.getScoreboardEntries(obj).stream().filter(e -> !e.hidden()).sorted(ORDER).limit(15).toList();
        for (ScoreboardEntry e : entries) {
            names.add(Team.decorateName(sb.getScoreHolderTeam(e.owner()), e.name()));
            scores.add(e.formatted(fmt));
        }
        return new Sidebar(obj.getDisplayName(), names, scores);
    }

    @Override public int hurtTime(Object entity) { return entity instanceof LivingEntity le ? le.hurtTime : 0; }
    @Override public int playerHurtTime() { return mc.player == null ? 0 : mc.player.hurtTime; }

    @Override
    public void spawnHitParticles(Object target, int crits, int sharpness) {
        if (!(target instanceof Entity e)) return;
        for (int i = 0; i < crits; i++) mc.particleManager.addEmitter(e, ParticleTypes.CRIT);
        for (int i = 0; i < sharpness; i++) mc.particleManager.addEmitter(e, ParticleTypes.ENCHANTED_HIT);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setFullbright(boolean on) {
        SimpleOptionAccessor gamma = (SimpleOptionAccessor) (Object) mc.options.getGamma();
        if (on) {
            if (savedGamma == null) savedGamma = mc.options.getGamma().getValue();
            gamma.cobra$setValue(16.0);
        } else if (savedGamma != null) {
            gamma.cobra$setValue(savedGamma);
            savedGamma = null;
        }
    }

    @Override public void chat(String message) { mc.inGameHud.getChatHud().addMessage(Text.literal(message)); }

    @Override
    public void title(String title, String subtitle) {
        mc.inGameHud.setTitleTicks(5, 50, 15);
        mc.inGameHud.setSubtitle(Text.literal(subtitle));
        mc.inGameHud.setTitle(Text.literal(title));
    }

    @Override public void clipboard(String s) { mc.keyboard.setClipboard(s); }
    @Override public void runOnMain(Runnable r) { mc.execute(r); }

    @Override
    public void titleAction(TitleAction a) {
        Screen cur = mc.currentScreen;
        switch (a) {
            case SINGLEPLAYER -> mc.setScreen(new SelectWorldScreen(cur));
            case MULTIPLAYER -> mc.setScreen(mc.options.skipMultiplayerWarning ? new MultiplayerScreen(cur) : new MultiplayerWarningScreen(cur));
            case OPTIONS -> mc.setScreen(new OptionsScreen(cur, mc.options));
            case RESOURCE_PACKS -> mc.setScreen(new PackScreen(mc.getResourcePackManager(), manager -> {
                mc.options.refreshResourcePacks(manager);
                mc.setScreen(cur);
            }, mc.getResourcePackDir(), Text.translatable("resourcePack.title")));
            case COBRA_MENU -> mc.setScreen(new CobraMenuScreen(cur));
            case VANILLA_MENU -> {
                CobraFabric.vanillaTitle = true;
                mc.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
            }
            case QUIT -> mc.scheduleStop();
        }
    }

    @Override public void openMenu() { mc.setScreen(new CobraMenuScreen(mc.currentScreen)); }

    // ------------------------------------------------------------------ extras

    private final java.util.Map<String, ItemStack> icons = new java.util.HashMap<>();

    private net.minecraft.item.Item item(String id) {
        return net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(id));
    }

    @Override
    public int countItem(String id) {
        if (mc.player == null) return 0;
        net.minecraft.item.Item it;
        if (id == null) {
            ItemStack h = mc.player.getMainHandStack();
            if (h.isEmpty()) return 0;
            it = h.getItem();
        } else it = item(id);
        int n = 0;
        net.minecraft.entity.player.PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.getItem() == it) n += s.getCount();
        }
        return n;
    }

    @Override
    public Object itemStack(String id) {
        if (id.equals("held")) {
            if (mc.player == null) return null;
            ItemStack h = mc.player.getMainHandStack();
            return h.isEmpty() ? null : h.copyWithCount(1);
        }
        return icons.computeIfAbsent(id, k -> new ItemStack(item(k)));
    }

    @Override
    public List<Teammate> teammates() {
        List<Teammate> out = new ArrayList<>();
        if (mc.player == null || mc.world == null || mc.player.getScoreboardTeam() == null) return out;
        for (net.minecraft.client.network.AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
            if (pl == mc.player || !pl.isTeammate(mc.player)) continue;
            out.add(new Teammate(pl.getName().getString(), pl.distanceTo(mc.player), pl.getHealth()));
        }
        out.sort(Comparator.comparingDouble(t -> t.distance));
        return out;
    }

    @Override
    public void setChunkBorders(boolean on) {}   // debug overlays were reworked in 1.21.9

    @Override
    public void setHitboxes(boolean on) {}

    @Override
    public void command(String command) {
        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand(command);
    }

    // ------------------------------------------------------------------ keys

    @Override
    public boolean rawKeyDown(int code) {
        return code >= 0 && GLFW.glfwGetKey(mc.getWindow().getHandle(), code) == GLFW.GLFW_PRESS;
    }

    @Override
    public String keyName(int code) {
        if (code < 0) return "None";
        return net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
    }

    private static KeyBinding globalBind(String id) {
        return switch (id) {
            case "menu" -> CobraFabric.MENU;
            case "zoom" -> CobraFabric.ZOOM;
            case "freelook" -> CobraFabric.FREELOOK;
            default -> CobraFabric.WAYPOINT;
        };
    }

    @Override
    public int bindCode(String id) {
        KeyBinding b = globalBind(id);
        return b == null ? -1 : net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(b).getCode();
    }

    @Override
    public void setBind(String id, int code) {
        KeyBinding b = globalBind(id);
        if (b == null) return;
        b.setBoundKey(code < 0 ? net.minecraft.client.util.InputUtil.UNKNOWN_KEY : net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(code));
        KeyBinding.updateKeysByCode();
        mc.options.write();
    }

    // ------------------------------------------------------------------ cursor

    private static long cursorHandle;

    /** Custom arrow pointer in menus: ivory on dark theme, charcoal on light theme. */
    @Override
    public void applyCursor(boolean enabled, boolean light) {
        long window = mc.getWindow().getHandle();
        if (window == 0) throw new IllegalStateException("window not ready");
        long old = cursorHandle;
        cursorHandle = 0;
        if (enabled) {
            int size = mc.getWindow().getScaleFactor() >= 3 ? 48 : 32;
            String file = "/assets/cobra/cursor/cursor_" + (light ? "dark" : "light") + "_" + size + ".png";
            try (java.io.InputStream in = FabricPlatform.class.getResourceAsStream(file);
                 org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                if (in != null) {
                    byte[] bytes = in.readAllBytes();
                    java.nio.ByteBuffer fileBuf = org.lwjgl.system.MemoryUtil.memAlloc(bytes.length);
                    fileBuf.put(bytes).flip();
                    java.nio.IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
                    java.nio.ByteBuffer pixels = org.lwjgl.stb.STBImage.stbi_load_from_memory(fileBuf, w, h, c, 4);
                    org.lwjgl.system.MemoryUtil.memFree(fileBuf);
                    if (pixels != null) {
                        org.lwjgl.glfw.GLFWImage img = org.lwjgl.glfw.GLFWImage.malloc(stack);
                        img.set(w.get(0), h.get(0), pixels);
                        int hot = Math.max(1, w.get(0) / 16);   // arrow tip sits in the top-left corner
                        cursorHandle = org.lwjgl.glfw.GLFW.glfwCreateCursor(img, hot, hot);
                        org.lwjgl.stb.STBImage.stbi_image_free(pixels);
                    }
                }
            } catch (java.io.IOException ignored) {}
        }
        org.lwjgl.glfw.GLFW.glfwSetCursor(window, cursorHandle);
        if (old != 0) org.lwjgl.glfw.GLFW.glfwDestroyCursor(old);
    }
    @Override public void closeScreen() { mc.setScreen(null); }

    @Override
    public double[] camera() {
        if (!inWorld()) return null;
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d p = cam.getCameraPos();
        return new double[]{p.x, p.y, p.z, cam.getYaw(), cam.getPitch(), lastFov};
    }

    // ------------------------------------------------------------------ Block Info

    private net.minecraft.block.BlockState target() {
        if (mc.world == null || !(mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)
                || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) return null;
        net.minecraft.block.BlockState st = mc.world.getBlockState(hit.getBlockPos());
        return st.isAir() ? null : st;
    }

    @Override
    public Object targetBlockStack() {
        net.minecraft.block.BlockState st = target();
        if (st == null) return null;
        ItemStack s = new ItemStack(st.getBlock().asItem());
        return s.isEmpty() ? null : s;
    }

    @Override
    public String targetBlockName() {
        net.minecraft.block.BlockState st = target();
        return st == null ? null : st.getBlock().getName().getString();
    }

    @Override
    public String targetBlockId() {
        net.minecraft.block.BlockState st = target();
        return st == null ? null : net.minecraft.registry.Registries.BLOCK.getId(st.getBlock()).toString();
    }

    @Override
    public String playerUuid() {
        return mc.player == null ? null : mc.player.getUuid().toString();
    }
}

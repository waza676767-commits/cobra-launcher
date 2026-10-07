package dev.cobra.client.core.module;

import dev.cobra.client.core.Cobra;
import dev.cobra.client.core.Platform;
import dev.cobra.client.core.Render;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class Features {
    private Features() {}

    private static Platform p() { return Cobra.platform; }

    // ------------------------------------------------------------------ client

    /** Global client options (the SETTINGS tab of the Right Shift menu). Always on. */
    public static final class Client extends Module {
        public final Setting.Mode theme = add(new Setting.Mode("theme", "Theme", "Dark", "Dark", "Light", "Custom"));
        public final Setting.Color themeBg = add(new Setting.Color("themebg", "Theme background", 0xFF15131F),
                new Setting.Cond() { public boolean ok() { return theme.is("Custom"); } });
        public final Setting.Color themeText = add(new Setting.Color("themetext", "Theme text", 0xFFF2EEFF),
                new Setting.Cond() { public boolean ok() { return theme.is("Custom"); } });
        public final Setting.Bool animations = add(new Setting.Bool("animations", "Animations", true));
        public final Setting.Bool particles = add(new Setting.Bool("particles", "Click particles", true));
        public final Setting.Bool blur = add(new Setting.Bool("blur", "Blur behind the Abyss menu", true));
        public final Setting.Mode screenBlur = add(new Setting.Mode("screenblur", "Blur other screens", "Menus", "Off", "Menus", "Menus + inventory"));
        public final Setting.Bind menuKey = add(new Setting.Bind("menukey", "Open menu", -1, "menu"));
        /** Everyone's "Update to the newest" button: fetches the latest Cobra release for the launcher. */
        public final Setting.Action update = add(new Setting.Action("update", "Update to the newest", new Runnable() {
            public void run() { dev.cobra.client.core.SelfUpdate.checkAndDownload(); }
        }));

        /** Use the launcher's colour theme (Settings → Theme) for Cobra's menus. */
        public final Setting.Bool followLauncher = add(new Setting.Bool("followlauncher", "Use the launcher's colours", true));

        /** The launcher's theme colour for {@code key} (bg, fg, accent), or -1 when not given / turned off. */
        public int launcherColour(String key) {
            if (!followLauncher.on()) return -1;
            String v = System.getProperty("cobra.theme." + key);
            if (v == null || v.isEmpty()) return -1;
            try {
                return 0xFF000000 | Integer.parseInt(v.trim(), 16);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        /** Size of the Right Shift menu (it still shrinks to fit small windows). */
        public final Setting.Number menuScale = add(new Setting.Number("menuscale", "Menu size", 100, 70, 130, 5, "%"));
        /** Speed of the menu animations (100 = normal). */
        public final Setting.Number menuSpeed = add(new Setting.Number("menuspeed", "Menu animation speed", 100, 50, 200, 10, "%"));
        /** How strong the Liquid Glass HUD panels are (tint + shine). */
        public final Setting.Number hudGlass = add(new Setting.Number("hudglass", "HUD background strength", 100, 0, 200, 10, "%"));
        /** Hide Cobra's HUD while the F3 debug screen is open (hitboxes / chunk borders never hide it). */
        public final Setting.Bool hideHudOnF3 = add(new Setting.Bool("hidehudf3", "Hide HUD while F3 is open", true));
        /** Look of HUD element backgrounds: plain boxes, or Liquid Glass panels. */
        public final Setting.Mode hudStyle = add(new Setting.Mode("hudstyle", "HUD style", "Liquid Glass", "Classic", "Liquid Glass"));
        /** Cobra icon next to other Cobra players in the Tab list (and let them see yours). */
        public final Setting.Bool tabIcon = add(new Setting.Bool("tabicon", "Abyss icon in Tab", true));
        /** Turns every particle off (explosions, crits, rain splashes, …): cleaner screen and more FPS. */
        public final Setting.Bool noParticles = add(new Setting.Bool("noparticles", "Disable all particles", false));
        /** Colour of highlights in the Cobra menus (selected tab, switches, bars). */
        public final Setting.Mode accentPreset = add(new Setting.Mode("accentpreset", "Accent", "Classic",
                "Classic", "Abyss green", "Ocean", "Violet", "Rose", "Sunset", "Gold", "Custom"));
        public final Setting.Color accent = add(new Setting.Color("accent", "Custom accent", 0xFF7C5CFF),
                new Setting.Cond() { public boolean ok() { return accentPreset.is("Custom"); } });

        private static final String[] PRESETS = {"Abyss green", "Ocean", "Violet", "Rose", "Sunset", "Gold"};
        private static final int[] PRESET_COLORS = {0xFF3DDC84, 0xFF3EA6FF, 0xFF8B6CFF, 0xFFFF5C8A, 0xFFFF8A3D, 0xFFF5C542};

        /** Accent colour, or 0 for the classic black/white look. */
        public int accentArgb() {
            int la = launcherColour("accent");
            if (la != -1 && accentPreset.is("Classic")) return la;       // follows the launcher unless you picked one here
            String p = accentPreset.get();
            if (p.equals("Custom")) return accent.argb() | 0xFF000000;
            for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i].equals(p)) return PRESET_COLORS[i];
            return 0;
        }

        public Client() {
            super("client", "Client", "Abyss Client options", Category.UTILITY, true);
            hidden = true;
        }

        @Override
        public void setEnabled(boolean on) {
            // always on
        }
    }

    // ------------------------------------------------------------------ visual

    /**
     * Screen recorder: records the game to an MP4 (ffmpeg), 1080p or native size at 30/60/120 fps.
     * Start, pause/resume and stop keys; recordings show up in the launcher's Recordings page.
     */
    public static final class Recorder extends Module {
        public final Setting.Bind start = add(new Setting.Bind("start", "Start recording", 298, null));     // F9
        public final Setting.Bind pause = add(new Setting.Bind("pause", "Pause / resume", 299, null));      // F10
        public final Setting.Bind stop = add(new Setting.Bind("stop", "Stop recording", 301, null));        // F12
        public final Setting.Mode fps = add(new Setting.Mode("fps", "Frame rate", "60", "30", "60", "120"));
        public final Setting.Mode resolution = add(new Setting.Mode("resolution", "Resolution", "Window size", "Window size", "1440p", "1080p", "720p"));
        public final Setting.Mode quality = add(new Setting.Mode("quality", "Quality", "High", "Ultra", "High", "Balanced", "Small file"));
        public final Setting.Bool indicator = add(new Setting.Bool("indicator", "Show REC in the corner", true));
        /** Auto: record with the graphics card outside the game when possible (no FPS loss). */
        public final Setting.Mode capture = add(new Setting.Mode("capture", "Capture", "Auto (lightest)", "Auto (lightest)", "Game frames"));

        private boolean external() {
            return capture.is("Auto (lightest)") && dev.cobra.client.core.ExternalCapture.available(ffmpeg());
        }

        public final dev.cobra.client.core.ScreenRecorder rec = new dev.cobra.client.core.ScreenRecorder();
        private boolean sDown, pDown, xDown;
        private long extStart;

        public Recorder() { super("recorder", "Screen Recorder", "Record 1080p at 30/60/120 fps with your own keys", Category.UTILITY, false); }

        @Override
        public void onTick() {
            boolean menus = Cobra.platform.screenOpen();
            boolean s = !menus && start.code() >= 0 && Cobra.platform.rawKeyDown(start.code());
            boolean p = !menus && pause.code() >= 0 && Cobra.platform.rawKeyDown(pause.code());
            boolean x = !menus && stop.code() >= 0 && Cobra.platform.rawKeyDown(stop.code());
            try {
                tickKeys(s, p, x);
            } finally {
                sDown = s;
                pDown = p;
                xDown = x;
            }
        }

        private boolean tipShown;

        private void tickKeys(boolean s, boolean p, boolean x) {
            if (s && !sDown && !dev.cobra.client.core.ExternalCapture.running() && external()) {
                problem = null;
                try {
                    dev.cobra.client.core.ExternalCapture.start(ffmpeg(), folder(), Integer.parseInt(fps.get()), quality.get());
                    Cobra.platform.chat("\u00a7c\u25cf\u00a7r Recording with your graphics card (" + fps.get() + " fps)");
                } catch (Exception e) {
                    Cobra.platform.chat("\u00a7cCouldn't start: " + e.getMessage());
                }
                sDown = true;
                return;
            }
            if (p && !pDown && dev.cobra.client.core.ExternalCapture.running()) {
                try {
                    dev.cobra.client.core.ExternalCapture.togglePause(ffmpeg(), folder(), Integer.parseInt(fps.get()), quality.get());
                } catch (Exception ignored) {}
                Cobra.platform.chat(dev.cobra.client.core.ExternalCapture.paused() ? "Recording paused" : "Recording resumed");
                pDown = true;
                return;
            }
            if (p && !pDown && dev.cobra.client.core.ExternalCapture.paused()) {
                try {
                    dev.cobra.client.core.ExternalCapture.resumeIfPaused(ffmpeg(), folder(), Integer.parseInt(fps.get()), quality.get());
                    Cobra.platform.chat("Recording resumed (new part)");
                } catch (Exception ignored) {}
                pDown = true;
                return;
            }
            if (x && !xDown && (dev.cobra.client.core.ExternalCapture.running() || dev.cobra.client.core.ExternalCapture.paused())) {
                java.io.File f = dev.cobra.client.core.ExternalCapture.stop();
                Cobra.platform.chat("Recording saved" + (f != null ? ": " + f.getName() : "") + " (open the launcher's Recordings)");
                xDown = true;
                return;
            }
            if (s && !sDown && rec.state() == dev.cobra.client.core.ScreenRecorder.State.IDLE && !dev.cobra.client.core.ExternalCapture.running()) {
                problem = null;
                rec.start(Integer.parseInt(fps.get()));
                Cobra.platform.chat("\u00a7c\u25cf\u00a7r Recording (" + fps.get() + " fps, " + resolution.get() + ")");
                if (!tipShown) {                                   // the smooth way is the graphics card
                    tipShown = true;
                    boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
                    Cobra.platform.chat("\u00a7eTip:\u00a7r recording from the game's frames costs FPS. For smooth, full-quality video "
                            + (win ? "let the launcher's Recordings page set up ffmpeg." : "install gpu-screen-recorder (Recordings page → Set up smooth recording)."));
                }
            }
            if (p && !pDown && rec.state() != dev.cobra.client.core.ScreenRecorder.State.IDLE) {
                rec.togglePause();
                Cobra.platform.chat(rec.state() == dev.cobra.client.core.ScreenRecorder.State.PAUSED ? "Recording paused" : "Recording resumed");
            }
            if (x && !xDown && rec.state() != dev.cobra.client.core.ScreenRecorder.State.IDLE) {
                java.io.File f = rec.stop();
                Cobra.platform.chat("Recording saved" + (f != null ? ": " + f.getName() : "") + " (open the launcher's Recordings)");
                Cobra.platform.chat("\u00a77" + dev.cobra.client.core.ScreenRecorder.lastInfo);
            }
            String err = rec.takeError();
            if (err != null) {
                Cobra.platform.chat("\u00a7c" + err);
                rec.stop();
                problem = err;
            }
            sDown = s;
            pDown = p;
            xDown = x;
        }

        @Override
        public void onDisable() {
            rec.stop();
            dev.cobra.client.core.ExternalCapture.stop();
        }

        /** Where recordings go (the launcher passes its folder; fallback: the game folder). */
        public java.io.File folder() {
            String d = System.getProperty("cobra.recordings");
            return d != null && !d.isEmpty() ? new java.io.File(d) : new java.io.File(Cobra.platform.gameDir(), "recordings");
        }

        public String ffmpeg() {
            String f = System.getProperty("cobra.ffmpeg");
            return f != null && !f.isEmpty() ? f : "ffmpeg";
        }

        /** Little REC badge while recording (drawn by the HUD pass). */
        public void renderBadge(dev.cobra.client.core.Render r) {
            boolean ext = dev.cobra.client.core.ExternalCapture.running() || dev.cobra.client.core.ExternalCapture.paused();
            if (!indicator.on() || rec.state() == dev.cobra.client.core.ScreenRecorder.State.IDLE && !ext) return;
            if (ext && extStart == 0) extStart = System.currentTimeMillis();
            if (!ext) extStart = 0;
            long t = ext ? (System.currentTimeMillis() - extStart) / 1000 : rec.seconds();
            boolean paused = ext ? dev.cobra.client.core.ExternalCapture.paused() : rec.state() == dev.cobra.client.core.ScreenRecorder.State.PAUSED;
            String label = (paused ? "PAUSED " : "REC ") + String.format(java.util.Locale.ROOT, "%d:%02d", t / 60, t % 60);
            int w = r.textWidth(label) + 16;
            r.rect(4, 4, w, 14, 0x99000000);
            boolean blink = paused || (System.currentTimeMillis() / 500) % 2 == 0;
            if (blink) r.rect(8, 9, 4, 4, paused ? 0xFFFFC04D : 0xFFFF4444);
            r.text(label, 14, 7, 0xFFFFFFFF, false);
        }
    }

    /**
     * Sky: pick one of the sky packs Cobra ships (applies right away), plus your own time of day
     * and weather. All client-side: only you see it.
     */
    public static final class Sky extends Module {
        public final Setting.Mode sky = add(new Setting.Mode("sky", "Sky", "Vanilla", skyOptions()));
        public final Setting.Mode time = add(new Setting.Mode("time", "Time", "Real", "Real", "Sunrise", "Day", "Noon", "Sunset", "Night", "Midnight"));
        public final Setting.Mode weather = add(new Setting.Mode("weather", "Weather", "Real", "Real", "Clear", "Rain", "Thunder"));

        public Sky() { super("sky", "Sky", "Custom skies, your own time of day and weather", Category.VISUAL, false); }

        /** "Vanilla" plus every cobra-sky-*.zip in the resource packs folder, named nicely. */
        private static String[] skyOptions() {
            java.util.List<String> out = new java.util.ArrayList<String>();
            out.add("Vanilla");
            try {
                java.io.File dir = new java.io.File(Cobra.platform.gameDir(), "resourcepacks");
                String[] files = dir.list();
                if (files != null) {
                    java.util.Arrays.sort(files);
                    for (String f : files) if (f.startsWith("cobra-sky-") && f.endsWith(".zip")) out.add(prettyName(f));
                }
            } catch (Exception ignored) {}
            return out.toArray(new String[0]);
        }

        static String prettyName(String file) {
            return file.substring("cobra-sky-".length(), file.length() - 4).replace('_', ' ').trim();
        }

        /** Resource pack id (file/cobra-sky-….zip) of the chosen sky, or null for vanilla. */
        private String[] listed;
        private long listedAt;

        public String packFile() {
            if (!isEnabled() || sky.is("Vanilla")) return null;
            long now = System.currentTimeMillis();
            if (listed == null || now - listedAt > 5000) {      // folder listing at most every 5 s, not every tick
                listed = new java.io.File(Cobra.platform.gameDir(), "resourcepacks").list();
                listedAt = now;
            }
            String[] files = listed;
            if (files != null) for (String f : files) if (f.startsWith("cobra-sky-") && prettyName(f).equals(sky.get())) return f;
            return null;
        }

        /** Time of day in ticks, or -1 for the real time. */
        public long timeTicks() {
            if (!isEnabled()) return -1;
            String t = time.get();
            if (t.equals("Sunrise")) return 23500;
            if (t.equals("Day")) return 1000;
            if (t.equals("Noon")) return 6000;
            if (t.equals("Sunset")) return 12500;
            if (t.equals("Night")) return 14500;
            if (t.equals("Midnight")) return 18000;
            return -1;
        }

        /** Rain strength override (0/1), or -1 for the real weather. */
        public float rain() {
            if (!isEnabled() || weather.is("Real")) return -1;
            return weather.is("Clear") ? 0 : 1;
        }

        public float thunder() {
            if (!isEnabled() || weather.is("Real")) return -1;
            return weather.is("Thunder") ? 1 : 0;
        }
    }

    /**
     * 1.7 visuals: the old PvP look. Swords are held "blocking" while you right-click, no dip when
     * switching items, and items sit where they did in 1.7/1.8.
     */
    public static final class OldVisuals extends Module {
        public final Setting.Bool blockHit = add(new Setting.Bool("blockhit", "Sword blocking pose (right click)", true));
        public final Setting.Bool noEquip = add(new Setting.Bool("noequip", "No item switch dip", true));
        public final Setting.Bool oldPositions = add(new Setting.Bool("positions", "1.7 item positions", true));

        public OldVisuals() { super("oldvisuals", "1.7 Visuals", "Old PvP animations: block-hitting, no switch dip, old item spots", Category.VISUAL, false); }
    }

    /** 1.7 sounds: the attack sounds added after 1.8 (sweep, crit, strong, weak, knockback) are muted. */
    public static final class OldSounds extends Module {
        public final Setting.Bool attack = add(new Setting.Bool("attack", "Mute new attack sounds", true));

        public OldSounds() { super("oldsounds", "1.7 Sounds", "Mutes the combat sounds that 1.9+ added", Category.VISUAL, false); }

        /** Should a sound with this id be muted? */
        public boolean mute(String id) {
            if (!isEnabled() || id == null) return false;
            return attack.on() && id.contains("entity.player.attack.");
        }
    }

    /** Menu blur: blurs the game behind every menu and inventory, as strong as you like. */
    public static final class MenuBlur extends Module {
        public final Setting.Number strength = add(new Setting.Number("strength", "Blur strength", 6, 1, 10, 1, ""));
        public final Setting.Bool inventory = add(new Setting.Bool("inventory", "Also behind inventories", true));

        public MenuBlur() { super("menublur", "Menu Blur", "Blurs the game behind menus and inventories", Category.VISUAL, false); }
    }

    /** Pack organizer: resource packs sorted A to Z, and a key to open the packs screen anywhere. */
    public static final class PackOrganizer extends Module {
        public final Setting.Bool sort = add(new Setting.Bool("sort", "Sort available packs A to Z", true));
        public final Setting.Bind open = add(new Setting.Bind("open", "Open packs screen", -1, null));

        public PackOrganizer() { super("packorganizer", "Pack Organizer", "Sorted pack list and a key to open your packs", Category.UTILITY, false); }
    }

    /**
     * Totem: the totem in your hand (size and place), the big totem that pops on screen when it saves
     * you (size, where it shows, or hide it), and the colour of its particles.
     */
    public static final class Totem extends Module {
        public final Setting.Number heldSize = add(new Setting.Number("heldsize", "Held totem size", 1, 0.3, 2, 0.05, "x"));
        public final Setting.Number heldX = add(new Setting.Number("heldx", "Held totem X", 0, -1, 1, 0.02, ""));
        public final Setting.Number heldY = add(new Setting.Number("heldy", "Held totem Y", 0, -1, 1, 0.02, ""));
        public final Setting.Number heldZ = add(new Setting.Number("heldz", "Held totem Z", 0, -1, 1, 0.02, ""));
        public final Setting.Bool hidePop = add(new Setting.Bool("hidepop", "Hide the totem pop animation", false));
        public final Setting.Number popSize = add(new Setting.Number("popsize", "Pop animation size", 1, 0.2, 2, 0.05, "x"),
                new Setting.Cond() { public boolean ok() { return !hidePop.on(); } });
        public final Setting.Mode popPlace = add(new Setting.Mode("popplace", "Pop animation place", "Random", "Random", "Center", "Left", "Right", "Top", "Bottom"),
                new Setting.Cond() { public boolean ok() { return !hidePop.on(); } });
        public final Setting.Mode particles = add(new Setting.Mode("particles", "Particle colour", "Default", "Default", "Custom", "Rainbow"));
        public final Setting.Color colour = add(new Setting.Color("colour", "Particle colour", 0xFFB565FF),
                new Setting.Cond() { public boolean ok() { return particles.is("Custom"); } });

        public Totem() { super("totem", "Totem", "Totem size and place in your hand, the pop animation and its particle colour", Category.VISUAL, false); }

        /** Where the pop animation shows, as vanilla's offset (-1..1 each way), or null for random. */
        public float[] popOffset() {
            String p = popPlace.get();
            if (p.equals("Center")) return new float[]{0, 0};
            if (p.equals("Left")) return new float[]{-0.75f, 0};
            if (p.equals("Right")) return new float[]{0.75f, 0};
            if (p.equals("Top")) return new float[]{0, -0.7f};
            if (p.equals("Bottom")) return new float[]{0, 0.7f};
            return null;
        }
    }

    /** Colors: the whole game's contrast, saturation and brightness (a screen effect, like a filter). */
    public static final class Colors extends Module {
        public final Setting.Number contrast = add(new Setting.Number("contrast", "Contrast", 100, 50, 150, 1, "%"));
        public final Setting.Number saturation = add(new Setting.Number("saturation", "Saturation", 100, 0, 200, 1, "%"));
        public final Setting.Number brightness = add(new Setting.Number("brightness", "Brightness", 100, 50, 150, 1, "%"));

        public Colors() { super("colors", "Colors", "Contrast, saturation and brightness of the whole game", Category.VISUAL, false); }

        /** The colour data the screen effect reads (each value 0..255, 128 = normal). */
        public int argb() {
            if (!isEnabled()) return 0x00808080;
            int c = enc(contrast.f() / 100f), s = enc(saturation.f() / 100f), b = enc(brightness.f() / 100f);
            return 0xFF000000 | c << 16 | s << 8 | b;
        }

        private static int enc(float v) {
            return Math.max(0, Math.min(255, Math.round(v / 2f * 255f)));
        }
    }

    /** Low Health Warning: a red glow round the edges of the screen (pulsing) when you're low. */
    public static final class LowHealth extends Module {
        public final Setting.Number below = add(new Setting.Number("below", "Warn below (hearts)", 4, 1, 10, 0.5, ""));
        public final Setting.Number strength = add(new Setting.Number("strength", "Strength", 60, 10, 100, 5, "%"));
        public final Setting.Bool pulse = add(new Setting.Bool("pulse", "Pulse", true));

        public LowHealth() { super("lowhealth", "Low Health Warning", "A red glow at the screen's edges when your health is low", Category.VISUAL, false); }

        /** Draws the glow (full screen, under the HUD). */
        public void draw(dev.cobra.client.core.Render r) {
            if (!isEnabled() || !Cobra.platform.inWorld()) return;
            float hp = Cobra.platform.health();
            float limit = below.f() * 2;
            if (hp <= 0 || hp > limit) return;
            float k = (1 - hp / limit) * 0.6f + 0.4f;
            if (pulse.on()) k *= 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 180.0);
            int w = r.width(), h = r.height();
            int band = Math.max(8, Math.min(w, h) / 6);
            float max = strength.f() / 100f * 0.7f;
            for (int i = 0; i < band; i++) {                          // a soft gradient, strongest at the edge
                float t = 1 - i / (float) band;
                int a = Math.round(255 * max * k * t * t);
                if (a <= 0) continue;
                int c = a << 24 | 0xD01818;
                r.rect(i, i, w - 2 * i, 1, c);
                r.rect(i, h - 1 - i, w - 2 * i, 1, c);
                r.rect(i, i + 1, 1, h - 2 * i - 2, c);
                r.rect(w - 1 - i, i + 1, 1, h - 2 * i - 2, c);
            }
        }
    }

    /** Hit Sound: a crisp sound every time you hit something (pick from a few). */
    public static final class HitSound extends Module {
        public final Setting.Mode sound = add(new Setting.Mode("sound", "Sound", "Ding", "Ding", "Click", "Pop", "Bell", "Bass"));
        public final Setting.Number volume = add(new Setting.Number("volume", "Volume", 70, 0, 100, 5, "%"));
        public final Setting.Number pitch = add(new Setting.Number("pitch", "Pitch", 1.0, 0.5, 2.0, 0.05, "x"));

        public HitSound() { super("hitsound", "Hit Sound", "Plays a sound when you hit something", Category.UTILITY, false); }

        public void hit() {
            if (!isEnabled()) return;
            String id = sound.is("Click") ? "ui.button.click" : sound.is("Pop") ? "entity.chicken.egg"
                    : sound.is("Bell") ? "block.note_block.bell" : sound.is("Bass") ? "block.note_block.bass" : "entity.experience_orb.pickup";
            Cobra.platform.playSound(id, volume.f() / 100f, pitch.f());
        }
    }

    /** Auto GG: says "gg" (or your text) in chat when a game ends on Hypixel-style servers. */
    public static final class AutoGG extends Module {
        public final Setting.Text text = add(new Setting.Text("text", "Message", "gg", 32));
        public final Setting.Number delay = add(new Setting.Number("delay", "Delay", 1.0, 0, 5, 0.5, "s"));
        private long sendAt = -1, lastSent;

        public AutoGG() { super("autogg", "Auto GG", "Says gg in chat when a game ends", Category.HYPIXEL, false); }

        private static final String[] ENDS = {"1st Killer -", "Winner -", "Winners -", "Winner:", "WINNER!", "VICTORY!", "Top Survivors", "won the game", "Reward Summary"};

        public void onChat(String plain) {
            if (!isEnabled() || plain == null) return;
            for (String e : ENDS) {
                if (plain.contains(e) && System.currentTimeMillis() - lastSent > 10_000) {
                    sendAt = System.currentTimeMillis() + Math.round(delay.f() * 1000);
                    return;
                }
            }
        }

        @Override
        public void onTick() {
            if (sendAt > 0 && System.currentTimeMillis() >= sendAt) {
                sendAt = -1;
                lastSent = System.currentTimeMillis();
                String t = text.get().trim();
                if (!t.isEmpty()) Cobra.platform.say(t);
            }
        }
    }

    /** TNT Timer: the seconds left above lit TNT, going from green to red. */
    public static final class TntTimer extends Module {
        public final Setting.Number decimals = add(new Setting.Number("decimals", "Decimals", 2, 0, 2, 1, ""));

        public TntTimer() { super("tnttimer", "TNT Timer", "Shows how long until lit TNT explodes", Category.VISUAL, false); }

        /** The label for a fuse of {@code ticks}: "§a3.25s" … "§c0.40s". */
        public String label(int ticks) {
            float s = Math.max(0, ticks) / 20f;
            String col = s > 2.5f ? "\u00a7a" : s > 1.2f ? "\u00a7e" : s > 0.6f ? "\u00a76" : "\u00a7c";
            return col + String.format(java.util.Locale.ROOT, "%." + decimals.i() + "fs", s);
        }
    }

    /** Despawn Timer: how long until dropped items (and blocks) on the ground disappear (5 minutes). */
    public static final class DespawnTimer extends Module {
        public final Setting.Number range = add(new Setting.Number("range", "Range", 16, 4, 64, 1, " blocks"));
        public final Setting.Bool showName = add(new Setting.Bool("name", "Show the item's name too", true));

        public DespawnTimer() { super("despawntimer", "Despawn Timer", "How long until dropped items disappear", Category.VISUAL, false); }

        /** "4:32" for an item that's {@code age} ticks old (it goes at 6000). */
        public String label(int age) {
            int left = Math.max(0, 6000 - age) / 20;
            String col = left > 60 ? "\u00a7a" : left > 20 ? "\u00a7e" : "\u00a7c";
            return col + (left / 60) + ":" + (left % 60 < 10 ? "0" : "") + (left % 60);
        }
    }

    /** Item Beams: a soft beam of light rising from dropped items, so you spot them from afar. */
    public static final class ItemBeams extends Module {
        public final Setting.Mode colour = add(new Setting.Mode("colourmode", "Colour", "Custom", "Custom", "Rainbow"));
        public final Setting.Color custom = add(new Setting.Color("colour", "Beam colour", 0xFF8BD5FF),
                new Setting.Cond() { public boolean ok() { return colour.is("Custom"); } });
        public final Setting.Number height = add(new Setting.Number("height", "Height", 3, 1, 8, 0.5, " blocks"));
        public final Setting.Number width = add(new Setting.Number("width", "Width", 0.08, 0.03, 0.3, 0.01, ""));

        public ItemBeams() { super("itembeams", "Item Beams", "Beams of light above dropped items", Category.VISUAL, false); }

        public int argb() {
            if (colour.is("Rainbow")) return 0xFF000000 | java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 4000) / 4000f, 0.7f, 1f);
            return custom.argb();
        }
    }

    /** Kill Effect: a lightning strike (just for you, it doesn't hurt anything) where you kill someone. */
    public static final class KillEffect extends Module {
        public final Setting.Bool playersOnly = add(new Setting.Bool("players", "Only for players", false));
        public final Setting.Bool sound = add(new Setting.Bool("sound", "Thunder sound", true));
        private Object target;
        private long hitAt;

        public KillEffect() { super("killeffect", "Kill Effect", "Lightning strikes where you kill someone", Category.VISUAL, false); }

        public void attacked(Object t) {
            target = t;
            hitAt = System.currentTimeMillis();
        }

        /** The entity you hit in the last 3 seconds (the platform checks if it died). */
        public Object recentTarget() {
            return target != null && System.currentTimeMillis() - hitAt < 3000 ? target : null;
        }

        public void consumed() { target = null; }
    }

    /** Rescale: a stretched resolution: the world drawn for another shape (e.g. 4:3) and stretched to fill your screen. */
    public static final class Rescale extends Module {
        public final Setting.Mode aspect = add(new Setting.Mode("aspect", "Shape", "4:3", "4:3", "5:4", "3:2", "16:10", "Custom"));
        public final Setting.Number cw = add(new Setting.Number("cw", "Custom width", 1440, 640, 3840, 10, ""),
                new Setting.Cond() { public boolean ok() { return aspect.is("Custom"); } });
        public final Setting.Number ch = add(new Setting.Number("ch", "Custom height", 1080, 480, 2160, 10, ""),
                new Setting.Cond() { public boolean ok() { return aspect.is("Custom"); } });

        public Rescale() { super("rescale", "Rescale", "Stretched resolution: play 4:3 (or any shape) stretched to your screen", Category.VISUAL, false); }

        /** The width ÷ height the world is drawn for. */
        public float target() {
            if (aspect.is("5:4")) return 5f / 4f;
            if (aspect.is("3:2")) return 3f / 2f;
            if (aspect.is("16:10")) return 16f / 10f;
            if (aspect.is("Custom")) return cw.f() / Math.max(1f, ch.f());
            return 4f / 3f;
        }
    }

    /** Wavy capes: capes ripple in the wind and swing more naturally. */
    public static final class WavyCapes extends Module {
        public final Setting.Number wind = add(new Setting.Number("wind", "Wind", 1, 0, 3, 0.1, "x"));

        public WavyCapes() { super("wavycapes", "Wavy Capes", "Capes flutter and wave like cloth", Category.VISUAL, false); }
    }

    /**
     * Cosmetics you wear in game: cat ears, wings (angel, red and more colours), a cat tail, a katana
     * on your back, big feet, boxing gloves and a halo. Only people who also use Cobra see them
     * (they're shared through the Cobra online list).
     */
    public static final class Cosmetics extends Module {
        public final Setting.Mode wings = add(new Setting.Mode("wings", "Wings", "Off", "Off", "Angel", "Red", "Black", "Gold", "Blue", "Purple", "Pink", "Green", "Cyan", "Custom"));
        public final Setting.Color wingsColour = add(new Setting.Color("wings_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return wings.is("Custom"); } });
        public final Setting.Mode wingStyle = add(new Setting.Mode("wingstyle", "Wing style", "Feather", "Feather", "Dragon", "Butterfly", "Demon", "Energy", "Fairy"));
        public final Setting.Mode halo = add(new Setting.Mode("halo", "Halo", "Off", "Off", "Angel", "Red", "Custom"));
        public final Setting.Color haloColour = add(new Setting.Color("halo_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return halo.is("Custom"); } });
        public final Setting.Mode hat = add(new Setting.Mode("hat", "Hat", "Off", "Off", "Crown", "Top hat", "Witch", "Santa", "Viking"));
        public final Setting.Mode ears = add(new Setting.Mode("ears", "Cat ears", "Off", "Off", "Black", "White", "Ginger", "Pink", "Custom"));
        public final Setting.Color earsColour = add(new Setting.Color("ears_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return ears.is("Custom"); } });
        public final Setting.Mode bunny = add(new Setting.Mode("bunny", "Bunny ears", "Off", "Off", "White", "Pink", "Black", "Brown", "Custom"));
        public final Setting.Color bunnyColour = add(new Setting.Color("bunny_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return bunny.is("Custom"); } });
        public final Setting.Mode horns = add(new Setting.Mode("horns", "Horns", "Off", "Off", "Red", "Black", "White", "Gold", "Custom"));
        public final Setting.Color hornsColour = add(new Setting.Color("horns_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return horns.is("Custom"); } });
        public final Setting.Mode glasses = add(new Setting.Mode("glasses", "Sunglasses", "Off", "Off", "Black", "Gold", "Pink", "Custom"));
        public final Setting.Color glassesColour = add(new Setting.Color("glasses_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return glasses.is("Custom"); } });
        public final Setting.Mode headphones = add(new Setting.Mode("headphones", "Headphones", "Off", "Off", "Black", "White", "Pink", "Blue", "Custom"));
        public final Setting.Color headphonesColour = add(new Setting.Color("headphones_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return headphones.is("Custom"); } });
        public final Setting.Mode antlers = add(new Setting.Mode("antlers", "Antlers", "Off", "Off", "Brown", "White", "Gold", "Custom"));
        public final Setting.Color antlersColour = add(new Setting.Color("antlers_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return antlers.is("Custom"); } });
        public final Setting.Mode orbit = add(new Setting.Mode("orbit", "Orbiting gems", "Off", "Off", "Purple", "Cyan", "Red", "Gold", "Green", "Custom"));
        public final Setting.Color orbitColour = add(new Setting.Color("orbit_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return orbit.is("Custom"); } });
        public final Setting.Mode scarf = add(new Setting.Mode("scarf", "Scarf", "Off", "Off", "Red", "Blue", "Green", "White", "Black", "Custom"));
        public final Setting.Color scarfColour = add(new Setting.Color("scarf_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return scarf.is("Custom"); } });
        public final Setting.Mode flowers = add(new Setting.Mode("flowers", "Flower crown", "Off", "Off", "Pink", "White", "Red", "Purple", "Gold", "Custom"));
        public final Setting.Color flowersColour = add(new Setting.Color("flowers_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return flowers.is("Custom"); } });
        public final Setting.Mode bowtie = add(new Setting.Mode("bowtie", "Bow tie", "Off", "Off", "Red", "Black", "Blue", "Pink", "Gold", "Custom"));
        public final Setting.Color bowtieColour = add(new Setting.Color("bowtie_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return bowtie.is("Custom"); } });
        public final Setting.Mode spikes = add(new Setting.Mode("spikes", "Back spikes", "Off", "Off", "Purple", "Red", "Black", "Green", "Gold", "Custom"));
        public final Setting.Color spikesColour = add(new Setting.Color("spikes_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return spikes.is("Custom"); } });
        public final Setting.Mode tail = add(new Setting.Mode("tailkind", "Tail", "Off", "Off", "Black", "White", "Ginger", "Pink", "Fox", "Custom"));
        public final Setting.Color tailColour = add(new Setting.Color("tailkind_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return tail.is("Custom"); } });
        public final Setting.Mode backpack = add(new Setting.Mode("backpack", "Backpack", "Off", "Off", "Brown", "Black", "Blue", "Red", "Custom"));
        public final Setting.Color backpackColour = add(new Setting.Color("backpack_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return backpack.is("Custom"); } });
        public final Setting.Bool katana = add(new Setting.Bool("katana", "Katana on your back", false));
        public final Setting.Mode gloves = add(new Setting.Mode("gloves", "Boxing gloves", "Off", "Off", "Red", "Blue", "Black", "Custom"));
        public final Setting.Color glovesColour = add(new Setting.Color("gloves_colour", "  custom colour", 0xFF8B5CF6),
                new Setting.Cond() { public boolean ok() { return gloves.is("Custom"); } });
        public final Setting.Bool feet = add(new Setting.Bool("feet", "Big feet", false));
        public final Setting.Number size = add(new Setting.Number("size", "Size", 100, 70, 150, 5, "%"));
        public final Setting.Bool glow = add(new Setting.Bool("glow", "Glow effects", true));
        public final Setting.Mode particles = add(new Setting.Mode("particles", "Particles", "Off", "Off", "Sparkles", "Hearts", "Flames", "Soul fire", "Snow", "Magic", "Petals", "Notes"));
        public final Setting.Bool trail = add(new Setting.Bool("trail", "Particle trail when moving", false));
        public final Setting.Bool showOwn = add(new Setting.Bool("showown", "Show mine in third person", true));

        public Cosmetics() {
            super("cosmetics", "Cosmetics", "Wings, halos, hats, ears, tails and more (only Abyss players see them)", Category.VISUAL, false);
            // fold-out groups so the list stays short; searching opens the right one
            group(new Setting.Group("g_back", "Wings & back"), wings, wingsColour, wingStyle, tail, tailColour, backpack, backpackColour, katana, scarf, scarfColour,
                    spikes, spikesColour, bowtie, bowtieColour);
            group(new Setting.Group("g_head", "Head"), halo, haloColour, hat, ears, earsColour, bunny, bunnyColour, horns, hornsColour,
                    antlers, antlersColour, orbit, orbitColour, flowers, flowersColour, glasses, glassesColour, headphones, headphonesColour);
            group(new Setting.Group("g_hands", "Hands & feet"), gloves, glovesColour, feet);
            group(new Setting.Group("g_fx", "Effects & size"), glow, particles, trail, size);
        }

        private static String code(String v) {
            return v.toLowerCase().replace(" ", "");
        }

        /** What you wear as a short code for the online list, e.g. "ears:black,wings:angel,tail:fox". */
        public String serialize() {
            if (!isEnabled()) return "";
            StringBuilder b = new StringBuilder();
            Setting.Mode[] modes = {wings, halo, hat, ears, bunny, horns, glasses, headphones, tail, backpack, gloves, antlers, orbit, scarf, flowers, bowtie, spikes};
            String[] keys = {"wings", "halo", "hat", "ears", "bunny", "horns", "glasses", "headphones", "tail", "backpack", "gloves", "antlers", "orbit", "scarf", "flowers", "bowtie", "spikes"};
            Setting.Color[] colours = {wingsColour, haloColour, null, earsColour, bunnyColour, hornsColour, glassesColour, headphonesColour, tailColour, backpackColour, glovesColour, antlersColour, orbitColour, scarfColour, flowersColour, bowtieColour, spikesColour};
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].is("Off")) continue;
                String v = modes[i].is("Custom") ? "x" + String.format("%06x", colours[i].argb() & 0xFFFFFF) : code(modes[i].get());
                b.append(keys[i]).append(':').append(v).append(',');
            }
            if (!wings.is("Off") && !wingStyle.is("Feather")) b.append("wingstyle:").append(code(wingStyle.get())).append(',');
            if (katana.on()) b.append("katana,");
            if (feet.on()) b.append("feet,");
            if (!particles.is("Off")) b.append("fx:").append(code(particles.get())).append(',');
            if (!particles.is("Off") && trail.on()) b.append("trail,");
            if (b.length() == 0) return "";
            if (size.i() != 100) b.append("size:").append(size.i()).append(',');
            if (glow.on()) b.append("glow,");
            return b.substring(0, b.length() - 1);
        }

        /**
         * Cosmetics chosen in the launcher (its Cosmetics page) arrive as -Dcobra.cosmetics=CODE and
         * replace what's set here when the game starts (you can still change them in game).
         */
        public void applyFromLauncher() {
            String code = System.getProperty("cobra.cosmetics");
            if (code == null) return;
            java.util.Map<String, String> m = new java.util.HashMap<String, String>();
            for (String part : code.split(",")) {
                if (part.isEmpty()) continue;
                int i = part.indexOf(':');
                m.put(i < 0 ? part : part.substring(0, i), i < 0 ? "" : part.substring(i + 1));
            }
            Setting.Mode[] modes = {wings, halo, hat, ears, bunny, horns, glasses, headphones, tail, backpack, gloves, antlers, orbit, scarf, flowers, bowtie, spikes};
            String[] keys = {"wings", "halo", "hat", "ears", "bunny", "horns", "glasses", "headphones", "tail", "backpack", "gloves", "antlers", "orbit", "scarf", "flowers", "bowtie", "spikes"};
            boolean any = false;
            Setting.Color[] colours = {wingsColour, haloColour, null, earsColour, bunnyColour, hornsColour, glassesColour, headphonesColour, tailColour, backpackColour, glovesColour, antlersColour, orbitColour, scarfColour, flowersColour, bowtieColour, spikesColour};
            for (int i = 0; i < modes.length; i++) {
                String v = m.get(keys[i]);
                if (v != null && v.length() == 7 && v.charAt(0) == 'x') {        // a custom colour from the launcher
                    modes[i].set("Custom");
                    try {
                        colours[i].set(0xFF000000 | Integer.parseInt(v.substring(1), 16));
                    } catch (NumberFormatException ignored) {}
                } else {
                    modes[i].set(pretty(modes[i], v));
                }
                any |= v != null;
            }
            wingStyle.set(pretty(wingStyle, m.containsKey("wingstyle") ? m.get("wingstyle") : "feather"));
            katana.set(m.containsKey("katana"));
            feet.set(m.containsKey("feet"));
            glow.set(m.containsKey("glow"));
            particles.set(pretty(particles, m.get("fx")));
            trail.set(m.containsKey("trail"));
            any |= m.containsKey("fx");
            try {
                size.set(m.containsKey("size") ? Double.parseDouble(m.get("size")) : 100.0);
            } catch (NumberFormatException ignored) {}
            any |= m.containsKey("katana") || m.containsKey("feet");
            if (any != isEnabled()) toggle();
        }

        /** The option of {@code mode} whose short code is {@code v} ("tophat" → "Top hat"), or "Off". */
        private static String pretty(Setting.Mode mode, String v) {
            if (v == null || v.isEmpty()) return "Off";
            for (String o : mode.modes) if (code(o).equals(v)) return o;
            return "Off";
        }
    }

    /** A fading trail behind the mouse pointer in menus. */
    public static final class MouseTrail extends Module {
        public final Setting.Color color = add(new Setting.Color("color", "Color", 0xFFF8F5F2));
        public final Setting.Bool chroma = add(new Setting.Bool("chroma", "Rainbow", false));
        public final Setting.Number length = add(new Setting.Number("length", "Length", 14, 4, 40, 1, ""));
        public final Setting.Number size = add(new Setting.Number("size", "Size", 3, 1, 8, 1, "px"));

        private final float[] xs = new float[64], ys = new float[64];
        private final long[] ts = new long[64];
        private int head, count;

        public MouseTrail() { super("mousetrail", "Mouse Trail", "A fading trail behind your pointer in menus", Category.VISUAL, false); }

        /** Call every frame a menu is drawn, with the mouse position (GUI pixels). */
        public void render(dev.cobra.client.core.Render r, int mx, int my) {
            if (!isEnabled()) return;
            long now = System.currentTimeMillis();
            int last = (head + 63) % 64;
            if (count == 0 || Math.abs(xs[last] - mx) + Math.abs(ys[last] - my) >= 1) {
                xs[head] = mx;
                ys[head] = my;
                ts[head] = now;
                head = (head + 1) % 64;
                count = Math.min(64, count + 1);
            }
            int n = Math.min(count, length.i());
            long life = 40L * length.i();
            int base = color.argb();
            float sz = size.f();
            for (int k = n - 1; k >= 1; k--) {
                int i = (head - 1 - k + 128) % 64, j = (head - k + 128) % 64;
                float age = (now - ts[i]) / (float) life;
                if (age >= 1) continue;
                float t = 1 - k / (float) n;                 // 0 = tail end, 1 = at the pointer
                float a = t * (1 - age) * ((base >>> 24) / 255f);
                int c = chroma.on() ? dev.cobra.client.core.ui.Draw.hsv((now / 1500f + k * 0.03f) % 1f, 0.7f, 1f, 255) : base;
                int col = ((int) (a * 255) & 0xFF) << 24 | (c & 0xFFFFFF);
                float s = Math.max(1, sz * (0.35f + 0.65f * t));
                // fill the gap between two samples so fast moves still give a line
                float dx = xs[j] - xs[i], dy = ys[j] - ys[i];
                int steps = Math.max(1, (int) (Math.max(Math.abs(dx), Math.abs(dy)) / Math.max(1, s * 0.6f)));
                for (int q = 0; q < steps; q++) {
                    float px = xs[i] + dx * q / steps, py = ys[i] + dy * q / steps;
                    r.rect(Math.round(px - s / 2), Math.round(py - s / 2), Math.max(1, Math.round(s)), Math.max(1, Math.round(s)), col);
                }
            }
        }
    }

    /**
     * Velocity motion blur: every pixel is smeared along how far the view moved this frame, so
     * quick turns blur and standing still is sharp (no ghost trails). Optional zoom blur when
     * running forward.
     */
    public static final class MotionBlur extends Module {
        public final Setting.Number strength = add(new Setting.Number("strength", "Strength", 3, 1, 10, 1, ""));
        public final Setting.Bool movement = add(new Setting.Bool("movement", "Blur when running", false));
        public final Setting.Number smoothing = add(new Setting.Number("smoothing", "Smoothing", 5, 0, 10, 1, ""));

        private float vx, vy, radial;
        private double lastYaw = Double.NaN, lastPitch;
        private long lastFrameNs;

        public MotionBlur() { super("motionblur", "Motion Blur", "Blurs by how fast you turn and move", Category.VISUAL, false); }

        /**
         * Called once per rendered frame with the camera. Returns the blur as ARGB for the 1x1
         * motion texture: r/g = screen motion (128 = none), b = radial, a = strength.
         */
        public int frame(double yaw, double pitch, double fovVertical, double aspect, double speed) {
            return frameAt(yaw, pitch, fovVertical, aspect, speed, System.nanoTime());
        }

        /** Explicit monotonic timestamp keeps the blur stable at different frame rates. */
        public int frameAt(double yaw, double pitch, double fovVertical, double aspect, double speed, long now) {
            double dt = (now - lastFrameNs) / 1_000_000_000.0;
            if (Double.isNaN(lastYaw) || dt <= 0 || dt > 0.25) {
                lastYaw = yaw;
                lastPitch = pitch;
                lastFrameNs = now;
                vx = vy = radial = 0;
                return 0x00808000;
            }
            lastFrameNs = now;
            double dyaw = yaw - lastYaw;
            dyaw = ((dyaw % 360) + 540) % 360 - 180;           // shortest way round
            double dpitch = pitch - lastPitch;
            lastYaw = yaw;
            lastPitch = pitch;
            double fovV = Math.max(1, fovVertical);
            double fovH = Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(fovV) / 2) * aspect));
            float tx = (float) Math.max(-0.25, Math.min(0.25, dyaw / fovH / (60 * dt)));
            float ty = (float) Math.max(-0.25, Math.min(0.25, dpitch / fovV / (60 * dt)));
            float tr = movement.on() ? (float) Math.max(0, Math.min(1, (speed - 0.13) * 2.2)) : 0;
            float k = (float) (1 - Math.pow(smoothing.f() * 0.08, dt * 60));
            vx += (tx - vx) * k;
            vy += (ty - vy) * k;
            radial += (tr - radial) * k;
            int r = clamp255(128 + vx / 0.5f * 255);
            int g = clamp255(128 + vy / 0.5f * 255);
            int b = clamp255(radial * 255);
            int a = clamp255(strength.f() / 10f * 255);
            return a << 24 | r << 16 | g << 8 | b;
        }

        private static int clamp255(float v) {
            return Math.max(0, Math.min(255, Math.round(v)));
        }

        /** Forget the last camera angle (after a teleport or reopening a world). */
        public void reset() {
            lastYaw = Double.NaN;
            lastFrameNs = 0;
            vx = vy = radial = 0;
        }
    }

    /**
     * View Model (BactroMod-style): move, rotate and resize what you hold in first person. "All items"
     * applies to everything; each item type (swords, tools, blocks, bows, food, shield, other items,
     * empty hand) adds its own tweak on top. Also moves the fire overlay.
     */
    public static final class ViewModel extends Module {
        public static final String[] TYPES = {"All items", "Selected items", "Swords", "Tools", "Blocks", "Bows", "Food", "Shield", "Other items", "Empty hand"};
        private static final String[] KEYS = {"all", "group", "sword", "tool", "block", "bow", "food", "shield", "item", "hand"};
        private static final int GROUP = 1;
        /** "Selected items": which types share the Selected items values (e.g. shield, food and bow). */
        private final Setting.Bool[] inGroup = new Setting.Bool[KEYS.length];

        public final Setting.Mode editing = add(new Setting.Mode("editing", "Editing", TYPES[0], TYPES));
        private final Setting.Number[][] values = new Setting.Number[KEYS.length][];
        public final Setting.Number fire = add(new Setting.Number("fire", "Fire height", -30, -60, 20, 1, ""));
        public final Setting.Bool mirror = add(new Setting.Bool("mirror", "Mirror for the off hand", true));
        public final Setting.Bool noEquip = add(new Setting.Bool("noequip", "No item switch animation", false));
        public final Setting.Number swingSpeed = add(new Setting.Number("swingspeed", "Swing speed", 1, 0.3, 3, 0.1, "x"));
        public final Setting.Bool noBob = add(new Setting.Bool("nobob", "No view bobbing (hand and camera)", false));
        public final Setting.Bool hideHand = add(new Setting.Bool("hidehand", "Hide your empty hand", false));
        public final Setting.Bool hideOffhand = add(new Setting.Bool("hideoffhand", "Hide the off-hand item", false));
        public final Setting.Action reset = add(new Setting.Action("reset", "Reset this type", new Runnable() {
            public void run() {
                int t = indexOf(editing.get());
                for (Setting.Number n : values[t]) n.reset();
            }
        }));
        // one-click looks for "All items" (x, y, z, rotation x/y/z, size); tweak from there
        public final Setting.Action presetSmall = add(new Setting.Action("preset_small", "Preset: small & tidy", preset(0.08, -0.06, -0.10, 0, 0, 0, 0.75)));
        public final Setting.Action presetLow = add(new Setting.Action("preset_low", "Preset: lowered", preset(0.04, -0.18, -0.06, 0, 0, 0, 0.9)));
        public final Setting.Action presetOld = add(new Setting.Action("preset_old", "Preset: 1.8 PvP", preset(0.02, -0.06, 0.08, 0, -6, 0, 0.85)));
        public final Setting.Action presetCenter = add(new Setting.Action("preset_center", "Preset: centred", preset(-0.32, -0.08, -0.10, 0, 12, 0, 0.8)));
        public final Setting.Action presetFar = add(new Setting.Action("preset_far", "Preset: far away", preset(0.10, 0.02, -0.45, 0, 0, 0, 0.9)));

        /** A preset sets "All items" (and leaves your per-type tweaks on top). */
        private Runnable preset(final double x, final double y, final double z, final double rx, final double ry, final double rz, final double size) {
            return new Runnable() {
                public void run() {
                    double[] v = {x, y, z, rx, ry, rz, size};
                    for (int k = 0; k < 7; k++) values[0][k].set(v[k]);
                    editing.set(TYPES[0]);
                }
            };
        }

        public ViewModel() {
            super("viewmodel", "View Model", "Presets, then position, rotate and size each item type (or several at once)", Category.VISUAL, false);
            // insert the per-type rows right after "Editing" so they read as its page
            List<Setting<?>> rows = new java.util.ArrayList<Setting<?>>();
            for (int t = 0; t < KEYS.length; t++) {
                final int type = t;
                String p = t == 0 ? "" : KEYS[t] + "_";          // "All items" keeps the old ids (x, y, z, size)
                Setting.Cond when = new Setting.Cond() {
                    public boolean ok() { return indexOf(editing.get()) == type; }
                };
                double scaleDef = 1;
                values[t] = new Setting.Number[]{
                        n(p + "x", "Position X", 0, -1.5, 1.5, 0.02, "", when),
                        n(p + "y", "Position Y", 0, -1.5, 1.5, 0.02, "", when),
                        n(p + "z", "Position Z", 0, -1.5, 1.5, 0.02, "", when),
                        n(p + "rx", "Rotation X", 0, -180, 180, 1, "\u00b0", when),
                        n(p + "ry", "Rotation Y", 0, -180, 180, 1, "\u00b0", when),
                        n(p + "rz", "Rotation Z", 0, -180, 180, 1, "\u00b0", when),
                        n(p + "size", "Size", scaleDef, 0.2, 2, 0.02, "x", when)};
                for (Setting.Number n : values[t]) rows.add(n);
            }
            // "Selected items": tick the types first, then the sliders below move all of them together
            List<Setting<?>> ticks = new java.util.ArrayList<Setting<?>>();
            Setting.Cond groupPage = new Setting.Cond() {
                public boolean ok() { return indexOf(editing.get()) == GROUP; }
            };
            for (int t = 2; t < KEYS.length; t++) {
                inGroup[t] = add(new Setting.Bool("group_has_" + KEYS[t], "Include " + TYPES[t].toLowerCase(java.util.Locale.ROOT), false), groupPage);
                ticks.add(inGroup[t]);
            }
            settings.removeAll(rows);
            settings.removeAll(ticks);
            settings.addAll(1, rows);
            settings.addAll(1, ticks);
            // tidy groups: presets first, the extras last (Editing + its sliders stay open)
            group(new Setting.Group("g_presets", "Presets"), presetSmall, presetLow, presetOld, presetCenter, presetFar);
            group(new Setting.Group("g_extras", "Hands, swing & fire"), hideHand, hideOffhand, mirror, noEquip, swingSpeed, noBob, fire);
            // presets at the top
            Setting<?> pg = null;
            for (Setting<?> st : settings) if (st.id.equals("g_presets")) pg = st;
            if (pg != null) {
                List<Setting<?>> block = new java.util.ArrayList<Setting<?>>();
                int i = settings.indexOf(pg);
                for (int k = 0; k < 6; k++) block.add(settings.get(i + k));
                settings.removeAll(block);
                settings.addAll(0, block);
            }
        }

        private Setting.Number n(String id, String name, double def, double min, double max, double step, String suffix, Setting.Cond when) {
            return add(new Setting.Number(id, name, def, min, max, step, suffix), when);
        }

        private static int indexOf(String type) {
            for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(type)) return i;
            return 0;
        }

        /**
         * Type of a held item from its registry id (e.g. "minecraft:diamond_sword"); the platform
         * says whether it's a block item or edible/drinkable.
         */
        public static String typeOf(String itemId, boolean empty, boolean block, boolean food) {
            if (empty) return "hand";
            String path = itemId == null ? "" : itemId.substring(itemId.indexOf(':') + 1);
            if (path.equals("shield")) return "shield";
            if (path.endsWith("_sword")) return "sword";
            if (path.endsWith("_axe") || path.endsWith("_pickaxe") || path.endsWith("_shovel") || path.endsWith("_hoe")
                    || path.equals("mace") || path.equals("trident") || path.endsWith("_spear")) return "tool";
            if (path.equals("bow") || path.equals("crossbow")) return "bow";
            if (food) return "food";
            if (block) return "block";
            return "item";
        }

        /**
         * Transform for an item type: {x, y, z, rotX, rotY, rotZ, size}. "All items" plus the type's
         * own tweak. Null when the module is off.
         */
        private final float[] buf = new float[7];

        public float[] transform(String type) {
            if (!isEnabled()) return null;
            int t = 0;
            for (int i = 2; i < KEYS.length; i++) if (KEYS[i].equals(type)) t = i;
            if (t > 0 && inGroup[t] != null && inGroup[t].on()) t = GROUP;   // this type follows "Selected items"
            float[] out = buf;                                   // reused: this runs for every hand, every frame
            for (int k = 0; k < 6; k++) out[k] = values[0][k].f() + (t == 0 ? 0 : values[t][k].f());
            out[6] = values[0][6].f() * (t == 0 ? 1 : values[t][6].f());
            return out;
        }

        /** Vertical shift of the fire overlay (negative = lower). */
        public float fireOffset() { return isEnabled() ? fire.f() / 100f : 0; }
    }

    public static final class Fullbright extends Module {
        private boolean applied;

        public Fullbright() { super("fullbright", "Fullbright", "See clearly in the dark", Category.VISUAL, false); }

        /** Applied from the tick loop so it never runs before the game options exist. */
        @Override
        public void onTick() {
            if (!applied && p().inWorld()) {
                p().setFullbright(true);
                applied = true;
            }
        }

        @Override
        public void onDisable() {
            if (applied) p().setFullbright(false);
            applied = false;
        }
    }

    public static final class Crosshair extends Module {
        public final Setting.Mode style = add(new Setting.Mode("style", "Style", "Cross", "Cross", "T-Shape", "Dot", "Square", "Plus"));
        public final Setting.Number size = add(new Setting.Number("size", "Length", 4, 1, 12, 1, "px"));
        public final Setting.Number gap = add(new Setting.Number("gap", "Gap", 2, 0, 8, 1, "px"));
        public final Setting.Number thickness = add(new Setting.Number("thickness", "Thickness", 1, 1, 4, 1, "px"));
        public final Setting.Color color = add(new Setting.Color("color", "Color", 0xFFFFFFFF));
        public final Setting.Bool outline = add(new Setting.Bool("outline", "Outline", true));
        public final Setting.Bool dot = add(new Setting.Bool("dot", "Center dot", false));
        /** Vanilla-style: the crosshair inverts the colours behind it (always visible on any background). */
        public final Setting.Bool inverted = add(new Setting.Bool("inverted", "Inverted color (like vanilla)", false));

        public Crosshair() { super("crosshair", "Custom Crosshair", "Your own crosshair shape and color", Category.VISUAL, false); }

        public void draw(Render r) {
            int cx = r.width() / 2, cy = r.height() / 2;
            int t = thickness.i(), l = size.i(), g = gap.i(), c = color.argb();
            int ht = t / 2;
            String s = style.get();
            if (s.equals("Dot")) {
                bar(r, cx - ht - 1, cy - ht - 1, t + 2, t + 2, c);
                return;
            }
            if (s.equals("Square")) {
                int e = g + l;
                bar(r, cx - e, cy - e, e * 2 + 1, t, c);
                bar(r, cx - e, cy + e - t + 1, e * 2 + 1, t, c);
                bar(r, cx - e, cy - e + t, t, e * 2 + 1 - 2 * t, c);
                bar(r, cx + e - t + 1, cy - e + t, t, e * 2 + 1 - 2 * t, c);
                return;
            }
            if (s.equals("Plus")) g = 0;
            bar(r, cx - g - l, cy - ht, l, t, c);            // left
            bar(r, cx + g + 1, cy - ht, l, t, c);            // right
            bar(r, cx - ht, cy + g + 1, t, l, c);            // bottom
            if (!s.equals("T-Shape")) bar(r, cx - ht, cy - g - l, t, l, c); // top
            if (dot.on() || s.equals("Plus")) bar(r, cx - ht, cy - ht, t, t, c);
        }

        private void bar(Render r, int x, int y, int w, int h, int c) {
            if (w <= 0 || h <= 0) return;
            if (inverted.on()) {        // no outline / colour: the inversion itself keeps it visible
                r.rectInverted(x, y, w, h);
                return;
            }
            if (outline.on()) r.rect(x - 1, y - 1, w + 2, h + 2, 0xB0000000);
            r.rect(x, y, w, h, c);
        }
    }

    public static final class HitColor extends Module {
        public final Setting.Color color = add(new Setting.Color("color", "Color", 0xFFFF4D4D));
        public final Setting.Number opacity = add(new Setting.Number("opacity", "Opacity", 30, 5, 80, 5, "%"));

        public HitColor() { super("hitcolor", "Hit Color", "Change the red flash on hit entities", Category.VISUAL, false); }

        public int argb() {
            int a = (int) (opacity.get() / 100 * 255);
            return (a << 24) | (color.argb() & 0xFFFFFF);
        }
    }

    public static final class HurtCam extends Module {
        public HurtCam() { super("hurtcam", "No Hurt Camera", "Removes the screen shake when you take damage", Category.VISUAL, false); }
    }

    public static final class ItemPhysics extends Module {
        public final Setting.Number speed = add(new Setting.Number("speed", "Turn speed", 0, 0, 3, 0.1, "x"));

        public ItemPhysics() { super("itemphysics", "Item Physics", "Dropped items lie flat on the ground, no bobbing", Category.VISUAL, false); }
    }

    public static final class Zoom extends Module {
        public final Setting.Number level = add(new Setting.Number("level", "Zoom", 4, 2, 12, 0.5, "x"));
        public final Setting.Bool smooth = add(new Setting.Bool("smooth", "Smooth", true));
        public final Setting.Bool scroll = add(new Setting.Bool("scroll", "Scroll to adjust", true));
        public final Setting.Bool cinematic = add(new Setting.Bool("cinematic", "Smooth camera", false));
        public final Setting.Bool slowMouse = add(new Setting.Bool("slowmouse", "Lower mouse sensitivity", true));
        public final Setting.Bool hideHand = add(new Setting.Bool("hidehand", "Keep hand unzoomed", true));
        public final Setting.Bind key = add(new Setting.Bind("key", "Zoom key", -1, "zoom"));
        public boolean held;
        private double current = 1, extra = 1;
        private long last = System.nanoTime();

        public Zoom() { super("zoom", "Zoom", "Hold C to zoom (change in Controls)", Category.VISUAL, true); }

        /** FOV divisor for this frame. */
        public double divisor() {
            long now = System.nanoTime();
            double dt = Math.min(0.1, (now - last) / 1e9);
            last = now;
            double target = isEnabled() && held ? level.get() * extra : 1;
            if (!held) extra = 1;
            if (!smooth.on() || !Cobra.animations) current = target;
            else current += (target - current) * (1 - Math.exp(-dt * 14));
            if (Math.abs(current - target) < 0.002) current = target;
            return current;
        }

        public boolean active() { return isEnabled() && held; }

        /** Last FOV divisor handed out (for mouse scaling without advancing the animation). */
        public double lastDivisor() { return current; }

        /** Mouse sensitivity multiplier while zoomed: aiming feels the same at any zoom. */
        public double sensitivity() {
            if (!slowMouse.on() || current <= 1.01) return 1;
            return 1 / current;
        }

        /** Returns true if the scroll was consumed. */
        public boolean onScroll(double amount) {
            if (!active() || !scroll.on()) return false;
            extra = Math.max(0.5, Math.min(4, extra * (amount > 0 ? 1.15 : 1 / 1.15)));
            return true;
        }
    }

    public static final class Freelook extends Module {
        public final Setting.Mode mode = add(new Setting.Mode("mode", "Mode", "Hold", "Hold", "Toggle"));
        public final Setting.Bool invert = add(new Setting.Bool("invert", "Invert vertical", false));
        public final Setting.Bind key = add(new Setting.Bind("key", "Freelook key", -1, "freelook"));
        public boolean active;
        public float yaw, pitch;

        public Freelook() { super("freelook", "Freelook", "Hold Left Alt to look around without turning", Category.VISUAL, true); }

        /** Called with the key state each tick; returns whether freelook just started. */
        public boolean update(boolean keyDown, boolean keyPressedEdge, float playerYaw, float playerPitch) {
            boolean was = active;
            if (!isEnabled()) active = false;
            else if (mode.is("Hold")) active = keyDown;
            else if (keyPressedEdge) active = !active;
            if (active && !was) {
                yaw = playerYaw;
                pitch = playerPitch;
            }
            return active && !was;
        }

        public void turn(double dx, double dy) {
            yaw += (float) dx * 0.15f;
            pitch += (float) (invert.on() ? -dy : dy) * 0.15f;
            pitch = Math.max(-90, Math.min(90, pitch));
        }
    }

    public static final class TimeChanger extends Module {
        public final Setting.Mode preset = add(new Setting.Mode("preset", "Time", "Noon", "Morning", "Noon", "Sunset", "Night", "Midnight", "Custom"));
        public final Setting.Number custom = add(new Setting.Number("custom", "Custom time", 6000, 0, 24000, 250, ""));

        public TimeChanger() { super("timechanger", "Time Changer", "Client-side time of day", Category.VISUAL, false); }

        public long time() {
            String p = preset.get();
            if (p.equals("Morning")) return 1000;
            if (p.equals("Noon")) return 6000;
            if (p.equals("Sunset")) return 12500;
            if (p.equals("Night")) return 14000;
            if (p.equals("Midnight")) return 18000;
            return custom.i();
        }
    }

    public static final class FovModifier extends Module {
        public final Setting.Number fov = add(new Setting.Number("fov", "Field of view", 90, 30, 140, 1, ""));
        public final Setting.Bool dynamic = add(new Setting.Bool("dynamic", "Sprint & speed FOV", false));

        public FovModifier() { super("fov", "FOV Modifier", "FOV beyond vanilla limits, optional static FOV", Category.VISUAL, false); }
    }

    public static final class Particles extends Module {
        public final Setting.Number crits = add(new Setting.Number("crits", "Crit particles", 1, 0, 5, 1, "x"));
        public final Setting.Bool alwaysSharp = add(new Setting.Bool("sharp", "Always sharpness", true));
        public final Setting.Number sharp = add(new Setting.Number("sharpmult", "Sharpness particles", 1, 0, 5, 1, "x"));

        public Particles() { super("particles", "Particle Changer", "More hit particles on every hit", Category.VISUAL, false); }

        public void onHit(Object target) {
            p().spawnHitParticles(target, crits.i(), alwaysSharp.on() ? sharp.i() : 0);
        }
    }

    /** Switch off individual kinds of fog and the pumpkin blur. (Fire/shield height: View Model.) */
    public static final class Tweaks extends Module {
        public final Setting.Bool pumpkin = add(new Setting.Bool("pumpkin", "Disable pumpkin blur", true));
        public final Setting.Bool lavaFog = add(new Setting.Bool("lavafog", "Disable lava fog", true));
        public final Setting.Bool waterFog = add(new Setting.Bool("waterfog", "Disable water fog", false));
        public final Setting.Bool snowFog = add(new Setting.Bool("snowfog", "Disable powder snow fog", true));
        public final Setting.Bool blindFog = add(new Setting.Bool("blindfog", "Disable blindness fog", false));
        public final Setting.Bool darkFog = add(new Setting.Bool("darkfog", "Disable darkness fog", true));
        public final Setting.Bool terrainFog = add(new Setting.Bool("terrainfog", "Disable terrain fog", false));
        public final Setting.Bool thickFog = add(new Setting.Bool("thickfog", "Disable thick fog (Nether)", false));
        public final Setting.Bool skyFog = add(new Setting.Bool("skyfog", "Disable sky fog", false));

        public Tweaks() {
            super("tweaks", "Visual Tweaks", "Turn off fog types and the pumpkin blur", Category.VISUAL, false);
        }

        public boolean noPumpkin() { return isEnabled() && pumpkin.on(); }

        /**
         * Whether a kind of fog should be removed: "lava", "water", "snow", "blind", "dark",
         * "terrain", "thick", "sky".
         */
        public boolean noFog(String kind) {
            if (!isEnabled()) return false;
            if (kind.equals("lava")) return lavaFog.on();
            if (kind.equals("water")) return waterFog.on();
            if (kind.equals("snow")) return snowFog.on();
            if (kind.equals("blind")) return blindFog.on();
            if (kind.equals("dark")) return darkFog.on();
            if (kind.equals("terrain")) return terrainFog.on();
            if (kind.equals("thick")) return thickFog.on();
            if (kind.equals("sky")) return skyFog.on();
            return false;
        }
    }

    /** Recolours the outline around the block you're looking at, with an optional fill. */
    public static final class BlockOverlay extends Module {
        public final Setting.Bool outline = add(new Setting.Bool("outline", "Outline", true));
        public final Setting.Color outlineColor = add(new Setting.Color("outlinecolor", "Outline color", 0xCCFFFFFF));
        public final Setting.Bool fill = add(new Setting.Bool("fill", "Fill", true));
        public final Setting.Color fillColor = add(new Setting.Color("fillcolor", "Fill color", 0x33FFFFFF));
        public final Setting.Bool chroma = add(new Setting.Bool("chroma", "Chroma (rainbow)", false));
        public final Setting.Number chromaSpeed = add(new Setting.Number("chromaspeed", "Chroma speed", 1, 0.2, 4, 0.1, "x"));
        public final Setting.Number width = add(new Setting.Number("width", "Outline width", 2, 1, 8, 0.5, "px"));
        public final Setting.Bool pulse = add(new Setting.Bool("pulse", "Pulse (breathing outline)", false));
        public final Setting.Number pulseSpeed = add(new Setting.Number("pulsespeed", "Pulse speed", 1, 0.2, 4, 0.1, "x"),
                new Setting.Cond() { public boolean ok() { return pulse.on(); } });

        /** Outline alpha multiplier for the pulse (0.35..1). */
        private float pulseK() {
            if (!pulse.on()) return 1;
            double t = System.currentTimeMillis() / 1000.0 * pulseSpeed.f() * Math.PI;
            return (float) (0.675 + 0.325 * Math.sin(t));
        }

        public BlockOverlay() {
            super("blockoverlay", "Block Overlay", "Custom outline and fill on the block you look at", Category.VISUAL, false);
        }

        private int chromaRgb() {
            float h = (System.currentTimeMillis() % 100000L) / 1000f * chromaSpeed.f() * 0.25f;
            return dev.cobra.client.core.ui.Draw.hsv(h - (float) Math.floor(h), 0.75f, 1f, 255) & 0xFFFFFF;
        }

        public int outlineArgb() {
            int c = outlineColor.argb();
            c = chroma.on() ? (c & 0xFF000000) | chromaRgb() : c;
            int a = Math.round((c >>> 24) * pulseK());
            return a << 24 | (c & 0xFFFFFF);
        }

        public int fillArgb() {
            int c = fillColor.argb();
            return chroma.on() ? (c & 0xFF000000) | chromaRgb() : c;
        }
    }

    // ---------------------------------------------------------------- utility

    public static final class Waypoints extends Module {
        public static final class Point {
            public final String name, server, dim;
            public final int x, y, z, color;

            Point(String name, int x, int y, int z, int color, String server, String dim) {
                this.name = name;
                this.x = x;
                this.y = y;
                this.z = z;
                this.color = color;
                this.server = server;
                this.dim = dim;
            }
        }

        private static final int[] COLORS = {0xFF4CD964, 0xFF3DDCFF, 0xFFFF9F43, 0xFFB86BFF, 0xFFFF6BCB, 0xFFFFD93D};
        public final List<Point> points = new ArrayList<Point>();
        public final Setting.Bool distance = add(new Setting.Bool("distance", "Show distance", true));
        public final Setting.Bind key = add(new Setting.Bind("key", "Add waypoint key", -1, "waypoint"));

        public void remove(Point pt) {
            points.remove(pt);
            Cobra.save();
        }

        public Waypoints() {
            super("waypoints", "Waypoints", "Press B to mark your position", Category.UTILITY, true);
            add(new Setting.Action("add", "Add waypoint here", new Runnable() {
                @Override public void run() { addHere(); }
            }));
            add(new Setting.Action("clear", "Remove waypoints on this server", new Runnable() {
                @Override public void run() { clearHere(); }
            }));
        }

        public void addHere() {
            if (!p().inWorld()) return;
            int n = here().size() + 1;
            Point pt = new Point("Waypoint " + n, (int) Math.floor(p().x()), (int) Math.floor(p().y()), (int) Math.floor(p().z()),
                    COLORS[points.size() % COLORS.length], p().server(), p().dimension());
            points.add(pt);
            p().chat("\u00a77[\u00a7fCobra\u00a77] Added \u00a7f" + pt.name + "\u00a77 at " + pt.x + ", " + pt.y + ", " + pt.z);
            Cobra.save();
        }

        public void clearHere() {
            List<Point> keep = new ArrayList<Point>();
            for (Point pt : points) if (!pt.server.equals(p().server())) keep.add(pt);
            points.clear();
            points.addAll(keep);
            Cobra.save();
        }

        /** Waypoints for the current server and dimension. */
        public List<Point> here() {
            List<Point> out = new ArrayList<Point>();
            if (p() == null || !p().inWorld()) return out;
            String s = p().server(), d = p().dimension();
            for (Point pt : points) if (pt.server.equals(s) && pt.dim.equals(d)) out.add(pt);
            return out;
        }

        /** Projects each waypoint onto the screen and draws a label (visible through walls). */
        public void render(Render r) {
            double[] cam = p().camera();
            if (cam == null) return;
            double yaw = Math.toRadians(cam[3]), pitch = Math.toRadians(cam[4]);
            double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy; // up = right x forward
            double focal = (r.height() / 2.0) / Math.tan(Math.toRadians(cam[5]) / 2);
            for (Point pt : here()) {
                double dx = pt.x + 0.5 - cam[0], dy = pt.y + 1.2 - cam[1], dz = pt.z + 0.5 - cam[2];
                double zc = dx * fx + dy * fy + dz * fz;
                if (zc < 0.1) continue;
                double xc = dx * rx + dz * rz, yc = dx * ux + dy * uy + dz * uz;
                float sx = (float) (r.width() / 2.0 + xc / zc * focal), sy = (float) (r.height() / 2.0 - yc / zc * focal);
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                String label = pt.name + (distance.on() ? "  " + Math.round(dist) + "m" : "");
                int tw = r.textWidth(label);
                r.rect(Math.round(sx) - 2, Math.round(sy) - 14, 4, 4, pt.color);
                r.rect(Math.round(sx - tw / 2f) - 3, Math.round(sy) - 8, tw + 6, 11, 0x80000000);
                r.text(label, sx - tw / 2f, sy - 6, 0xFFFFFFFF, false);
            }
        }

        public void load(Properties props) {
            points.clear();
            for (int i = 0; ; i++) {
                String v = props.getProperty("waypoint." + i);
                if (v == null) break;
                String[] f = v.split("\\|", -1);
                if (f.length < 7) continue;
                try {
                    points.add(new Point(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                            (int) Long.parseLong(f[4], 16), f[5], f[6]));
                } catch (Exception ignored) {}
            }
        }

        public void save(Properties props) {
            for (int i = 0; i < points.size(); i++) {
                Point pt = points.get(i);
                props.setProperty("waypoint." + i, pt.name.replace("|", "") + "|" + pt.x + "|" + pt.y + "|" + pt.z + "|"
                        + Integer.toHexString(pt.color) + "|" + pt.server + "|" + pt.dim);
            }
        }
    }

    public static final class ChatMod extends Module {
        public final Setting.Bool timestamps = add(new Setting.Bool("timestamps", "Timestamps", true));
        public final Setting.Bool seconds = add(new Setting.Bool("seconds", "Include seconds", false));
        public final Setting.Bool history = add(new Setting.Bool("history", "Longer chat history", true));
        public final Setting.Bool stack = add(new Setting.Bool("stack", "Stack repeated messages (x2, x3...)", true));

        public ChatMod() { super("chat", "Better Chat", "Timestamps, stacked repeats and a 1000-line history", Category.UTILITY, false); }

        public String stamp() {
            return "\u00a78[\u00a77" + new SimpleDateFormat(seconds.on() ? "HH:mm:ss" : "HH:mm").format(new Date()) + "\u00a78]\u00a7r ";
        }

        public int historySize() { return isEnabled() && history.on() ? 1000 : 100; }
    }

    public static final class NickHider extends Module {
        public final Setting.Text nick = add(new Setting.Text("nick", "Shown name", "You", 16));

        public NickHider() { super("nickhider", "Nick Hider", "Replaces your name on your screen (for recording)", Category.UTILITY, false); }

        public String apply(String s) {
            if (s == null || !isEnabled() || p() == null) return s;
            String me = p().playerName();
            if (me == null || me.isEmpty() || s.indexOf(me) < 0) return s;
            return s.replace(me, nick.get());
        }
    }

    /** Watches the screenshots folder and uploads new shots to catbox.moe, copying the link. */
    public static final class ScreenshotUploader extends Module {
        public final Setting.Bool copy = add(new Setting.Bool("copy", "Copy link", true));
        private static final Map<String, Long> SEEN = new HashMap<String, Long>();

        public ScreenshotUploader() {
            super("screenshots", "Screenshot Uploader", "Uploads new F2 screenshots to catbox.moe and copies the link", Category.UTILITY, false);
        }

        public static void startWatcher(final File dir) {
            final long started = System.currentTimeMillis();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    Map<String, Long> sizes = new HashMap<String, Long>();
                    while (true) {
                        try {
                            Thread.sleep(1000);
                            File[] files = dir.listFiles();
                            if (files == null) continue;
                            for (File f : files) {
                                if (!f.getName().endsWith(".png") || f.lastModified() < started || SEEN.containsKey(f.getName())) continue;
                                Long prev = sizes.get(f.getName());
                                long len = f.length();
                                sizes.put(f.getName(), len);
                                if (prev == null || prev != len || len == 0) continue; // still being written
                                SEEN.put(f.getName(), len);
                                ScreenshotUploader m = Cobra.get(ScreenshotUploader.class);
                                if (m.isEnabled()) m.upload(f);
                            }
                        } catch (InterruptedException e) {
                            return;
                        } catch (Throwable ignored) {}
                    }
                }
            }, "cobra-screenshots");
            t.setDaemon(true);
            t.start();
        }

        private void upload(final File f) {
            try {
                String boundary = "----cobra" + System.nanoTime();
                HttpURLConnection c = (HttpURLConnection) new URL("https://catbox.moe/user/api.php").openConnection();
                c.setDoOutput(true);
                c.setConnectTimeout(15000);
                c.setReadTimeout(60000);
                c.setRequestMethod("POST");
                c.setRequestProperty("User-Agent", "CobraClient/" + Cobra.VERSION);
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                DataOutputStream out = new DataOutputStream(c.getOutputStream());
                out.writeBytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n");
                out.writeBytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + f.getName()
                        + "\"\r\nContent-Type: image/png\r\n\r\n");
                InputStream in = new FileInputStream(f);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                out.writeBytes("\r\n--" + boundary + "--\r\n");
                out.flush();
                out.close();
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                InputStream rin = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
                while ((n = rin.read(buf)) > 0) body.write(buf, 0, n);
                rin.close();
                final String url = body.toString("UTF-8").trim();
                final boolean ok = url.startsWith("https://");
                p().runOnMain(new Runnable() {
                    @Override
                    public void run() {
                        if (!ok) {
                            p().chat("\u00a77[\u00a7fCobra\u00a77] \u00a7cScreenshot upload failed.");
                            return;
                        }
                        if (copy.on()) p().clipboard(url);
                        p().chat("\u00a77[\u00a7fCobra\u00a77] Screenshot uploaded: \u00a7f" + url + (copy.on() ? " \u00a78(copied)" : ""));
                    }
                });
            } catch (final Exception e) {
                p().runOnMain(new Runnable() {
                    @Override public void run() { p().chat("\u00a77[\u00a7fCobra\u00a77] \u00a7cScreenshot upload failed: " + e.getMessage()); }
                });
            }
        }
    }

    static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}

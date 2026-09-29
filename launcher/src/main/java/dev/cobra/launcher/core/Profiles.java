package dev.cobra.launcher.core;

import com.google.gson.reflect.TypeToken;
import dev.cobra.launcher.game.GameVersion;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Profiles: a name, an optional picture and a chosen version. Each profile keeps its own
 * mods, packs, worlds and options per version, so a "PvP" profile and a "Survival" profile
 * never share mods.
 */
public final class Profiles {
    public static final class Profile {
        public String id;
        public String name;
        /** Category on the Profiles page (e.g. "PvP"), or null for none. */
        public String category;
        public String version = GameVersion.MODERN.id;
        public long created;

        public GameVersion gameVersion() { return GameVersion.byId(version); }

        /** The built-in FPS Boost profile: can't be deleted, its mods are fixed. */
        public boolean locked() { return FPS_ID.equals(id); }
    }

    /** Id of the built-in FPS Boost profile. */
    public static final String FPS_ID = "fps-boost";

    /**
     * Mods of the FPS Boost profile (Modrinth slugs). Sodium first; the rest are made to work with
     * it. Anything Modrinth doesn't have for 1.21.11 yet is skipped until it's out.
     */
    public static final List<String> FPS_MODS = List.of(
            "sodium",            // rendering engine, the big one
            "lithium",           // game logic / physics / AI
            "ferrite-core",      // memory
            "entityculling",     // skip hidden entities and block entities
            "immediatelyfast",   // HUD, text and particle batching
            "modernfix",         // startup + memory fixes
            "moreculling",       // extra block face culling (Sodium-aware)
            "dynamic-fps",       // lowers FPS when the game is in the background
            "sodium-extra",      // extra Sodium options (fog, particles, animations)
            "reeses-sodium-options" // nicer Sodium options screen
    );

    private static final Path FILE = Paths.ROOT.resolve("profiles.json");
    private static final Path IMAGES = Paths.ROOT.resolve("profile-images");
    private static final Map<String, BufferedImage> ICONS = new HashMap<>();
    private static List<Profile> list;

    private Profiles() {}

    public static synchronized List<Profile> all() {
        if (list == null) {
            try {
                if (Files.exists(FILE)) list = Http.GSON.fromJson(Files.readString(FILE), new TypeToken<List<Profile>>() {}.getType());
            } catch (Exception ignored) {}
            if (list == null) list = new ArrayList<>();
            migrateVersions();
            if (list.stream().noneMatch(Profile::locked)) {   // the built-in FPS Boost profile, always there
                Profile f = new Profile();
                f.id = FPS_ID;
                f.name = "FPS Boost";
                f.created = System.currentTimeMillis();
                list.add(f);
                save();
            }
            if (list.stream().noneMatch(x -> !x.locked())) {
                // "default" maps onto the original instances/<version> folders, so nothing gets lost
                Profile p = new Profile();
                p.id = "default";
                p.name = "Default";
                p.version = Settings.get().lastVersion;
                p.created = System.currentTimeMillis();
                list.add(p);
                save();
            }
        }
        return list;
    }

    /**
     * Cobra only runs 1.21.11 now. Profiles on other versions switch to it, and their worlds,
     * resource packs and server list move into the 1.21.11 folder (plus options and Cobra's
     * settings when coming from another 1.21 version). Mods stay behind in the old folder:
     * they're built for the old version and would crash 1.21.11.
     */
    private static void migrateVersions() {
        boolean changed = false;
        String now = GameVersion.MODERN.id;
        for (Profile p : list) {
            if (now.equals(p.version)) continue;
            String old = p.version == null ? "" : p.version;
            p.version = now;
            changed = true;
            try {
                Path from = Paths.instance(p.id.equals("default") ? old : "profiles/" + p.id + "/" + old);
                Path to = gameDir(p, GameVersion.MODERN);
                if (old.isEmpty() || !Files.isDirectory(from) || Files.exists(to.resolve("saves"))) continue;
                Files.createDirectories(to);
                java.util.List<String> keep = new ArrayList<>(java.util.List.of("saves", "resourcepacks", "servers.dat", "screenshots"));
                if (old.startsWith("1.21")) {
                    keep.add("options.txt");
                    keep.add("config");
                }
                for (String name : keep) {
                    Path a = from.resolve(name), b = to.resolve(name);
                    if (Files.exists(a) && !Files.exists(b)) {
                        try {
                            Files.move(a, b);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (!now.equals(Settings.get().lastVersion)) {
            Settings.get().lastVersion = now;
            Settings.get().save();
        }
        if (changed) save();
    }

    public static synchronized Profile current() {
        String sel = Settings.get().selectedProfile;
        for (Profile p : all()) if (p.id.equals(sel)) return p;
        return all().get(0);
    }

    public static synchronized void select(Profile p) {
        Settings.get().selectedProfile = p.id;
        Settings.get().save();
    }

    public static synchronized Profile create(String name, GameVersion version) {
        Profile p = new Profile();
        p.id = UUID.randomUUID().toString().substring(0, 8);
        p.name = name == null || name.isBlank() ? "Profile " + (all().size() + 1) : name.trim();
        p.version = version.id;
        p.created = System.currentTimeMillis();
        all().add(p);
        save();
        return p;
    }

    public static synchronized void delete(Profile p) throws IOException {
        if (p.locked()) throw new IOException("The FPS Boost profile is built in and can't be deleted.");
        if (all().size() <= 1) return;
        all().removeIf(x -> x.id.equals(p.id));
        ICONS.remove(p.id);
        Files.deleteIfExists(IMAGES.resolve(p.id + ".png"));
        if (!p.id.equals("default")) deleteRecursive(Paths.INSTANCES.resolve("profiles").resolve(p.id));
        if (p.id.equals(Settings.get().selectedProfile)) select(all().get(0));
        save();
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, Http.GSON.toJson(list));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Game folder for a profile + version (mods, resourcepacks, saves, options.txt). */
    public static Path gameDir(Profile p, GameVersion gv) {
        String key = p.id.equals("default") ? gv.id : "profiles/" + p.id + "/" + gv.id;
        return Paths.instance(key);
    }

    public static Path gameDir(GameVersion gv) {
        return gameDir(current(), gv);
    }

    // ------------------------------------------------------------------ pictures

    public static synchronized BufferedImage icon(Profile p) {
        if (ICONS.containsKey(p.id)) return ICONS.get(p.id);
        BufferedImage img = null;
        try {
            Path f = IMAGES.resolve(p.id + ".png");
            if (Files.exists(f)) img = ImageIO.read(f.toFile());
        } catch (Exception ignored) {}
        ICONS.put(p.id, img);
        return img;
    }

    /** Crops the picked image to a centred square, scales it to 128 px and stores it. */
    public static synchronized void setIcon(Profile p, Path source) throws IOException {
        BufferedImage src = ImageIO.read(source.toFile());
        if (src == null) throw new IOException("That file isn't a PNG, JPG, GIF or BMP image.");
        int side = Math.min(src.getWidth(), src.getHeight());
        BufferedImage sq = src.getSubimage((src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
        BufferedImage out = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(sq, 0, 0, 128, 128, null);
        g.dispose();
        Files.createDirectories(IMAGES);
        ImageIO.write(out, "png", IMAGES.resolve(p.id + ".png").toFile());
        ICONS.put(p.id, out);
    }

    public static synchronized void clearIcon(Profile p) throws IOException {
        Files.deleteIfExists(IMAGES.resolve(p.id + ".png"));
        ICONS.put(p.id, null);
    }

    private static void deleteRecursive(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : (Iterable<Path>) s.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(x);
        }
    }
}

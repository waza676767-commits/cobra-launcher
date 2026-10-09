package dev.life.client.core;

import java.io.File;
import java.util.List;

/** Everything the shared client needs from a specific Minecraft version. */
public interface Platform {
    enum Key { FORWARD, LEFT, BACK, RIGHT, JUMP, SNEAK, SPRINT, ATTACK, USE }

    enum TitleAction { SINGLEPLAYER, MULTIPLAYER, OPTIONS, RESOURCE_PACKS, LIFE_MENU, VANILLA_MENU, QUIT }

    final class Effect {
        public final Object handle;
        public final String name;
        public final int amplifier;
        public final String duration;

        public Effect(Object handle, String name, int amplifier, String duration) {
            this.handle = handle;
            this.name = name;
            this.amplifier = amplifier;
            this.duration = duration;
        }
    }

    final class Boss {
        public final Object name;
        public final float percent;
        public final int color;

        public Boss(Object name, float percent, int color) {
            this.name = name;
            this.percent = percent;
            this.color = color;
        }
    }

    final class Sidebar {
        public final Object title;
        public final List<Object> names;
        public final List<Object> scores;

        public Sidebar(Object title, List<Object> names, List<Object> scores) {
            this.title = title;
            this.names = names;
            this.scores = scores;
        }
    }

    String version();

    File gameDir();

    boolean inWorld();

    boolean screenOpen();

    boolean hudHidden();

    int fps();

    int ping();

    double x();

    double y();

    double z();

    float yaw();

    String dimension();

    String server();

    String playerName();

    boolean key(Key k);

    /** Returns true once per physical key press (consumes it). */
    boolean wasPressed(Key k);

    void setKey(Key k, boolean down);

    /** Armor from helmet to boots, then main hand. Empty slots are null. */
    List<Object> equipment();

    String durability(Object stack);

    List<Effect> effects();

    List<Boss> bosses();

    Sidebar sidebar();

    int hurtTime(Object entity);

    int playerHurtTime();

    void spawnHitParticles(Object target, int crits, int sharpness);

    void setFullbright(boolean on);

    void chat(String message);

    void title(String title, String subtitle);

    void clipboard(String s);

    void runOnMain(Runnable r);

    void titleAction(TitleAction a);

    void openMenu();

    /** Physical key state by native key code (GLFW on 1.21, LWJGL 2 on 1.8.9); false for -1. */
    boolean rawKeyDown(int code);

    /** Display name of a native key code, "NONE" for -1. */
    String keyName(int code);

    /** Native key code of one of Life's own key bindings: menu, zoom, freelook, waypoint. */
    int bindCode(String id);

    /** Rebinds one of Life's key bindings and saves it to options.txt. */
    void setBind(String id, int code);

    /** Custom pointer on/off, ivory (dark theme) or charcoal (light theme). */
    void applyCursor(boolean enabled, boolean light);

    final class Teammate {
        public final String name;
        public final double distance;
        public final float health;

        public Teammate(String name, double distance, float health) {
            this.name = name;
            this.distance = distance;
            this.health = health;
        }
    }

    /** Total count of an item id (e.g. "minecraft:arrow") in the player's inventory; -1 = held item. */
    int countItem(String id);

    /** An item stack handle for drawing an icon ("held" = main hand item), or null. */
    Object itemStack(String id);

    /** Players on your scoreboard team that are loaded nearby. */
    List<Teammate> teammates();

    void setChunkBorders(boolean on);

    void setHitboxes(boolean on);

    /** Sends a command as if typed (without the leading slash). */
    void command(String command);

    /** Camera x, y, z, yaw, pitch, vertical FOV for this frame, or null when not in a world. */
    double[] camera();

    void closeScreen();

    /** Your health and max health (0 outside a world). */
    default float health() { return 20; }

    default float maxHealth() { return 20; }

    /** Plays a Minecraft sound (e.g. "entity.experience_orb.pickup") just for you. */
    default void playSound(String id, float volume, float pitch) {}

    /** Sends a chat message to the server as you. */
    default void say(String message) {}

    /** Looking up/down in degrees (-90 up … 90 down). */
    default float pitch() { return 0; }

    /** Is your player sprinting right now? */
    default boolean sprinting() { return false; }

    /** The selected hotbar slot (0-8), or -1 outside a world. */
    default int hotbarSlot() { return -1; }

    /** Saturation (the hidden food bar), or -1 outside a world. */
    default float saturation() { return -1; }

    /** Food level 0-20. */
    default int food() { return 20; }

    /** Names of the resource packs you have on (not the built-in / mod ones), top pack first. */
    default java.util.List<String> activePacks() { return java.util.Collections.emptyList(); }

    /** Your UUID while in a world (for the Life online list), or null. */
    default String playerUuid() { return null; }

    /** Item stack for the block the crosshair is on (for its icon), or null. */
    default Object targetBlockStack() { return null; }

    /** Display name of the block the crosshair is on, or null when not looking at a block. */
    default String targetBlockName() { return null; }

    /** Extra line for Block Info (e.g. "minecraft:stone" or the tool it needs), or null. */
    default String targetBlockId() { return null; }
}

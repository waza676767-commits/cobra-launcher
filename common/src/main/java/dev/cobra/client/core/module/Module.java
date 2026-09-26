package dev.cobra.client.core.module;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    public final List<Setting<?>> settings = new ArrayList<Setting<?>>();
    private boolean enabled;
    /** Icon texture name under textures/gui/icon (module cards). */
    public String icon = "star";
    /** Hidden modules don't get a card (the Client settings live in their own tab). */
    public boolean hidden;
    /** Set by a feature that couldn't hook into this version; shown on its card. */
    public String problem;
    /** Per-module toggle key (added to every module by Cobra.init). */
    public Setting.Bind toggleKey;
    public boolean keyWasDown;

    protected Module(String id, String name, String description, Category category, boolean enabledByDefault) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = enabledByDefault;
    }

    protected <S extends Setting<?>> S add(S s) {
        settings.add(s);
        return s;
    }

    /** Adds a setting that's only listed while {@code when} holds (e.g. per item type). */
    protected <S extends Setting<?>> S add(S s, Setting.Cond when) {
        s.visibleIf = when;
        settings.add(s);
        return s;
    }

    /** The settings currently listed in the menu. */
    public List<Setting<?>> shownSettings() {
        List<Setting<?>> out = new ArrayList<Setting<?>>();
        for (Setting<?> s : settings) if (s.shown()) out.add(s);
        return out;
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on;
        if (on) onEnable();
        else onDisable();
    }

    public void toggle() { setEnabled(!enabled); }

    /** Restores state after config load without firing callbacks twice. */
    public void loadEnabled(boolean on) {
        enabled = on;
        if (on) onEnable();
    }

    public void onEnable() {}

    public void onDisable() {}

    public void onTick() {}
}

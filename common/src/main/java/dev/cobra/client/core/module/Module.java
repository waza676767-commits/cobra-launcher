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

    /**
     * Puts {@code members} under a fold-out heading: the heading goes where the first one was,
     * and each member only shows while the heading is open (or the search finds it).
     */
    protected void group(final Setting.Group g, Setting<?>... members) {
        int at = settings.indexOf(members[0]);
        settings.add(Math.max(0, at), g);
        for (final Setting<?> m : members) {
            final Setting.Cond old = m.visibleIf;
            m.visibleIf = new Setting.Cond() {
                public boolean ok() { return g.open() && (old == null || old.ok()); }
            };
            g.add(m);
            settings.remove(m);
        }
        // members follow their heading, in the order given
        int pos = settings.indexOf(g) + 1;
        for (Setting<?> m : members) settings.add(pos++, m);
    }

    /** Does the search text hit this module's name, description or one of its settings? */
    public boolean matches(String q) {
        if (q == null || q.isEmpty()) return true;
        if (name.toLowerCase().contains(q) || description.toLowerCase().contains(q)) return true;
        for (Setting<?> s : settings) if (s.name.toLowerCase().contains(q)) return true;
        return false;
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

    /**
     * Puts every setting of this module back to its default (and, for HUD elements, its default
     * place and size). The module stays on or off as it is.
     */
    public void resetSettings() {
        for (Setting<?> st : settings) st.reset();
        if (this instanceof HudModule) {
            try {
                HudModule fresh = (HudModule) getClass().getDeclaredConstructor().newInstance();
                ((HudModule) this).px = fresh.px;
                ((HudModule) this).py = fresh.py;
            } catch (Exception ignored) {}
        }
        problem = null;
    }
}

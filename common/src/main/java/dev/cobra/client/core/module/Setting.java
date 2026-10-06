package dev.cobra.client.core.module;

public abstract class Setting<T> {
    public final String id;
    public final String name;
    protected T value;
    protected final T def;

    /** Show this setting only while the condition holds (null = always shown). */
    public interface Cond {
        boolean ok();
    }

    public Cond visibleIf;

    public boolean shown() {
        return visibleIf == null || visibleIf.ok();
    }

    protected Setting(String id, String name, T def) {
        this.id = id;
        this.name = name;
        this.value = def;
        this.def = def;
    }

    public T get() { return value; }

    public void set(T v) { value = v; }

    public void reset() { value = def; }

    public abstract String save();

    public abstract void load(String s);

    public static final class Bool extends Setting<Boolean> {
        public Bool(String id, String name, boolean def) { super(id, name, def); }
        public boolean on() { return value; }
        public void toggle() { value = !value; }
        @Override public String save() { return String.valueOf(value); }
        @Override public void load(String s) { value = Boolean.parseBoolean(s); }
    }

    public static final class Number extends Setting<Double> {
        public final double min, max, step;
        public final String suffix;

        public Number(String id, String name, double def, double min, double max, double step, String suffix) {
            super(id, name, def);
            this.min = min;
            this.max = max;
            this.step = step;
            this.suffix = suffix;
        }

        public float f() { return value.floatValue(); }
        public int i() { return (int) Math.round(value); }

        @Override
        public void set(Double v) {
            double s = Math.round(v / step) * step;
            value = Math.max(min, Math.min(max, s));
        }

        public String display() {
            double v = value;
            String num = step >= 1 ? String.valueOf((long) Math.round(v)) : String.format(java.util.Locale.ROOT, step >= 0.1 ? "%.1f" : "%.2f", v);
            return num + suffix;
        }

        @Override public String save() { return String.valueOf(value); }
        @Override public void load(String s) { try { set(Double.parseDouble(s)); } catch (Exception ignored) {} }
    }

    public static final class Mode extends Setting<String> {
        public final String[] modes;

        public Mode(String id, String name, String def, String... modes) {
            super(id, name, def);
            this.modes = modes;
        }

        public boolean is(String m) { return value.equals(m); }

        public void cycle(int dir) {
            int i = 0;
            for (int k = 0; k < modes.length; k++) if (modes[k].equals(value)) i = k;
            value = modes[(i + dir + modes.length) % modes.length];
        }

        @Override public String save() { return value; }
        @Override public void load(String s) { for (String m : modes) if (m.equals(s)) value = s; }
    }

    public static final class Color extends Setting<Integer> {
        public static final int[] PRESETS = {0xFFFFFFFF, 0xFFB5B5B5, 0xFF646464, 0xFFFF4D4D, 0xFFFF9F43, 0xFFFFD93D,
                0xFF4CD964, 0xFF3DDCFF, 0xFF4D7CFF, 0xFFB86BFF, 0xFFFF6BCB};

        public Color(String id, String name, int def) { super(id, name, def); }
        public int argb() { return value; }
        @Override public String save() { return Integer.toHexString(value); }
        @Override public void load(String s) { try { value = (int) Long.parseLong(s, 16); } catch (Exception ignored) {} }
    }

    public static final class Text extends Setting<String> {
        public final int maxLength;

        public Text(String id, String name, String def, int maxLength) {
            super(id, name, def);
            this.maxLength = maxLength;
        }

        @Override public String save() { return value; }
        @Override public void load(String s) { value = s; }
    }

    /**
     * A key binding. With {@code global} set it mirrors one of the client's Minecraft key bindings
     * (menu, zoom, freelook, waypoint); otherwise it's stored in Cobra's config. -1 = none.
     */
    public static final class Bind extends Setting<Integer> {
        public final String global;

        public Bind(String id, String name, int def, String global) {
            super(id, name, def);
            this.global = global;
        }

        @Override
        public Integer get() {
            if (global != null && dev.cobra.client.core.Cobra.platform != null) return dev.cobra.client.core.Cobra.platform.bindCode(global);
            return value;
        }

        @Override
        public void set(Integer v) {
            if (global != null && dev.cobra.client.core.Cobra.platform != null) dev.cobra.client.core.Cobra.platform.setBind(global, v);
            else value = v;
        }

        public int code() { return get(); }

        @Override public String save() { return global != null ? null : String.valueOf(value); }
        @Override public void load(String s) { try { value = Integer.parseInt(s); } catch (Exception ignored) {} }
    }

    /** A button in the settings list. */
    public static final class Action extends Setting<Runnable> {
        public Action(String id, String name, Runnable r) { super(id, name, r); }
        public void run() { value.run(); }
        @Override public String save() { return null; }
        @Override public void load(String s) {}
    }

    /**
     * A fold-out heading in a module's settings: the settings under it only show while it's open
     * (click it), or when the menu's search matches one of them.
     */
    public static final class Group extends Setting<Boolean> {
        /** What's typed in the Right Shift menu's search (lower case), set by the menu. */
        public static volatile String search = "";
        private final java.util.List<Setting<?>> members = new java.util.ArrayList<Setting<?>>();

        public Group(String id, String name) {
            super(id, name, false);
        }

        public boolean open() { return value || matches(); }

        public void toggle() { value = !value; }

        public void add(Setting<?> s) { members.add(s); }

        @Override
        public String save() { return String.valueOf(value); }

        @Override
        public void load(String s) { value = Boolean.parseBoolean(s); }

        /** Does the search hit this group or something in it? */
        public boolean matches() {
            String q = search;
            if (q == null || q.isEmpty()) return false;
            if (name.toLowerCase().contains(q)) return true;
            for (Setting<?> m : members) if (m.name.toLowerCase().contains(q)) return true;
            return false;
        }

        /** Shown only while the group is open. */
        public Cond inside() {
            return new Cond() { public boolean ok() { return open(); } };
        }
    }
}

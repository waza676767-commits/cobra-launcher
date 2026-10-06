package dev.cobra.client.core.cosmetics;

/**
 * The geometry of every cosmetic, as shaded boxes in a body part's space (1 unit = 1 skin pixel;
 * y points DOWN, the face is at -z and the back at +z). Kept apart from Minecraft so it can be
 * checked and previewed outside the game; the client passes an {@link Out} that turns the quads
 * into vertices.
 */
public final class CosmeticModels {
    /** Light value meaning "full bright" (glowing parts). */
    public static final int BRIGHT = 0xF000F0;

    /** Receives one four-cornered face: xyz of 4 corners, its normal, colour and light. */
    public interface Out {
        void quad(float[] p, float nx, float ny, float nz, int argb, int light);
    }

    /** A small affine transform (3x4). */
    public static final class M {
        final float[] a = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};

        public static M identity() { return new M(); }

        public M copy() {
            M m = new M();
            System.arraycopy(a, 0, m.a, 0, 12);
            return m;
        }

        public M translate(float x, float y, float z) {
            a[3] += a[0] * x + a[1] * y + a[2] * z;
            a[7] += a[4] * x + a[5] * y + a[6] * z;
            a[11] += a[8] * x + a[9] * y + a[10] * z;
            return this;
        }

        private M mul3(float b00, float b01, float b02, float b10, float b11, float b12, float b20, float b21, float b22) {
            float[] r = new float[12];
            for (int i = 0; i < 3; i++) {
                float x = a[i * 4], y = a[i * 4 + 1], z = a[i * 4 + 2];
                r[i * 4] = x * b00 + y * b10 + z * b20;
                r[i * 4 + 1] = x * b01 + y * b11 + z * b21;
                r[i * 4 + 2] = x * b02 + y * b12 + z * b22;
                r[i * 4 + 3] = a[i * 4 + 3];
            }
            System.arraycopy(r, 0, a, 0, 12);
            return this;
        }

        /** Uniform scale (a cosmetic's size). */
        public M scale(float k) {
            for (int i = 0; i < 3; i++) {
                a[i * 4] *= k;
                a[i * 4 + 1] *= k;
                a[i * 4 + 2] *= k;
            }
            return this;
        }

        public M rotX(float t) {
            float c = (float) Math.cos(t), s = (float) Math.sin(t);
            return mul3(1, 0, 0, 0, c, -s, 0, s, c);
        }

        public M rotY(float t) {
            float c = (float) Math.cos(t), s = (float) Math.sin(t);
            return mul3(c, 0, s, 0, 1, 0, -s, 0, c);
        }

        public M rotZ(float t) {
            float c = (float) Math.cos(t), s = (float) Math.sin(t);
            return mul3(c, -s, 0, s, c, 0, 0, 0, 1);
        }

        void apply(float x, float y, float z, float[] out, int o) {
            out[o] = a[0] * x + a[1] * y + a[2] * z + a[3];
            out[o + 1] = a[4] * x + a[5] * y + a[6] * z + a[7];
            out[o + 2] = a[8] * x + a[9] * y + a[10] * z + a[11];
        }

        float[] dir(float x, float y, float z) {
            return new float[]{a[0] * x + a[1] * y + a[2] * z, a[4] * x + a[5] * y + a[6] * z, a[8] * x + a[9] * y + a[10] * z};
        }
    }

    private CosmeticModels() {}

    /** A box from (x0,y0,z0) to (x1,y1,z1), with lighter top and darker sides. */
    public static void box(M e, Out vc, int light, int argb, float x0, float y0, float z0, float x1, float y1, float z1) {
        int top = shade(argb, 1.08f), side = argb, dark = shade(argb, 0.78f), bottom = shade(argb, 0.62f);
        quad(e, vc, light, top, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
        quad(e, vc, light, bottom, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);
        quad(e, vc, light, side, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1);
        quad(e, vc, light, dark, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1);
        quad(e, vc, light, dark, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0);
        quad(e, vc, light, side, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0);
    }

    private static void quad(M e, Out vc, int light, int argb, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
        float[] p = new float[12];
        e.apply(ax, ay, az, p, 0);
        e.apply(bx, by, bz, p, 3);
        e.apply(cx, cy, cz, p, 6);
        e.apply(dx, dy, dz, p, 9);
        float[] n = e.dir(nx, ny, nz);
        vc.quad(p, n[0], n[1], n[2], argb, light);
    }

    public static int shade(int argb, float k) {
        int r = Math.min(255, Math.round((argb >> 16 & 255) * k)), g = Math.min(255, Math.round((argb >> 8 & 255) * k)),
                b = Math.min(255, Math.round((argb & 255) * k));
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** Colour names shared by every cosmetic. */
    /** The halo's colour for its code: angel gold, red, or a custom x-colour. */
    public static int haloColour(String c) {
        if ("red".equals(c)) return 0xFFFF3B3B;
        if (c != null && c.length() == 7 && c.charAt(0) == 'x') return colour(c);
        return 0xFFFFE08A;
    }

    public static int colour(String c) {
        String k = c == null ? "" : c;
        if (k.length() == 7 && k.charAt(0) == 'x') {                    // a custom colour: x + RRGGBB
            try {
                return 0xFF000000 | Integer.parseInt(k.substring(1), 16);
            } catch (NumberFormatException ignored) {}
        }
        if (k.equals("white") || k.equals("angel")) return 0xFFF4F4EF;
        if (k.equals("red")) return 0xFFD62B3A;
        if (k.equals("gold")) return 0xFFE8B63A;
        if (k.equals("blue")) return 0xFF3F86E8;
        if (k.equals("purple")) return 0xFF8B5CF6;
        if (k.equals("pink")) return 0xFFF4A6C8;
        if (k.equals("green")) return 0xFF34C77B;
        if (k.equals("ginger") || k.equals("orange")) return 0xFFE08A3C;
        if (k.equals("brown")) return 0xFF7A5234;
        if (k.equals("cyan")) return 0xFF3FD0E0;
        if (k.equals("silver")) return 0xFFC8CDD4;
        return 0xFF1E1E22;
    }

    static M moved(M e, float x, float y, float z, float ry, float rx, float rz) {
        M m = e.copy().translate(x, y, z);
        if (ry != 0) m.rotY(ry);
        if (rx != 0) m.rotX(rx);
        if (rz != 0) m.rotZ(rz);
        return m;
    }

    static int tint(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    /** Pointy cat ears with a pink inside, sitting on the top corners of the head. */
    public static void catEars(M e, Out vc, int light, int c) {
        for (int s = -1; s <= 1; s += 2) {
            M q = moved(e, s * 2.8f, -8f, 0f, 0, 0, s * 0.18f);
            box(q, vc, light, c, -2.2f, -1.6f, -1f, 2.2f, 0.2f, 1f);
            box(q, vc, light, c, -1.6f, -3.2f, -0.9f, 1.6f, -1.6f, 0.9f);
            box(q, vc, light, c, -1f, -4.6f, -0.8f, 1f, -3.2f, 0.8f);
            box(q, vc, light, c, -0.4f, -5.6f, -0.7f, 0.4f, -4.6f, 0.7f);
            box(q, vc, light, 0xFFF3A6B8, -1.2f, -3.8f, -1.15f, 1.2f, -0.2f, -1f);      // inner ear, facing forward
        }
    }

    /** Tall bunny ears that droop and bob a little. */
    public static void bunnyEars(M e, Out vc, int light, int c, float t) {
        for (int s = -1; s <= 1; s += 2) {
            float bob = (float) Math.sin(t * 0.09 + s) * 0.06f;
            M q = moved(e, s * 2.2f, -8f, 0f, 0, -0.15f + bob, s * 0.12f);
            box(q, vc, light, c, -1.3f, -10f, -0.7f, 1.3f, 0.2f, 0.7f);
            box(q, vc, light, 0xFFF3A6B8, -0.7f, -9f, -0.85f, 0.7f, -1f, -0.7f);
        }
    }

    /** Curved devil horns. */
    public static void horns(M e, Out vc, int light, int c) {
        for (int s = -1; s <= 1; s += 2) {
            M q = moved(e, s * 3f, -8f, -1f, 0, 0, s * 0.35f);
            box(q, vc, light, c, -1.1f, -1.8f, -1.1f, 1.1f, 0.3f, 1.1f);
            box(q, vc, light, shade(c, 1.05f), -0.8f, -3.4f, -0.8f, 0.8f, -1.8f, 0.8f);
            M tip = moved(q, 0, -3.4f, 0, 0, 0, s * 0.5f);
            box(tip, vc, light, shade(c, 1.12f), -0.5f, -2f, -0.5f, 0.5f, 0f, 0.5f);
        }
    }

    /** Hats: a gold crown, a black top hat with a red band, or a purple witch hat. */
    public static void hat(M e, Out vc, int light, String kind) {
        String kd = kind == null ? "" : kind;
        if (kd.equals("tophat")) {
                box(e, vc, light, 0xFF18181C, -6f, -8.8f, -6f, 6f, -8f, 6f);           // brim
                box(e, vc, light, 0xFF18181C, -4.2f, -15f, -4.2f, 4.2f, -8.8f, 4.2f);  // crown
                box(e, vc, light, 0xFFB3202C, -4.35f, -10.4f, -4.35f, 4.35f, -9f, 4.35f); // band
        } else if (kd.equals("witch")) {
                int c = 0xFF4B2A7A;
                box(e, vc, light, c, -7f, -8.8f, -7f, 7f, -8f, 7f);
                float[][] tiers = {{4.4f, -11f}, {3.4f, -13.5f}, {2.4f, -16f}, {1.4f, -18.2f}, {0.6f, -20f}};
                float y = -8.8f;
                for (float[] tr : tiers) {
                    box(e, vc, light, c, -tr[0], tr[1], -tr[0], tr[0], y, tr[0]);
                    y = tr[1];
                }
                box(e, vc, light, 0xFFE8B63A, -4.5f, -10f, -4.5f, 4.5f, -9f, 4.5f);    // gold band
        } else {                                                                       // crown
                int g = 0xFFE8B63A;
                box(e, vc, light, g, -4.6f, -10f, -4.6f, 4.6f, -8.2f, 4.6f);
                for (int i = 0; i < 4; i++) {                                          // points on each side
                    float p = -3.6f + i * 2.4f;
                    box(e, vc, light, g, p - 0.6f, -11.8f, -4.6f, p + 0.6f, -10f, -4f);
                    box(e, vc, light, g, p - 0.6f, -11.8f, 4f, p + 0.6f, -10f, 4.6f);
                    box(e, vc, light, g, -4.6f, -11.8f, p - 0.6f, -4f, -10f, p + 0.6f);
                    box(e, vc, light, g, 4f, -11.8f, p - 0.6f, 4.6f, -10f, p + 0.6f);
                }
                box(e, vc, light, 0xFFD62B3A, -0.7f, -9.6f, -4.9f, 0.7f, -8.6f, -4.6f); // gems
                box(e, vc, light, 0xFF3F86E8, -2.9f, -9.6f, -4.9f, -1.9f, -8.6f, -4.6f);
                box(e, vc, light, 0xFF34C77B, 1.9f, -9.6f, -4.9f, 2.9f, -8.6f, -4.6f);
        }
    }

    /** Deer antlers: branching tines from the top of the head. */
    public static void antlers(M e, Out vc, int light, int c) {
        for (int s = -1; s <= 1; s += 2) {
            M a = moved(e, s * 2.6f, -8f, 0.5f, 0, -0.2f, s * 0.45f);
            box(a, vc, light, c, -0.6f, -7f, -0.6f, 0.6f, 0.2f, 0.6f);                        // main beam
            M t1 = moved(a, 0, -3f, 0, 0, 0, s * -0.9f);
            box(t1, vc, light, shade(c, 1.08f), -0.45f, -3.2f, -0.45f, 0.45f, 0f, 0.45f);    // tines
            M t2 = moved(a, 0, -5.6f, 0, 0, 0, s * -0.8f);
            box(t2, vc, light, shade(c, 1.08f), -0.4f, -2.6f, -0.4f, 0.4f, 0f, 0.4f);
            M t3 = moved(a, 0, -7f, 0, 0, 0, s * 0.5f);
            box(t3, vc, light, shade(c, 1.12f), -0.35f, -2.4f, -0.35f, 0.35f, 0f, 0.35f);
        }
    }

    /** Three glowing gems orbiting your head. */
    public static void orbit(M e, Out vc, int light, float t, int c) {
        for (int i = 0; i < 3; i++) {
            double a = t * 0.08 + i * Math.PI * 2 / 3;
            float x = (float) Math.cos(a) * 8f, z = (float) Math.sin(a) * 8f, y = -5f + (float) Math.sin(t * 0.1 + i) * 1.2f;
            M g = moved(e, x, y, z, (float) (t * 0.12), 0, 0.6f);
            box(g, vc, BRIGHT, c, -0.9f, -1.3f, -0.9f, 0.9f, 1.3f, 0.9f);
            box(g, vc, BRIGHT, shade(c, 1.25f), -0.5f, -0.5f, -0.95f, 0.5f, 0.5f, 0.95f);
        }
    }

    /** A knitted scarf round the neck with one end hanging down the back. */
    public static void scarf(M e, Out vc, int light, float t, int c) {
        int dark = shade(c, 0.82f);
        box(e, vc, light, c, -4.6f, -0.6f, -2.6f, 4.6f, 1.6f, 2.6f);                              // wrap
        for (int i = 0; i < 4; i++) box(e, vc, light, i % 2 == 0 ? dark : c, -4.65f, -0.2f + i * 0.45f, -2.65f, 4.65f, 0.05f + i * 0.45f, 2.65f);
        float sway = (float) Math.sin(t * 0.12) * 0.15f;
        M tail = moved(e, 1.8f, 1.2f, 2.6f, 0, 0.25f + sway, 0);
        box(tail, vc, light, c, -1.3f, 0f, 0f, 1.3f, 7f, 0.5f);
        for (int i = 0; i < 3; i++) box(tail, vc, light, dark, -1.35f, 1.5f + i * 2f, -0.02f, 1.35f, 2.1f + i * 2f, 0.52f);
        box(tail, vc, light, dark, -1.3f, 7f, 0f, 1.3f, 7.8f, 0.5f);                             // fringe
    }

    /** Sunglasses on the face. */
    public static void glasses(M e, Out vc, int light, int c) {
        box(e, vc, light, c, -3.9f, -5.2f, -4.45f, 3.9f, -4.7f, -4.2f);                // bridge bar
        box(e, vc, light, 0xFF101014, -3.6f, -5f, -4.5f, -0.6f, -3.2f, -4.25f);        // lenses
        box(e, vc, light, 0xFF101014, 0.6f, -5f, -4.5f, 3.6f, -3.2f, -4.25f);
        box(e, vc, light, c, -4.3f, -5.1f, -4.3f, -4.05f, -4.6f, 1f);                   // arms
        box(e, vc, light, c, 4.05f, -5.1f, -4.3f, 4.3f, -4.6f, 1f);
    }

    /** Headphones: a band over the head and a cup on each ear. */
    public static void headphones(M e, Out vc, int light, int c) {
        box(e, vc, light, c, -4.7f, -9f, -0.8f, 4.7f, -8.2f, 0.8f);
        for (int s = -1; s <= 1; s += 2) {
            box(e, vc, light, c, s < 0 ? -4.9f : 4.1f, -8.2f, -0.8f, s < 0 ? -4.1f : 4.9f, -5f, 0.8f);
            box(e, vc, light, shade(c, 0.8f), s < 0 ? -5.6f : 4.1f, -5.6f, -2f, s < 0 ? -4.1f : 5.6f, -1.8f, 2f);
        }
    }

    /** A thin ring floating over the head, bobbing gently. */
    public static void halo(M e, Out vc, int light, float t, boolean red, float thick, int alpha) {
        halo(e, vc, light, t, red ? 0xFFFF3B3B : 0xFFFFE08A, thick, alpha);
    }

    /** The halo in any colour (custom colours from the colour wheel). */
    public static void halo(M e, Out vc, int light, float t, int rgb, float thick, int alpha) {
        int c = tint(rgb, alpha);
        float y = -13f - (float) Math.sin(t * 0.08) * 0.5f;
        int n = 20;
        float r = 5.4f;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
            float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            box(e, vc, light, c, Math.min(x0, x1) - thick, y - thick, Math.min(z0, z1) - thick,
                    Math.max(x0, x1) + thick, y + thick, Math.max(z0, z1) + thick);
        }
    }

    /**
     * Wings from the shoulder blades, swept back and slowly flapping. Styles: feathered (rows of
     * separate feathers), dragon (bones with a skin between) and butterfly (two big rounded panes).
     * With {@code shell} it draws the soft glow around them instead.
     */
    public static void wings(M e, Out vc, int light, float t, int c, String style, boolean shell, float pulse) {
        float flap = (float) Math.sin(t * 0.1) * 0.16f;
        float g = shell ? 0.6f : 0f;
        int a = shell ? Math.round(60 * pulse) : 0xFF;
        for (int s = -1; s <= 1; s += 2) {
            // out to the side, then swept BACK (+z) — the model is mirrored, so the sweep is -s
            M w = moved(e, s * 1.5f, 2.5f, 2.4f, -s * (0.55f + flap), 0, -s * 0.32f);
            if (style.equals("demon")) {
                // three long curved blades per side, sweeping out and up like scythes
                for (int b = 0; b < 3; b++) {
                    M blade = moved(w, 0, -1f + b * 3.2f, 0.2f * b, 0, 0, s * (-0.15f + b * 0.55f) * -1f);
                    float len = 15f - b * 2.5f;
                    int segs = 8;
                    M cur = blade;
                    for (int i = 0; i < segs; i++) {
                        float th = (1.3f - i * 0.13f) - (shell ? -g : 0);
                        int col = tint(shade(c, 0.75f + 0.04f * i), a);
                        wbox(cur, vc, light, col, s, -g, -th / 2 - g, -0.4f - g, len / segs + 0.3f + g, th / 2 + g, 0.4f + g);
                        cur = moved(cur, s * len / segs, 0, 0, 0, 0, -s * 0.13f);   // curl upwards along the blade
                    }
                }
            } else if (style.equals("energy")) {
                // a fan of thin rays of light (always faintly glowing, brighter with Glow on)
                int rays = 9;
                for (int i = 0; i < rays; i++) {
                    float ang = -0.9f + i * (1.6f / (rays - 1));
                    float len = 12f + (float) Math.sin(i * 1.7) * 3f + (float) Math.sin(t * 0.15 + i) * 1.2f;
                    M ray = moved(w, 0, 3f, 0.1f * i, 0, 0, -s * ang);
                    int col = tint(shade(c, 1.1f), shell ? a : 0xFF);
                    wbox(ray, vc, light, col, s, 0 - g, -0.35f - g, -0.2f - g, len + g, 0.35f + g, 0.2f + g);
                    wbox(ray, vc, light, tint(0xFFFFFFFF, shell ? a : 0xFF), s, len - 1.2f - g, -0.55f - g, -0.25f - g, len + 0.4f + g, 0.55f + g, 0.25f + g);
                }
            } else if (style.equals("dragon")) {
                    int bone = tint(shade(c, 0.7f), a), skin = tint(c, a);
                    wbox(w, vc, light, bone, s, -g, -1.2f - g, -g, 20f + g, 0.4f + g, 0.9f + g);          // arm
                    float[] fx = {6f, 11f, 16f, 20f};
                    for (float x : fx) wbox(w, vc, light, bone, s, x - 0.5f - g, 0 - g, -g, x + 0.5f + g, 15f - x * 0.35f + g, 0.8f + g);
                    for (int i = 0; i < 4; i++) {                                                           // membrane between fingers
                        float x0 = i == 0 ? 0.5f : fx[i - 1] + 0.5f, x1 = fx[i] - 0.5f, h = 13f - x1 * 0.3f;
                        wbox(w, vc, light, tint(shade(c, 0.95f), shell ? a : 0xE6), s, x0 - g, 0.4f - g, 0.2f - g, x1 + g, h + g, 0.6f + g);
                    }
            } else if (style.equals("butterfly")) {
                    int col = tint(c, a), dot = tint(0xFFFFFFFF, shell ? a : 0xFF);
                    for (int i = 0; i < 7; i++) {                                                           // upper wing, rounded
                        float x0 = i * 2.2f, half = 7f - Math.abs(i - 3.5f) * 1.2f;
                        wbox(w, vc, light, col, s, x0 - g, -2f - half - g, -g, x0 + 2.2f + g, -2f + half * 0.6f + g, 0.5f + g);
                    }
                    for (int i = 0; i < 5; i++) {                                                           // lower wing
                        float x0 = i * 2f, half = 5f - Math.abs(i - 2f) * 1.1f;
                        wbox(w, vc, light, tint(shade(c, 0.85f), a), s, x0 - g, 4f - g, 0.05f - g, x0 + 2f + g, 4f + half * 1.6f + g, 0.55f + g);
                    }
                    if (!shell) {
                        wbox(w, vc, light, dot, s, 9f, -4f, -0.1f, 11f, -2f, 0.6f);
                        wbox(w, vc, light, dot, s, 5f, 6f, -0.05f, 6.5f, 7.5f, 0.6f);
                    }
            } else {                                                                                        // feathered
                feathered(w, vc, light, c, s, g, a, t);
            }
        }
    }

    /**
     * Real-looking feathered wing: an arched bone (three segments rising outwards), long flight
     * feathers fanning from it (longest towards the tip), and a row of short rounded covert
     * feathers over their roots. Every feather is tapered (wide at the root, pointed at the tip)
     * with a darker shaft down the middle and its own shade, so the wing reads as feathers, not
     * planks. {@code g} > 0 draws the slightly larger glow shell instead.
     */
    private static void feathered(M w, Out vc, int light, int c, int s, float g, int a, float t) {
        // the bone: 3 segments that curve up and out
        float bx = 0, by = 0;
        float[][] bone = {{6f, -1.5f}, {6.5f, -2.6f}, {6f, -2.2f}};
        float[] boneX = new float[4], boneY = new float[4];
        boneX[0] = 0;
        boneY[0] = 0;
        for (int i = 0; i < bone.length; i++) {
            float nx = bx + bone[i][0], ny = by + bone[i][1];
            float ang = (float) Math.atan2(ny - by, nx - bx), len = (float) Math.hypot(nx - bx, ny - by);
            M seg = moved(w, s * bx, by, 0, 0, 0, s * ang);
            box(seg, vc, light, tint(shade(c, 1.06f), a), -g, -0.9f - g, -0.2f - g, len + g, 0.9f + g, 1.0f + g);
            bx = nx;
            by = ny;
            boneX[i + 1] = bx;
            boneY[i + 1] = by;
        }
        float flutter = (float) Math.sin(t * 0.22) * 0.03f;
        // flight feathers (primaries + secondaries), fanning out, longest at the tip
        int n = 16;
        for (int i = 0; i < n; i++) {
            float f = i / (float) (n - 1);
            float px = f * bx, py = f * by;                         // along the bone
            float len = 9f + 9f * (float) Math.pow(f, 1.2);         // 9 → 18 px, longest at the tip
            float ang = (float) Math.toRadians(-6 + 40 * f) + flutter * (i % 2 == 0 ? 1 : -1);  // hang down, fanning out a little
            int col = tint(shade(c, (i % 2 == 0 ? 0.98f : 0.92f) - 0.05f * f), a);
            feather(w, vc, light, s, px, py + 0.4f, 0.45f + 0.035f * i, ang, len, 3.4f - 1.0f * f, col, shade(c, 0.72f), a, g);
        }
        // coverts: short rounded feathers over the roots, a step forward
        int m = 10;
        for (int i = 0; i < m; i++) {
            float f = (i + 0.5f) / m;
            float px = f * bx, py = f * by;
            float len = 4.5f + 2.5f * f;
            float ang = (float) Math.toRadians(-4 + 30 * f);
            int col = tint(shade(c, 1.05f - 0.03f * (i % 2)), a);
            feather(w, vc, light, s, px, py + 0.3f, -0.15f, ang, len, 2.4f, col, shade(c, 0.85f), a, g);
        }
    }

    /** One tapered feather hanging from (px, py), turned outwards by {@code ang} (radians). */
    private static void feather(M w, Out vc, int light, int s, float px, float py, float z, float ang, float len, float width,
                                int col, int shaftCol, int a, float g) {
        M f = moved(w, s * px, py, z, 0, 0, -s * ang);              // y runs down the feather
        float[] widths = {1f, 0.92f, 0.78f, 0.55f, 0.28f};          // tapers to a point
        float seg = len / widths.length;
        for (int k = 0; k < widths.length; k++) {
            float hw = width * widths[k] / 2;
            float y0 = k * seg - 0.05f, y1 = (k + 1) * seg + 0.05f;
            int cc = k == widths.length - 1 ? shade(col, 1.08f) : col;    // tips catch a little light
            box(f, vc, light, cc, -hw - g, y0 - g, -0.18f - g, hw + g, y1 + g, 0.18f + g);
        }
        if (g == 0) box(f, vc, light, tint(shaftCol, a), -0.16f, 0, -0.24f, 0.16f, len * 0.92f, 0.24f);   // the shaft
    }

    /** A box on one side of the body (mirrored for the other side). */
    static void wbox(M e, Out vc, int light, int c, int side,
                             float x0, float y0, float z0, float x1, float y1, float z1) {
        if (side < 0) box(e, vc, light, c, -x1, y0, z0, -x0, y1, z1);
        else box(e, vc, light, c, x0, y0, z0, x1, y1, z1);
    }

    /** A cat tail (any colour) or a fluffy fox tail with a white tip, swaying. */
    public static void tail(M e, Out vc, int light, float t, String kind) {
        boolean fox = "fox".equals(kind);
        int c = fox ? 0xFFE0762C : colour(kind);
        float sway = (float) Math.sin(t * 0.15);
        float x = 0, y = 11f, z = 2.2f;
        int n = fox ? 8 : 11;
        for (int i = 0; i < n; i++) {
            float k = i / (float) (n - 1);
            x += sway * 0.45f * k;
            y -= fox ? 0.1f + k * 0.9f : 0.3f + k * 1.2f;
            z += fox ? 1.4f - k * 0.6f : 1.3f - k * 1f;
            float r = fox ? 1.4f + (float) Math.sin(k * Math.PI) * 1.4f : 1f - k * 0.3f;
            int col = fox && k > 0.8f ? 0xFFF4F4EF : c;
            box(e, vc, light, col, x - r, y - r, z - r, x + r, y + r, z + r);
        }
    }

    /** A small backpack with a flap and straps. */
    public static void backpack(M e, Out vc, int light, int c) {
        box(e, vc, light, c, -3.4f, 1.2f, 2f, 3.4f, 9.6f, 5f);
        box(e, vc, light, shade(c, 0.85f), -3.6f, 0.8f, 1.9f, 3.6f, 3.6f, 5.3f);            // flap
        box(e, vc, light, shade(c, 0.75f), -2.6f, 6f, 5f, 2.6f, 9f, 5.8f);                   // front pocket
        box(e, vc, light, 0xFFC9A227, -0.5f, 3.3f, 5.3f, 0.5f, 4.2f, 5.5f);                  // buckle
        for (int s = -1; s <= 1; s += 2) box(e, vc, light, shade(c, 0.6f), s * 2.6f - 0.6f, -0.2f, -2.2f, s * 2.6f + 0.6f, 6f, 2.1f);   // straps
    }

    /** A katana worn across the back: blade, guard and wrapped handle. */
    public static void katana(M e, Out vc, int light) {
        M kq = moved(e, 0, 6f, 2.9f, 0, 0, (float) Math.toRadians(-40));
        box(kq, vc, light, 0xFFD8DDE4, -0.45f, -17f, -0.25f, 0.45f, 4f, 0.25f);             // blade
        box(kq, vc, light, 0xFFB5BCC6, -0.55f, -17f, -0.3f, -0.3f, 4f, 0.3f);               // edge line
        box(kq, vc, light, 0xFFC9A227, -1.6f, 4f, -0.9f, 1.6f, 4.8f, 0.9f);                 // guard
        box(kq, vc, light, 0xFF1A1A1E, -0.6f, 4.8f, -0.55f, 0.6f, 11f, 0.55f);              // handle
        for (float y = 5.4f; y < 10.8f; y += 1.3f) box(kq, vc, light, 0xFF7A1F2B, -0.65f, y, -0.6f, 0.65f, y + 0.45f, 0.6f);
    }

    /** The katana's blade glowing softly. */
    public static void katanaGlow(M e, Out vc, float pulse) {
        M kq = moved(e, 0, 6f, 2.9f, 0, 0, (float) Math.toRadians(-40));
        box(kq, vc, BRIGHT, tint(0xFF9FD8FF, Math.round(70 * pulse)), -1f, -17.5f, -0.7f, 1f, 4f, 0.7f);
    }

    /** Oversized bare feet at the bottom of the legs. */
    public static void foot(M e, Out vc, int light) {
        int skin = 0xFFF0C8A0;
        box(e, vc, light, skin, -3.4f, 9.4f, -6f, 3.4f, 12.2f, 2.6f);
        for (int i = 0; i < 4; i++) box(e, vc, light, shade(skin, 0.95f), -3.1f + i * 1.6f, 10.4f, -7.4f, -2f + i * 1.6f, 12.2f, -6f);
    }

    /** A boxing glove over each hand. */
    public static void glove(M e, Out vc, int light, int c, int side) {
        float cx = side < 0 ? -1f : 1f;
        box(e, vc, light, c, cx - 3.2f, 6.6f, -3.3f, cx + 3.2f, 12.6f, 3.3f);
        box(e, vc, light, 0xFFF4F4F4, cx - 2.8f, 6f, -2.8f, cx + 2.8f, 6.7f, 2.8f);          // cuff
        box(e, vc, light, shade(c, 0.9f), cx - 1.3f, 7.6f, -4.2f, cx + 1.3f, 10.6f, -3.3f); // thumb
    }
}

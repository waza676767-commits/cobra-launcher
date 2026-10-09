package dev.life.client.core.cosmetics;

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

        /**
         * A face whose four corners each have their own colour (blended across it: gradients).
         * Outputs that can't blend draw it in the average colour.
         */
        default void quad4(float[] p, float nx, float ny, float nz, int[] argb, int light) {
            quad(p, nx, ny, nz, average(argb), light);
        }
    }

    /** Average of colours (alpha too). */
    public static int average(int[] c) {
        int a = 0, r = 0, g = 0, b = 0;
        for (int x : c) {
            a += x >>> 24;
            r += x >> 16 & 255;
            g += x >> 8 & 255;
            b += x & 255;
        }
        int n = c.length;
        return (a / n) << 24 | (r / n) << 16 | (g / n) << 8 | (b / n);
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

        /** Mirror left / right (for the other wing, ear...). */
        public M mirrorX() {
            return mul3(-1, 0, 0, 0, 1, 0, 0, 0, 1);
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

    // =====================================================================================
    // Shared looks
    // =====================================================================================

    private static final int PINK_IN = 0xFFF29BB3, PINK_IN_DEEP = 0xFFD9738F;
    private static final int GOLD = 0xFFE8B63A, GOLD_DARK = 0xFFA87418, GOLD_LIGHT = 0xFFFFE597;

    /** A colour a little lighter towards the tip (black stays a charcoal, never grey). */
    static int tipOf(int c) {
        int lum = ((c >> 16 & 255) * 299 + (c >> 8 & 255) * 587 + (c & 255) * 114) / 1000;
        return Shapes.mix(c, 0xFFFFFFFF, lum < 60 ? 0.22f : 0.28f);
    }

    static int rootOf(int c) {
        return shade(c, 0.62f);
    }

    // =====================================================================================
    // Geometry cache: most cosmetics don't move, so their (many) faces are built once per look
    // and replayed every frame, which keeps them cheap even with lots of detail.
    // =====================================================================================

    interface Build {
        void run(M e, Out o);
    }

    private static final class Rec {
        float[] pos = new float[1200], nrm = new float[300];
        int[] col = new int[400];
        int n;

        void add(float[] p, float nx, float ny, float nz, int c0, int c1, int c2, int c3) {
            if (n * 12 + 12 > pos.length) {
                pos = java.util.Arrays.copyOf(pos, pos.length * 2);
                nrm = java.util.Arrays.copyOf(nrm, nrm.length * 2);
                col = java.util.Arrays.copyOf(col, col.length * 2);
            }
            System.arraycopy(p, 0, pos, n * 12, 12);
            nrm[n * 3] = nx;
            nrm[n * 3 + 1] = ny;
            nrm[n * 3 + 2] = nz;
            col[n * 4] = c0;
            col[n * 4 + 1] = c1;
            col[n * 4 + 2] = c2;
            col[n * 4 + 3] = c3;
            n++;
        }
    }

    private static final java.util.Map<String, Rec> CACHE = new java.util.LinkedHashMap<String, Rec>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Rec> eldest) {
            return size() > 160;
        }
    };

    /** Draws what {@code b} builds, built only the first time for this key. */
    static void cached(String key, M e, Out vc, int light, Build b) {
        Rec r;
        synchronized (CACHE) {
            r = CACHE.get(key);
        }
        if (r == null) {
            final Rec rec = new Rec();
            b.run(M.identity(), new Out() {
                @Override
                public void quad(float[] p, float nx, float ny, float nz, int argb, int l) {
                    rec.add(p, nx, ny, nz, argb, argb, argb, argb);
                }

                @Override
                public void quad4(float[] p, float nx, float ny, float nz, int[] argb, int l) {
                    rec.add(p, nx, ny, nz, argb[0], argb[1], argb[2], argb[3]);
                }
            });
            r = rec;
            synchronized (CACHE) {
                CACHE.put(key, r);
            }
        }
        float[] p = new float[12];
        int[] c = new int[4];
        for (int q = 0; q < r.n; q++) {
            int o = q * 12;
            for (int k = 0; k < 4; k++) e.apply(r.pos[o + k * 3], r.pos[o + k * 3 + 1], r.pos[o + k * 3 + 2], p, k * 3);
            float[] n = e.dir(r.nrm[q * 3], r.nrm[q * 3 + 1], r.nrm[q * 3 + 2]);
            float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            if (len > 1e-6f) {
                n[0] /= len;
                n[1] /= len;
                n[2] /= len;
            }
            c[0] = r.col[q * 4];
            c[1] = r.col[q * 4 + 1];
            c[2] = r.col[q * 4 + 2];
            c[3] = r.col[q * 4 + 3];
            if (c[0] == c[1] && c[1] == c[2] && c[2] == c[3]) vc.quad(p, n[0], n[1], n[2], c[0], light);
            else vc.quad4(p, n[0], n[1], n[2], c, light);
        }
    }

    // =====================================================================================
    // Head
    // =====================================================================================

    /**
     * Cat ears: real triangular ears that lean out a little, fur darker at the root and lighter
     * at the tip, a soft pink inner ear set into the front and a little tuft on top.
     */
    public static void catEars(M e, Out vc, int light, final int c) {
        cached("ears" + c, e, vc, light, (m, o) -> catEarsRaw(m, o, 0, c));
    }

    private static void catEarsRaw(M e, Out vc, int light, int c) {
        int root = rootOf(c), tip = tipOf(c);
        for (int s = -1; s <= 1; s += 2) {
            M q = moved(e, s * 2.6f, -7.9f, 0.3f, s * -0.18f, -0.12f, s * 0.22f);
            // outer ear: a wide base narrowing to a rounded point
            Shapes.taper(q, vc, light, 0, 0, 0.2f, -2.6f, 2.3f, 1.05f, 1.5f, 0.75f, root, c);
            Shapes.taper(q, vc, light, 0, 0.05f, -2.6f, -4.6f, 1.5f, 0.75f, 0.55f, 0.45f, c, tip);
            Shapes.taper(q, vc, light, 0, 0.1f, -4.6f, -5.5f, 0.55f, 0.45f, 0.08f, 0.1f, tip, tip);
            // inner ear: pink, a step in front, deeper colour at the bottom
            Shapes.taper(q, vc, light, 0, -0.85f, 0f, -2.4f, 1.65f, 0.35f, 1.05f, 0.3f, PINK_IN_DEEP, PINK_IN);
            Shapes.taper(q, vc, light, 0, -0.75f, -2.4f, -4.3f, 1.05f, 0.3f, 0.12f, 0.15f, PINK_IN, Shapes.mix(PINK_IN, 0xFFFFFFFF, 0.3f));
        }
    }

    /** Long bunny ears: rounded, curving up and flopping at the top, pink inside; they bob. */
    public static void bunnyEars(M e, Out vc, int light, int c, float t) {
        int root = shade(c, 0.8f), tip = tipOf(c);
        for (int s = -1; s <= 1; s += 2) {
            float bob = (float) Math.sin(t * 0.09 + s) * 0.08f;
            float flop = s > 0 ? 3.4f : 1.4f;                                  // one ear flops more
            M q = moved(e, s * 2.0f, -7.6f, 0.6f, 0, -0.15f + bob, s * 0.14f);
            float[][] path = {Shapes.v(0, 0.4f, 0), Shapes.v(0, -3f, 0.2f), Shapes.v(s * 0.3f, -6.2f, 0.5f),
                    Shapes.v(s * 0.9f, -8.6f, 1.0f), Shapes.v(s * (1.4f + flop * 0.35f), -10.2f, 1.6f + flop * 0.2f),
                    Shapes.v(s * (1.8f + flop * 0.8f), -10.9f + flop * 0.35f, 2.2f + flop * 0.35f)};
            float[] wide = {1.0f, 1.45f, 1.75f, 1.75f, 1.45f, 0.7f}, thick = {0.75f, 0.6f, 0.55f, 0.55f, 0.5f, 0.4f};
            int[] cols = {root, c, c, Shapes.mix(c, tip, 0.5f), tip, tip};
            Shapes.tube(q, vc, light, path, wide, thick, cols, 8, Shapes.v(1, 0, 0), 0);
            // the pink inside, a little in front (only up to where the ear bends)
            float[][] inner = new float[5][];
            for (int i = 0; i < 5; i++) inner[i] = Shapes.v(path[i][0], path[i][1], path[i][2] - 0.42f);
            Shapes.tube(q, vc, light, inner, new float[]{0.45f, 0.95f, 1.15f, 1.05f, 0.5f}, new float[]{0.18f, 0.2f, 0.2f, 0.2f, 0.15f},
                    new int[]{PINK_IN_DEEP, PINK_IN, PINK_IN, PINK_IN, Shapes.mix(PINK_IN, 0xFFFFFFFF, 0.3f)}, 6, Shapes.v(1, 0, 0), 0);
        }
    }

    /**
     * Devil horns: thick at the root, curling out, back and up to a sharp point, with ridged
     * rings, dark at the base and bright at the tip.
     */
    public static void horns(M e, Out vc, int light, final int c) {
        cached("horns" + c, e, vc, light, (m, o) -> hornsRaw(m, o, 0, c));
    }

    private static void hornsRaw(M e, Out vc, int light, int c) {
        int root = rootOf(c), tip = Shapes.mix(c, 0xFFFFFFFF, 0.35f);
        int n = 12;
        for (int s = -1; s <= 1; s += 2) {
            M q = moved(e, s * 2.6f, -7.7f, -1.2f, 0, 0, 0);
            float[][] path = Shapes.curve(Shapes.v(0, 0.3f, 0), Shapes.v(s * 3.2f, -2.8f, 0.6f), Shapes.v(s * 2.6f, -6.6f, 2.8f), n);
            float[] r = new float[n];
            int[] cols = new int[n];
            for (int i = 0; i < n; i++) {
                float k = i / (float) (n - 1);
                r[i] = 1.45f * (1 - k) * (1 - 0.15f * k) + (i == n - 1 ? 0 : 0.05f);
                int base = Shapes.mix(root, c, Math.min(1, k * 1.6f));
                if (k > 0.55f) base = Shapes.mix(base, tip, (k - 0.55f) / 0.45f);
                cols[i] = i % 2 == 1 && k < 0.7f ? shade(base, 0.86f) : base;          // the ridges
            }
            r[n - 1] = 0;
            Shapes.tube(q, vc, light, path, r, cols, 8);
            // a dark ring where the horn meets the head
            Shapes.tube(q, vc, light, new float[][]{Shapes.v(0, 0.55f, 0), Shapes.v(0, -0.05f, 0)}, new float[]{1.75f, 1.7f},
                    new int[]{shade(root, 0.7f), root}, 8);
        }
    }

    /** Antlers: a velvet main beam sweeping up and back with three tines, bone-pale at the tips. */
    public static void antlers(M e, Out vc, int light, final int c) {
        cached("antlers" + c, e, vc, light, (m, o) -> antlersRaw(m, o, 0, c));
    }

    private static void antlersRaw(M e, Out vc, int light, int c) {
        int root = rootOf(c), tip = Shapes.mix(c, 0xFFF1E6CC, 0.7f);
        for (int s = -1; s <= 1; s += 2) {
            M a = moved(e, s * 2.4f, -7.8f, 0.6f, 0, 0, 0);
            Shapes.tube(a, vc, light, new float[][]{Shapes.v(0, 0.5f, 0), Shapes.v(0, -0.6f, 0)}, new float[]{1.0f, 0.95f},
                    new int[]{shade(root, 0.8f), root}, 8);                             // the burr at the base
            float[][] beam = Shapes.curve(Shapes.v(0, 0, 0), Shapes.v(s * 3.5f, -4.5f, 0.8f), Shapes.v(s * 5.0f, -10.5f, 2.6f), 9);
            Shapes.tube(a, vc, light, beam, Shapes.ramp(0.75f, 0.12f, 9, 1.3f), Shapes.ramp(root, tip, 9), 6);
            float[][] tines = {{0.3f, -0.4f, -3.2f, -0.6f}, {0.55f, 1.4f, -3.0f, -0.4f}, {0.78f, -0.6f, -2.6f, 0.3f}};
            for (float[] tn : tines) {
                int i = Math.round(tn[0] * 8);
                float[] b = beam[i];
                float[][] p = Shapes.curve(b, Shapes.v(b[0] + s * tn[1] * 0.4f, b[1] + tn[2] * 0.6f, b[2] + tn[3]),
                        Shapes.v(b[0] + s * tn[1], b[1] + tn[2], b[2] + tn[3] * 1.4f), 5);
                Shapes.tube(a, vc, light, p, Shapes.ramp(0.5f - tn[0] * 0.2f, 0.08f, 5, 1f), Shapes.ramp(Shapes.mix(root, tip, tn[0]), tip, 5), 6);
            }
        }
    }

    /** Three cut gems circling your head, each spinning, with a tiny spark trailing it. */
    public static void orbit(M e, Out vc, int light, float t, int c) {
        for (int i = 0; i < 3; i++) {
            double a = t * 0.08 + i * Math.PI * 2 / 3;
            float x = (float) Math.cos(a) * 8f, z = (float) Math.sin(a) * 8f, y = -5f + (float) Math.sin(t * 0.1 + i) * 1.2f;
            M g = moved(e, x, y, z, (float) (t * 0.12 + i), 0, 0.25f);
            Shapes.gem(g, vc, BRIGHT, 1.25f, 1.1f, 6, c);
            double a2 = a - 0.32;                                               // a spark following it
            M sp = moved(e, (float) Math.cos(a2) * 8f, y + 0.3f, (float) Math.sin(a2) * 8f, (float) (t * 0.2), 0.6f, 0.6f);
            Shapes.gem(sp, vc, BRIGHT, 0.35f, 0.35f, 4, Shapes.mix(c, 0xFFFFFFFF, 0.5f));
        }
    }

    /** A smooth glowing ring over the head, bobbing gently (bright inner edge, deeper outer). */
    public static void halo(M e, Out vc, int light, float t, boolean red, float thick, int alpha) {
        halo(e, vc, light, t, red ? 0xFFFF3B3B : 0xFFFFE08A, thick, alpha);
    }

    /** The halo in any colour (custom colours from the colour wheel). {@code thick} > 1: the glow around it. */
    public static void halo(M e, Out vc, int light, float t, int rgb, float thick, int alpha) {
        final float y = -13f - (float) Math.sin(t * 0.08) * 0.5f;
        final int edge = tint(shade(rgb, 0.78f), alpha), inner = tint(Shapes.mix(rgb, 0xFFFFFFFF, 0.55f), alpha), glow = tint(rgb, alpha);
        final float r = thick > 0.8f ? thick * 1.15f : Math.max(0.4f, thick * 1.25f);
        M at = e.copy().translate(0, y, 0);
        if (thick > 0.8f) cached("haloglow" + glow + "|" + r, at, vc, light, (m, o) -> Shapes.ring(m, o, 0, 0, 0, 0, 5.4f, r, 32, 6, glow, glow));
        else cached("halo" + edge + "|" + inner + "|" + r, at, vc, light, (m, o) -> Shapes.ring(m, o, 0, 0, 0, 0, 5.4f, r, 40, 8, edge, inner));
    }

    /** Hats: a jewelled crown, a top hat, a witch hat, a Santa hat or a Viking helmet. */
    public static void hat(M e, Out vc, int light, String kind) {
        final String k = kind == null ? "" : kind;
        cached("hat" + k, e, vc, light, (m, o) -> hatRaw(m, o, 0, k));
    }

    private static void hatRaw(M e, Out vc, int light, String kd) {
        if (kd.equals("santa")) santaHat(e, vc, light);
        else if (kd.equals("viking")) vikingHelmet(e, vc, light);
        else if (kd.equals("tophat")) topHat(e, vc, light);
        else if (kd.equals("witch")) witchHat(e, vc, light);
        else crown(e, vc, light);
    }

    /** A band of 12 sides running round the head at radius r, from y0 (bottom) to y1 (top). */
    private static void band(M e, Out vc, int light, float r, float thick, float y0, float y1, int cBottom, int cTop, int n) {
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float ro = r + thick / 2, ri = r - thick / 2;
            float[][] bot = {Shapes.v(c0 * ri, y0, s0 * ri), Shapes.v(c0 * ro, y0, s0 * ro), Shapes.v(c1 * ro, y0, s1 * ro), Shapes.v(c1 * ri, y0, s1 * ri)};
            float[][] top = {Shapes.v(c0 * ri, y1, s0 * ri), Shapes.v(c0 * ro, y1, s0 * ro), Shapes.v(c1 * ro, y1, s1 * ro), Shapes.v(c1 * ri, y1, s1 * ri)};
            Shapes.hull(e, vc, light, bot, top, cBottom, cTop);
        }
    }

    private static void crown(M e, Out vc, int light) {
        float r = 4.95f;
        // red velvet cap inside, then the gold band with a beaded rim
        Shapes.dome(e, vc, light, 0, -8.4f, 0, 4.4f, 2.6f, 4.4f, 4, 12, 0xFFC0283A, 0xFF6E0F1E, true);
        band(e, vc, light, r, 0.7f, -7.9f, -10.3f, GOLD_DARK, GOLD, 16);
        Shapes.ring(e, vc, light, 0, -7.95f, 0, r + 0.05f, 0.42f, 24, 5, GOLD_DARK, GOLD_LIGHT);
        Shapes.ring(e, vc, light, 0, -10.3f, 0, r + 0.05f, 0.36f, 24, 5, GOLD, GOLD_LIGHT);
        // eight points, each topped with a pearl, a gem on the band below every other one
        int[] gems = {0xFFE0263C, 0xFF2F7BEA, 0xFF27C26C, 0xFFB24DF0};
        for (int i = 0; i < 8; i++) {
            float a = (float) (Math.PI * 2 * i / 8 + Math.PI / 2);   // first one at the front (-z)
            a = (float) (-Math.PI / 2 + Math.PI * 2 * i / 8);
            float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
            M p = moved(e, x, -10.1f, z, (float) (-a + Math.PI / 2), 0, 0);
            float hgt = i % 2 == 0 ? 3.6f : 2.5f;
            Shapes.taper(p, vc, light, 0, 0, 0, -hgt, 1.15f, 0.32f, 0.12f, 0.12f, GOLD, GOLD_LIGHT);
            M pearl = moved(p, 0, -hgt - 0.35f, 0, 0, 0, 0);
            Shapes.dome(pearl, vc, light, 0, 0, 0, 0.6f, 0.6f, 0.6f, 3, 7, 0xFFFFFFFF, 0xFFD9CFC0, false);
            if (i % 2 == 0) {
                M g = moved(e, (float) Math.cos(a) * (r + 0.45f), -9.1f, (float) Math.sin(a) * (r + 0.45f), (float) (-a + Math.PI / 2), (float) (Math.PI / 2), 0);
                Shapes.gem(g, vc, light, 0.75f, 0.35f, 6, gems[(i / 2) % gems.length]);
            }
        }
    }

    private static void topHat(M e, Out vc, int light) {
        int felt = 0xFF1B1B21, sheen = 0xFF3A3A46, band = 0xFF9E1B2B;
        // brim: an oval disc with a rolled edge
        Shapes.tube(e, vc, light, new float[][]{Shapes.v(0, -7.8f, 0), Shapes.v(0, -8.25f, 0)}, new float[]{6.4f, 6.4f}, new float[]{5.7f, 5.7f},
                new int[]{0xFF101014, felt}, 24, Shapes.v(1, 0, 0), 0);
        float[][] roll = new float[33][];
        for (int i = 0; i <= 32; i++) {
            double a = Math.PI * 2 * i / 32;
            float side = (float) Math.abs(Math.cos(a));
            roll[i] = Shapes.v((float) Math.cos(a) * 6.4f, -8.05f - side * side * 0.7f, (float) Math.sin(a) * 5.7f);   // curls up at the sides
        }
        Shapes.tube(e, vc, light, roll, Shapes.ramp(0.32f, 0.32f, 33, 1), Shapes.ramp(sheen, sheen, 33), 6);
        // the crown, flaring slightly towards the top, with a shine down one side
        float[][] path = {Shapes.v(0, -8.2f, 0), Shapes.v(0, -11.5f, 0), Shapes.v(0, -15.3f, 0)};
        Shapes.tube(e, vc, light, path, new float[]{4.15f, 4.2f, 4.45f}, new float[]{4.15f, 4.2f, 4.45f}, new int[]{felt, sheen, felt}, 20, Shapes.v(1, 0, 0), 0);
        // satin band with a gold buckle
        Shapes.tube(e, vc, light, new float[][]{Shapes.v(0, -8.3f, 0), Shapes.v(0, -9.9f, 0)}, new float[]{4.45f, 4.47f},
                new int[]{shade(band, 0.8f), band}, 20);
        Shapes.gbox(e, vc, light, GOLD_LIGHT, GOLD_DARK, -1.1f, -10.2f, -4.85f, 1.1f, -8.1f, -4.4f);
        Shapes.gbox(e, vc, light, 0xFF1B1B21, 0xFF1B1B21, -0.55f, -9.65f, -4.95f, 0.55f, -8.65f, -4.75f);
    }

    private static void witchHat(M e, Out vc, int light) {
        int c = 0xFF4B2A7A, dark = 0xFF2C1650, light2 = 0xFF6B44A6;
        Shapes.tube(e, vc, light, new float[][]{Shapes.v(0, -7.8f, 0), Shapes.v(0, -8.4f, 0)}, new float[]{7.6f, 7.4f},
                new int[]{dark, c}, 28);                                              // wide brim
        // a tall crooked cone: leans back, then the tip bends over
        int n = 11;
        float[][] path = new float[n][];
        float[] r = new float[n];
        int[] cols = new int[n];
        for (int i = 0; i < n; i++) {
            float k = i / (float) (n - 1);
            float bend = k > 0.65f ? (k - 0.65f) / 0.35f : 0;
            path[i] = Shapes.v(bend * bend * 1.6f, -8.2f - k * 11f + bend * bend * 2.2f, 1.2f * k + bend * 2.8f);
            r[i] = 4.6f * (1 - k) * (1 - 0.25f * k) + (i == n - 1 ? 0 : 0.1f);
            cols[i] = Shapes.mix(c, light2, k * 0.8f);
        }
        r[n - 1] = 0;
        Shapes.tube(e, vc, light, path, r, cols, 16);
        // band and square buckle
        Shapes.tube(e, vc, light, new float[][]{Shapes.v(0, -8.4f, 0.05f), Shapes.v(0, -9.8f, 0.2f)}, new float[]{4.75f, 4.45f},
                new int[]{0xFF3A1B12, 0xFF5A2E1F}, 16);
        Shapes.gbox(e, vc, light, GOLD_LIGHT, GOLD_DARK, -1.3f, -10.2f, -4.95f, 1.3f, -8.0f, -4.5f);
        Shapes.gbox(e, vc, light, 0xFF3A1B12, 0xFF3A1B12, -0.7f, -9.6f, -5.05f, 0.7f, -8.6f, -4.85f);
    }

    private static void santaHat(M e, Out vc, int light) {
        int red = 0xFFC8202C, redHi = 0xFFE5414A, fur = 0xFFF7F7F4, furShade = 0xFFDCDCD8;
        int n = 12;
        float[][] path = new float[n][];
        float[] r = new float[n];
        int[] cols = new int[n];
        for (int i = 0; i < n; i++) {
            float k = i / (float) (n - 1);
            // up, then flopping over to the side and down
            float flop = k > 0.45f ? (k - 0.45f) / 0.55f : 0;
            path[i] = Shapes.v(flop * flop * 6.5f, -8.6f - k * 6.5f + flop * flop * 4.8f, 0.6f * k);
            r[i] = 4.4f * (1 - k * 0.82f);
            cols[i] = Shapes.mix(shade(red, 0.85f), redHi, k);
        }
        Shapes.tube(e, vc, light, path, r, cols, 14);
        Shapes.ring(e, vc, light, 0, -8.5f, 0, 4.55f, 1.05f, 28, 8, furShade, fur);    // fluffy rim
        float[] tip = path[n - 1];
        Shapes.dome(e, vc, light, tip[0] + 0.4f, tip[1] + 0.6f, tip[2], 1.45f, 1.45f, 1.45f, 5, 10, fur, furShade, false);   // pom-pom
    }

    private static void vikingHelmet(M e, Out vc, int light) {
        int steel = 0xFFA9B2BC, steelHi = 0xFFDDE3EA, steelDark = 0xFF5E666F, leather = 0xFF5A3A22;
        Shapes.dome(e, vc, light, 0, -7.6f, 0, 4.85f, 4.2f, 4.85f, 6, 16, steelHi, steel, true);
        Shapes.ring(e, vc, light, 0, -7.7f, 0, 4.8f, 0.55f, 32, 6, steelDark, steel);       // rim
        // a raised ridge front to back, and rivets round the rim
        for (int i = 0; i < 9; i++) {
            double a = Math.PI * i / 8;
            float y = -7.6f - 4.25f * (float) Math.sin(a), z = 4.95f * (float) Math.cos(a);
            M rv = moved(e, 0, y, z, 0, (float) (a - Math.PI / 2), 0);
            Shapes.gbox(rv, vc, light, steelHi, steelDark, -0.45f, -0.3f, -0.6f, 0.45f, 0.3f, 0.6f);
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            Shapes.dome(e, vc, light, (float) Math.cos(a) * 5.25f, -7.7f, (float) Math.sin(a) * 5.25f, 0.32f, 0.32f, 0.32f, 2, 5, steelHi, steelDark, false);
        }
        Shapes.taper(e, vc, light, 0, -5.05f, -7.4f, -4.4f, 0.55f, 0.2f, 0.32f, 0.2f, steel, steelDark);   // nose guard
        // curved ivory horns with a leather wrap at the root
        for (int s = -1; s <= 1; s += 2) {
            M h = moved(e, s * 4.4f, -9.4f, 0.3f, 0, 0, 0);
            float[][] p = Shapes.curve(Shapes.v(0, 0, 0), Shapes.v(s * 4.2f, -0.2f, 0.2f), Shapes.v(s * 5.0f, -5.4f, -0.6f), 10);
            Shapes.tube(h, vc, light, p, Shapes.ramp(1.25f, 0f, 10, 1.1f), Shapes.ramp(0xFFD8CCAF, 0xFFFFF8E8, 10), 8);
            Shapes.tube(h, vc, light, new float[][]{Shapes.v(s * -0.2f, 0, 0), Shapes.v(s * 1.0f, -0.05f, 0.03f)}, new float[]{1.45f, 1.4f},
                    new int[]{shade(leather, 0.8f), leather}, 8);
        }
    }

    /** Sunglasses: a proper frame, dark lenses fading lighter at the bottom with a glint, arms to the ears. */
    public static void glasses(M e, Out vc, int light, final int c) {
        cached("glasses" + c, e, vc, light, (m, o) -> glassesRaw(m, o, 0, c));
    }

    private static void glassesRaw(M e, Out vc, int light, int c) {
        int frame = c, frameDark = shade(c, 0.7f);
        int lensTop = 0xFF07070B, lensBottom = 0xFF2C3446;
        for (int s = -1; s <= 1; s += 2) {
            float x0 = s < 0 ? -3.85f : 0.55f, x1 = s < 0 ? -0.55f : 3.85f;
            // rim round the lens (top thicker, like a wayfarer brow)
            Shapes.gbox(e, vc, light, frame, frameDark, x0 - 0.2f, -5.55f, -4.55f, x1 + 0.2f, -4.95f, -4.2f);
            Shapes.gbox(e, vc, light, frameDark, frameDark, x0 - 0.2f, -3.25f, -4.45f, x1 + 0.2f, -2.95f, -4.2f);
            Shapes.gbox(e, vc, light, frame, frameDark, x0 - 0.25f, -5.0f, -4.45f, x0 + 0.05f, -3.0f, -4.2f);
            Shapes.gbox(e, vc, light, frame, frameDark, x1 - 0.05f, -5.0f, -4.45f, x1 + 0.25f, -3.0f, -4.2f);
            // the lens, slightly tapered at the bottom, with a diagonal glint
            Shapes.hull(e, vc, light,
                    new float[][]{Shapes.v(x0, -4.98f, -4.42f), Shapes.v(x1, -4.98f, -4.42f), Shapes.v(x1, -4.98f, -4.25f), Shapes.v(x0, -4.98f, -4.25f)},
                    new float[][]{Shapes.v(x0 + 0.35f, -3.2f, -4.42f), Shapes.v(x1 - 0.35f, -3.2f, -4.42f), Shapes.v(x1 - 0.35f, -3.2f, -4.25f), Shapes.v(x0 + 0.35f, -3.2f, -4.25f)},
                    lensTop, lensBottom);
            float gx = (x0 + x1) / 2 - 0.5f;
            Shapes.face(e, vc, light, Shapes.v(gx, -4.8f, -4.46f), Shapes.v(gx + 0.6f, -4.8f, -4.46f), Shapes.v(gx - 0.2f, -3.6f, -4.46f), Shapes.v(gx - 0.75f, -3.6f, -4.46f),
                    0x66FFFFFF | 0xFF000000, 0xFFB8C4D8, 0xFF59647A, 0xFF59647A, Shapes.v(gx, -4.2f, 0));
            // arms back to the ears
            Shapes.gbox(e, vc, light, frame, frameDark, s < 0 ? -4.35f : 4.1f, -5.4f, -4.3f, s < 0 ? -4.1f : 4.35f, -4.85f, 1.2f);
        }
        Shapes.gbox(e, vc, light, frame, frameDark, -0.6f, -5.2f, -4.5f, 0.6f, -4.75f, -4.25f);   // bridge
    }

    /**
     * Over-ear headphones: a padded band over the head, sliders, big round cups with soft
     * cushions and a coloured ring on the outside.
     */
    public static void headphones(M e, Out vc, int light, final int c) {
        cached("headphones" + c, e, vc, light, (m, o) -> headphonesRaw(m, o, 0, c));
    }

    private static void headphonesRaw(M e, Out vc, int light, int c) {
        int dark = shade(c, 0.55f), hi = Shapes.mix(c, 0xFFFFFFFF, 0.3f), pad = 0xFF2A2A30;
        int accent = Shapes.mix(c, 0xFFFFFFFF, 0.55f);
        // the band: an arc over the top of the head
        int n = 13;
        float[][] arc = new float[n][];
        for (int i = 0; i < n; i++) {
            double a = Math.PI * i / (n - 1);
            arc[i] = Shapes.v(-(float) Math.cos(a) * 5.0f, -4.6f - (float) Math.sin(a) * 4.9f, 0.4f);
        }
        float[] w = new float[n], th = new float[n];
        for (int i = 0; i < n; i++) {
            w[i] = 0.95f;
            th[i] = 0.42f;
        }
        Shapes.tube(e, vc, light, arc, w, th, Shapes.ramp(c, c, n), 6, Shapes.v(0, 0, 1), 0);
        float[][] padArc = new float[7][];
        for (int i = 0; i < 7; i++) {
            double a = Math.PI * (0.25 + 0.5 * i / 6);
            padArc[i] = Shapes.v(-(float) Math.cos(a) * 4.45f, -4.6f - (float) Math.sin(a) * 4.35f, 0.4f);
        }
        float[] pw = {0.6f, 0.85f, 0.85f, 0.85f, 0.85f, 0.85f, 0.6f}, pt = {0.3f, 0.42f, 0.42f, 0.42f, 0.42f, 0.42f, 0.3f};
        Shapes.tube(e, vc, light, padArc, pw, pt, Shapes.ramp(pad, pad, 7), 6, Shapes.v(0, 0, 1), 0);
        for (int s = -1; s <= 1; s += 2) {
            // slider down to the cup
            Shapes.gbox(e, vc, light, 0xFFC9CED6, 0xFF7D838C, s * 4.95f - 0.25f, -5.2f, 0.1f, s * 4.95f + 0.25f, -3.4f, 0.7f);
            // cup: a short round can lying on its side, cushion against the head, cap outside
            float cx = s * 4.0f;
            Shapes.tube(e, vc, light, new float[][]{Shapes.v(cx + s * 0.1f, -2.9f, 0.3f), Shapes.v(cx + s * 1.1f, -2.9f, 0.3f)}, new float[]{2.25f, 2.0f},
                    new int[]{pad, shade(pad, 1.2f)}, 16);                                 // cushion
            Shapes.tube(e, vc, light, new float[][]{Shapes.v(cx + s * 1.0f, -2.9f, 0.3f), Shapes.v(cx + s * 2.3f, -2.9f, 0.3f)}, new float[]{2.45f, 2.3f},
                    new int[]{dark, c}, 16);                                               // shell
            Shapes.tube(e, vc, light, new float[][]{Shapes.v(cx + s * 2.25f, -2.9f, 0.3f), Shapes.v(cx + s * 2.6f, -2.9f, 0.3f)}, new float[]{1.75f, 1.6f},
                    new int[]{hi, hi}, 16);                                                // cap
            Shapes.tube(e, vc, light, new float[][]{Shapes.v(cx + s * 2.55f, -2.9f, 0.3f), Shapes.v(cx + s * 2.7f, -2.9f, 0.3f)}, new float[]{0.7f, 0.7f},
                    new int[]{accent, accent}, 12);                                        // logo dot
        }
    }

    /** A flower crown: a green vine round the head with five-petal flowers and pointed leaves. */
    public static void flowers(M e, Out vc, int light, final int c) {
        cached("flowers" + c, e, vc, light, (m, o) -> flowersRaw(m, o, 0, c));
    }

    private static void flowersRaw(M e, Out vc, int light, int c) {
        int leaf = 0xFF3FA34D, leafDark = 0xFF256B30, centre = 0xFFFFD34D;
        Shapes.ring(e, vc, light, 0, -8.15f, 0, 4.75f, 0.33f, 32, 5, leafDark, leaf);
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n - Math.PI / 2;
            float x = (float) Math.cos(a) * 4.9f, z = (float) Math.sin(a) * 4.9f;
            float face = (float) Math.atan2(-Math.cos(a), -Math.sin(a));       // turn the flower to face outwards
            boolean big = i % 2 == 0;
            float k = big ? 1f : 0.75f;
            M f = moved(e, x, -8.5f, z, face, -0.45f, 0);
            int pc = big ? c : Shapes.mix(c, 0xFFFFFFFF, 0.45f);
            for (int p = 0; p < 5; p++) {
                M pm = moved(f, 0, 0, 0, 0, 0, (float) (Math.PI * 2 * p / 5 + i * 0.4));
                Shapes.tube(pm, vc, light, new float[][]{Shapes.v(0, 0, -0.15f), Shapes.v(0, -0.85f * k, -0.3f), Shapes.v(0, -1.5f * k, -0.25f)},
                        new float[]{0.3f * k, 0.68f * k, 0.25f * k}, new float[]{0.1f, 0.13f, 0.08f},
                        new int[]{shade(pc, 0.72f), pc, Shapes.mix(pc, 0xFFFFFFFF, 0.35f)}, 4, Shapes.v(1, 0, 0), 0, false);
            }
            Shapes.dome(f, vc, light, 0, 0, -0.42f, 0.48f * k, 0.48f * k, 0.3f, 3, 8, Shapes.mix(centre, 0xFFFFFFFF, 0.3f), shade(centre, 0.8f), false);
            // a leaf either side of the small flowers
            if (!big) {
                for (int side = -1; side <= 1; side += 2) {
                    double la = a + side * Math.PI * 2 / n * 0.4;
                    float lf = (float) Math.atan2(-Math.cos(la), -Math.sin(la));
                    M l = moved(e, (float) Math.cos(la) * 4.95f, -8.2f, (float) Math.sin(la) * 4.95f, lf, -0.3f, side * 1.2f);
                    Shapes.tube(l, vc, light, new float[][]{Shapes.v(0, 0, 0), Shapes.v(0, -0.9f, -0.15f), Shapes.v(0, -1.9f, 0)},
                            new float[]{0.15f, 0.55f, 0f}, new float[]{0.08f, 0.12f, 0f}, new int[]{leafDark, leaf, Shapes.mix(leaf, 0xFFFFFFFF, 0.2f)}, 4, Shapes.v(1, 0, 0), 0);
                }
            }
        }
    }

    // =====================================================================================
    // Neck and back
    // =====================================================================================

    /** A chunky knitted scarf: striped loop round the neck, a knot, two ends with tassels. */
    public static void scarf(M e, Out vc, int light, float t, int c) {
        int dark = shade(c, 0.72f), stripe = Shapes.mix(c, 0xFFFFFFFF, 0.55f);
        int n = 24;
        float[][] loop = new float[n + 1][];
        int[] cols = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            loop[i] = Shapes.v((float) Math.cos(a) * 4.8f, 1.3f + (float) Math.sin(a * 2) * 0.12f, (float) Math.sin(a) * 2.8f);
            cols[i] = i % 6 == 3 ? stripe : (i % 2 == 0 ? c : Shapes.mix(c, dark, 0.6f));
        }
        float[] ra = new float[n + 1], rb = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            ra[i] = 1.45f;
            rb[i] = 0.6f;
        }
        Shapes.tube(e, vc, light, loop, ra, rb, cols, 6, Shapes.v(0, -1, 0), 0);
        // the knot at the front-left and two ends hanging down it, swaying
        Shapes.dome(e, vc, light, -1.8f, 1.6f, -3.1f, 1.35f, 1.3f, 0.95f, 4, 8, c, dark, false);
        float sway = (float) Math.sin(t * 0.12) * 0.5f;
        for (int k = 0; k < 2; k++) {
            float x = -1.8f + (k == 0 ? -0.7f : 0.8f), len = k == 0 ? 7.5f : 6.2f;
            float[][] p = Shapes.curve(Shapes.v(x, 1.8f, -3.2f), Shapes.v(x + sway * 0.4f, 1.8f + len * 0.5f, -3.5f - k * 0.2f),
                    Shapes.v(x + sway, 1.8f + len, -3.0f - k * 0.3f), 8);
            int[] pc = new int[8];
            for (int i = 0; i < 8; i++) pc[i] = i == 5 ? stripe : (i % 2 == 0 ? c : Shapes.mix(c, dark, 0.6f));
            Shapes.tube(e, vc, light, p, Shapes.ramp(1.25f, 1.15f, 8, 1), Shapes.ramp(0.35f, 0.3f, 8, 1), pc, 4, Shapes.v(1, 0, 0), (float) (Math.PI / 4));
            float[] end = p[7];
            for (int f = 0; f < 4; f++) {                                       // tassels
                float fx = end[0] - 0.9f + f * 0.6f;
                Shapes.tube(e, vc, light, new float[][]{Shapes.v(fx, end[1], end[2]), Shapes.v(fx + sway * 0.2f, end[1] + 1.4f, end[2])},
                        new float[]{0.22f, 0.12f}, new int[]{dark, c}, 4);
            }
        }
    }

    /** A bow tie: two folded loops narrowing into a knot, with creases. */
    public static void bowtie(M e, Out vc, int light, final int c) {
        cached("bowtie" + c, e, vc, light, (m, o) -> bowtieRaw(m, o, 0, c));
    }

    private static void bowtieRaw(M e, Out vc, int light, int c) {
        int dark = shade(c, 0.62f), hi = Shapes.mix(c, 0xFFFFFFFF, 0.25f);
        float z0 = -2.55f, z1 = -2.1f;
        for (int s = -1; s <= 1; s += 2) {
            float xin = s * 0.55f, xout = s * 2.9f;
            // narrow at the knot, wide and slightly pinched at the outer end
            Shapes.hull(e, vc, light,
                    new float[][]{Shapes.v(xin, 0.55f, z0), Shapes.v(xin, 1.45f, z0), Shapes.v(xin, 1.45f, z1), Shapes.v(xin, 0.55f, z1)},
                    new float[][]{Shapes.v(xout, -0.2f, z0 - 0.15f), Shapes.v(xout, 2.2f, z0 - 0.15f), Shapes.v(xout, 2.2f, z1), Shapes.v(xout, -0.2f, z1)},
                    dark, hi);
            Shapes.gbox(e, vc, light, dark, dark, s < 0 ? xout - 0.05f : xout - 0.2f, -0.15f, z0 - 0.2f, s < 0 ? xout + 0.2f : xout + 0.05f, 2.15f, z1 - 0.05f);
            Shapes.face(e, vc, light, Shapes.v(xin + s * 0.4f, 1.0f, z0 - 0.04f), Shapes.v(xout - s * 0.3f, 0.5f, z0 - 0.19f),
                    Shapes.v(xout - s * 0.3f, 0.65f, z0 - 0.19f), Shapes.v(xin + s * 0.4f, 1.1f, z0 - 0.04f), dark, dark, dark, dark, Shapes.v(0, 1, 0));   // crease
        }
        Shapes.dome(e, vc, light, 0, 1.0f, -2.45f, 0.7f, 0.75f, 0.42f, 3, 8, hi, dark, false);   // knot
    }

    /** Dragon spikes down the spine: curved, pointed, biggest between the shoulders, dark root to light tip. */
    public static void spikes(M e, Out vc, int light, final int c) {
        cached("spikes" + c, e, vc, light, (m, o) -> spikesRaw(m, o, 0, c));
    }

    private static void spikesRaw(M e, Out vc, int light, int c) {
        float[] sizes = {1.5f, 2.1f, 2.5f, 2.2f, 1.7f, 1.2f};
        int root = rootOf(c), tip = Shapes.mix(c, 0xFFFFFFFF, 0.4f);
        for (int i = 0; i < sizes.length; i++) {
            float y = 1.2f + i * 1.85f, s = sizes[i];
            float[][] p = Shapes.curve(Shapes.v(0, y, 1.9f), Shapes.v(0, y + 0.1f, 2f + s * 0.75f), Shapes.v(0, y - s * 0.55f, 2f + s * 1.25f), 7);
            Shapes.tube(e, vc, light, p, Shapes.ramp(s * 0.55f, 0, 7, 0.8f), Shapes.ramp(0.55f, 0, 7, 0.8f),
                    Shapes.ramp(root, tip, 7), 4, Shapes.v(0, 1, 0), 0);
        }
    }

    /** A cat tail (any colour) or a big fluffy fox tail with a white tip, curving and swaying. */
    public static void tail(M e, Out vc, int light, float t, String kind) {
        boolean fox = "fox".equals(kind);
        int c = fox ? 0xFFE0762C : colour(kind);
        float sway = (float) Math.sin(t * 0.15);
        int n = fox ? 12 : 16;
        float[][] p = new float[n][];
        float[] r = new float[n];
        int[] cols = new int[n];
        for (int i = 0; i < n; i++) {
            float k = i / (float) (n - 1);
            float sw = sway * k * k * (fox ? 2.2f : 3.2f);
            if (fox) {
                p[i] = Shapes.v(sw, 10.4f + k * 7f - k * k * 5.5f, 2.0f + k * 12f - k * k * 2f);
                r[i] = 0.9f + (float) Math.sin(Math.min(1, k * 1.1f) * Math.PI) * 1.75f + (i == n - 1 ? -1.4f : 0);
                cols[i] = k > 0.72f ? Shapes.mix(0xFFF6F2EA, 0xFFFFFFFF, (k - 0.72f) * 3) : Shapes.mix(shade(c, 0.85f), Shapes.mix(c, 0xFFFFC08A, 0.25f), k / 0.72f);
            } else {
                // out from the back, down, then curling up like a question mark
                p[i] = Shapes.v(sw, 11f + k * 6f - k * k * 13f, 2.0f + k * 8f - k * k * 2.5f);
                r[i] = 0.95f - 0.25f * k;
                cols[i] = Shapes.mix(shade(c, 0.85f), tipOf(c), k * 0.6f);
            }
        }
        Shapes.tube(e, vc, light, p, r, cols, 8);
    }

    /** A rounded backpack: zipped body, front pocket, shoulder straps with buckles, a side loop. */
    public static void backpack(M e, Out vc, int light, final int c) {
        cached("backpack" + c, e, vc, light, (m, o) -> backpackRaw(m, o, 0, c));
    }

    private static void backpackRaw(M e, Out vc, int light, int c) {
        int dark = shade(c, 0.68f), hi = Shapes.mix(c, 0xFFFFFFFF, 0.18f), trim = 0xFF26262B, metal = 0xFFC9CED6;
        // body: a box with a rounded top (a half cylinder across it)
        Shapes.gbox(e, vc, light, c, dark, -3.4f, 3.0f, 2.0f, 3.4f, 10.0f, 5.4f);
        Shapes.tube(e, vc, light, new float[][]{Shapes.v(-3.4f, 3.05f, 3.7f), Shapes.v(3.4f, 3.05f, 3.7f)}, new float[]{1.7f, 1.7f},
                new float[]{1.7f, 1.7f}, new int[]{hi, hi}, 12, Shapes.v(0, -1, 0), 0);
        // zip round the top
        Shapes.tube(e, vc, light, Shapes.curve(Shapes.v(-3.45f, 4.6f, 5.45f), Shapes.v(0, 0.2f, 5.6f), Shapes.v(3.45f, 4.6f, 5.45f), 9),
                Shapes.ramp(0.16f, 0.16f, 9, 1), Shapes.ramp(trim, trim, 9), 4);
        Shapes.gbox(e, vc, light, metal, 0xFF7D838C, 1.6f, 2.0f, 5.4f, 2.1f, 3.3f, 5.75f);       // zip pull
        // front pocket with its own zip
        Shapes.gbox(e, vc, light, hi, dark, -2.6f, 6.2f, 5.3f, 2.6f, 9.4f, 6.5f);
        Shapes.gbox(e, vc, light, trim, trim, -2.5f, 6.15f, 6.45f, 2.5f, 6.45f, 6.6f);
        Shapes.gbox(e, vc, light, metal, 0xFF7D838C, -0.25f, 6.2f, 6.55f, 0.25f, 7.3f, 6.8f);
        // straps over the shoulders and down the front, with buckles
        for (int s = -1; s <= 1; s += 2) {
            float x = s * 2.4f;
            float[][] strap = {Shapes.v(x, 3.0f, 2.1f), Shapes.v(x, -0.35f, 1.6f), Shapes.v(x, -0.4f, -1.2f), Shapes.v(x, 0.6f, -2.3f),
                    Shapes.v(x, 5.5f, -2.35f), Shapes.v(x, 8.5f, -2.25f)};
            Shapes.tube(e, vc, light, strap, Shapes.ramp(0.75f, 0.75f, 6, 1), Shapes.ramp(0.2f, 0.2f, 6, 1), Shapes.ramp(dark, dark, 6), 4,
                    Shapes.v(1, 0, 0), (float) (Math.PI / 4));
            Shapes.gbox(e, vc, light, metal, 0xFF7D838C, x - 0.85f, 4.6f, -2.65f, x + 0.85f, 5.4f, -2.3f);
        }
        Shapes.ring(moved(e, 3.5f, 6.5f, 3.7f, 0, 0, (float) (Math.PI / 2)), vc, light, 0, 0, 0, 0.9f, 0.18f, 12, 4, trim, dark);   // side loop
    }

    /**
     * A katana across the back in its lacquered scabbard: gold-capped scabbard, round guard,
     * a handle wrapped in diamond-patterned cord, a red cord knotted on.
     */
    public static void katana(M e, Out vc, int light) {
        cached("katana", e, vc, light, (m, o) -> katanaRaw(m, o, 0));
    }

    private static void katanaRaw(M e, Out vc, int light) {
        M kq = moved(e, 0, 6f, 3.0f, 0, 0, (float) Math.toRadians(-40));
        int lacquer = 0xFF1C1418, lacquerHi = 0xFF4A2A34, wrap = 0xFF15151A, wrapHi = 0xFF7A1F2B;
        // scabbard: slightly flattened, gently curved, a shine down its length
        int n = 9;
        float[][] saya = new float[n][];
        for (int i = 0; i < n; i++) {
            float k = i / (float) (n - 1);
            saya[i] = Shapes.v(0, 4f - k * 21f, 0.6f * (float) Math.sin(k * Math.PI));
        }
        int[] sc = new int[n];
        for (int i = 0; i < n; i++) sc[i] = Shapes.mix(lacquer, lacquerHi, (float) Math.sin(i / (float) (n - 1) * Math.PI) * 0.6f);
        Shapes.tube(kq, vc, light, saya, Shapes.ramp(0.6f, 0.55f, n, 1), Shapes.ramp(0.85f, 0.8f, n, 1), sc, 8, Shapes.v(1, 0, 0), 0);
        Shapes.tube(kq, vc, light, new float[][]{Shapes.v(0, -16.9f, 0.05f), Shapes.v(0, -17.8f, 0.05f)}, new float[]{0.62f, 0.4f},
                new float[]{0.88f, 0.6f}, new int[]{GOLD_DARK, GOLD_LIGHT}, 8, Shapes.v(1, 0, 0), 0);        // end cap
        Shapes.tube(kq, vc, light, new float[][]{Shapes.v(0, 2.6f, 0), Shapes.v(0, 4.0f, 0)}, new float[]{0.7f, 0.68f},
                new float[]{0.95f, 0.92f}, new int[]{GOLD_DARK, GOLD}, 8, Shapes.v(1, 0, 0), 0);            // collar
        // guard: a thick round disc
        Shapes.tube(kq, vc, light, new float[][]{Shapes.v(0, 4.0f, 0), Shapes.v(0, 4.6f, 0)}, new float[]{1.9f, 1.9f},
                new int[]{0xFF3A3530, GOLD}, 12);
        // handle: a diamond wrap (alternating colours), gold pommel
        int hn = 9;
        float[][] tsuka = new float[hn][];
        int[] hc = new int[hn];
        for (int i = 0; i < hn; i++) {
            tsuka[i] = Shapes.v(0, 4.6f + i * 0.85f, 0);
            hc[i] = i % 2 == 0 ? wrap : wrapHi;
        }
        Shapes.tube(kq, vc, light, tsuka, Shapes.ramp(0.62f, 0.56f, hn, 1), Shapes.ramp(0.78f, 0.72f, hn, 1), hc, 6, Shapes.v(1, 0, 0), 0);
        Shapes.dome(kq, vc, light, 0, 11.6f, 0, 0.7f, 0.55f, 0.85f, 3, 8, GOLD_DARK, GOLD_LIGHT, false);
        // the red cord tied round the scabbard
        Shapes.ring(moved(kq, 0, 1.4f, 0, 0, 0, 0), vc, light, 0, 0, 0, 0.95f, 0.2f, 12, 4, 0xFF8E1B26, 0xFFD13A45);
        Shapes.tube(kq, vc, light, Shapes.curve(Shapes.v(0.8f, 1.4f, -0.6f), Shapes.v(1.6f, 0.4f, -0.8f), Shapes.v(1.2f, -2.4f, -0.6f), 6),
                Shapes.ramp(0.2f, 0.18f, 6, 1), Shapes.ramp(0xFFB22A36, 0xFFD13A45, 6), 4);
    }

    /** The katana's glow: a soft aura along the scabbard. */
    public static void katanaGlow(M e, Out vc, float pulse) {
        M kq = moved(e, 0, 6f, 3.0f, 0, 0, (float) Math.toRadians(-40));
        int a = Math.round(70 * pulse);
        Shapes.tube(kq, vc, BRIGHT, new float[][]{Shapes.v(0, 11.8f, 0), Shapes.v(0, 4f, 0), Shapes.v(0, -8f, 0.5f), Shapes.v(0, -18f, 0)},
                new float[]{1.0f, 1.3f, 1.2f, 0.9f}, new float[]{1.2f, 1.5f, 1.4f, 1.1f},
                new int[]{tint(0xFF9FD8FF, a), tint(0xFF9FD8FF, a), tint(0xFF9FD8FF, a), tint(0xFF9FD8FF, a / 2)}, 8, Shapes.v(1, 0, 0), 0);
    }

    // =====================================================================================
    // Hands and feet
    // =====================================================================================

    /** Big cartoon feet: a rounded foot, five toes with nails, a little ankle. */
    public static void foot(M e, Out vc, int light) {
        cached("foot", e, vc, light, (m, o) -> footRaw(m, o, 0));
    }

    private static void footRaw(M e, Out vc, int light) {
        int skin = 0xFFF0C8A0, under = 0xFFD9A47E, nail = 0xFFF6D9CB;
        Shapes.dome(e, vc, light, 0, 10.9f, -1.6f, 3.4f, 2.7f, 4.4f, 5, 14, skin, under, true);
        Shapes.tube(e, vc, light, new float[][]{Shapes.v(0, 10.9f, -1.4f), Shapes.v(0, 12.15f, -1.6f)}, new float[]{3.4f, 3.4f},
                new float[]{4.4f, 4.4f}, new int[]{under, shade(under, 0.85f)}, 14, Shapes.v(1, 0, 0), 0);  // sole
        float[] tx = {-2.4f, -1.15f, 0.05f, 1.2f, 2.3f}, tr = {0.95f, 0.8f, 0.75f, 0.7f, 0.62f};
        for (int i = 0; i < 5; i++) {
            float x = tx[i], r = tr[i], z = -5.5f + Math.abs(i - 1) * 0.3f;
            Shapes.dome(e, vc, light, x, 11.3f, z, r, r, r * 1.15f, 4, 8, skin, under, false);
            Shapes.dome(e, vc, light, x, 10.75f + (0.95f - r) * 0.6f, z - r * 0.55f, r * 0.42f, 0.1f, r * 0.38f, 2, 8, nail, nail, true);
        }
    }

    /** A boxing glove: round padded fist, thumb, laced cuff with a white stripe, a soft shine. */
    public static void glove(M e, Out vc, int light, final int c, final int side) {
        cached("glove" + c + side, e, vc, light, (m, o) -> gloveRaw(m, o, 0, c, side));
    }

    private static void gloveRaw(M e, Out vc, int light, int c, int side) {
        float cx = side < 0 ? -1f : 1f;
        int dark = shade(c, 0.66f), hi = Shapes.mix(c, 0xFFFFFFFF, 0.45f);
        Shapes.dome(e, vc, light, cx, 9.9f, -0.2f, 3.3f, 3.2f, 3.6f, 6, 14, hi, dark, false);           // the fist
        Shapes.dome(e, vc, light, cx + (side < 0 ? 2.2f : -2.2f) * -1 * 0, 9.0f, -3.0f, 1.25f, 1.9f, 1.2f, 4, 8, c, dark, false);   // thumb
        // cuff: a short padded tube with a white stripe and laces
        Shapes.tube(e, vc, light, new float[][]{Shapes.v(cx, 5.2f, 0), Shapes.v(cx, 7.4f, 0)}, new float[]{2.75f, 2.95f}, new int[]{dark, c}, 14);
        Shapes.tube(e, vc, light, new float[][]{Shapes.v(cx, 5.9f, 0), Shapes.v(cx, 6.6f, 0)}, new float[]{2.85f, 2.88f}, new int[]{0xFFF4F4F4, 0xFFFFFFFF}, 14);
        for (int i = 0; i < 3; i++) {
            float y = 5.5f + i * 0.75f;
            Shapes.gbox(e, vc, light, 0xFFF4F4F4, 0xFFDADADA, cx - 0.9f, y, -2.95f - 0.05f * i, cx + 0.9f, y + 0.22f, -2.75f);
        }
    }

    // =====================================================================================
    // Wings
    // =====================================================================================

    /**
     * Wings from the shoulder blades, swept back and slowly flapping. Styles: feathered (three
     * layers of tapered feathers on an arched bone), dragon (bones with a scalloped skin between),
     * butterfly and fairy (shaped panes with patterns), demon (curved blades) and energy (rays of
     * light). With {@code shell} it draws the soft glow around them instead.
     */
    public static void wings(M e, Out vc, int light, float t, int c, String style, boolean shell, float pulse) {
        float flap = (float) Math.sin(t * 0.1) * 0.16f;
        float g = shell ? 0.55f : 0f;
        int a = shell ? Math.round(60 * pulse) : 0xFF;
        String st = style == null ? "" : style;
        for (int s = -1; s <= 1; s += 2) {
            // out to the side, then swept BACK (+z); built for the +x side and mirrored for the other
            M w = moved(e, s * 1.5f, 2.5f, 2.4f, -s * (0.55f + flap), 0, -s * 0.32f);
            if (s < 0) w = w.copy().mirrorX();
            if (st.equals("energy")) {                                 // its rays wave about: built every frame
                energyWing(w, vc, light, c, g, a, t);
                continue;
            }
            if (shell) {                                              // the glow pulses: built every frame
                buildWing(st, w, vc, light, c, g, a);
                continue;
            }
            final String sty = st;
            cached("wing" + st + c, w, vc, light, (m, o) -> buildWing(sty, m, o, 0, c, 0f, 0xFF));
        }
    }

    private static void buildWing(String st, M w, Out vc, int light, int c, float g, int a) {
        if (st.equals("fairy")) paneWing(w, vc, light, c, g, a, 0, true);
        else if (st.equals("butterfly")) paneWing(w, vc, light, c, g, a, 0, false);
        else if (st.equals("demon")) demonWing(w, vc, light, c, g, a);
        else if (st.equals("dragon")) dragonWing(w, vc, light, c, g, a);
        else featherWing(w, vc, light, c, g, a, 0);
    }

    /** Points of the feathered wing's arched bone (root to tip). */
    private static final float[][] BONE = {{0, 0, 0}, {3f, -1.3f, 0.1f}, {6.5f, -2.6f, 0.2f}, {10f, -3.9f, 0.3f}, {13.5f, -4.8f, 0.3f}, {17f, -5.3f, 0.2f}, {19f, -5.2f, 0.1f}};

    private static float[] along(float[][] path, float f) {
        float x = f * (path.length - 1);
        int i = Math.min(path.length - 2, (int) x);
        float k = x - i;
        return Shapes.v(path[i][0] + (path[i + 1][0] - path[i][0]) * k, path[i][1] + (path[i + 1][1] - path[i][1]) * k,
                path[i][2] + (path[i + 1][2] - path[i][2]) * k);
    }

    private static void featherWing(M w, Out vc, int light, int c, float g, int a, float t) {
        int lum = ((c >> 16 & 255) * 299 + (c >> 8 & 255) * 587 + (c & 255) * 114) / 1000;
        int root = tint(shade(c, 0.8f), a), mid = tint(c, a);
        int tip = tint(lum > 200 ? Shapes.mix(c, 0xFFC9D2E2, 0.55f) : tipOf(c), a);
        // the arched bone along the top
        Shapes.tube(w, vc, light, BONE, Shapes.ramp(1.0f + g, 0.45f + g, BONE.length, 1), Shapes.ramp(tint(Shapes.mix(c, 0xFFFFFFFF, 0.15f), a), mid, BONE.length), 6);
        float flutter = (float) Math.sin(t * 0.22) * 0.04f;
        // primaries / secondaries: long feathers fanning down, longest towards the tip
        int n = 15;
        for (int i = 0; i < n; i++) {
            float f = i / (float) (n - 1);
            float[] p = along(BONE, f * 0.97f);
            float len = 9f + 10f * (float) Math.pow(f, 1.3);
            float ang = (float) Math.toRadians(-4 + 46 * f) + flutter * (i % 2 == 0 ? 1 : -1);
            feather(w, vc, light, p[0], p[1] + 0.3f, p[2] + 0.35f + 0.03f * i, ang, len, 3.3f - 0.9f * f, 0.2f, root, mid, tip, g, i % 2 == 0 ? 1f : 0.93f);
        }
        if (g > 0) return;                                                     // the glow only needs the outline
        // coverts: a middle row of shorter, rounder feathers over the roots
        int m = 11;
        for (int i = 0; i < m; i++) {
            float f = (i + 0.3f) / m;
            float[] p = along(BONE, f * 0.9f);
            feather(w, vc, light, p[0], p[1] + 0.2f, p[2] - 0.05f, (float) Math.toRadians(-2 + 34 * f), 5.2f + 2.6f * f, 2.6f, 0.22f,
                    root, mid, tint(Shapes.mix(c, 0xFFFFFFFF, 0.1f), a), 0, i % 2 == 0 ? 1.03f : 0.97f);
        }
        // small coverts along the bone
        int k = 9;
        for (int i = 0; i < k; i++) {
            float f = (i + 0.5f) / k;
            float[] p = along(BONE, f * 0.85f);
            feather(w, vc, light, p[0], p[1] - 0.2f, p[2] - 0.3f, (float) Math.toRadians(4 + 24 * f), 2.8f + 1.2f * f, 2.2f, 0.24f,
                    tint(Shapes.mix(c, 0xFFFFFFFF, 0.12f), a), mid, mid, 0, 1f);
        }
    }

    /**
     * One feather: a tapered blade hanging from (x, y, z), turned out by {@code ang} (radians),
     * diamond-shaped across so it has a raised shaft down the middle; root → middle → tip colours.
     */
    private static void feather(M w, Out vc, int light, float x, float y, float z, float ang, float len, float width, float thick,
                                int root, int mid, int tip, float g, float tone) {
        float dx = (float) Math.sin(ang), dy = (float) Math.cos(ang);
        float[] prof = {0.6f, 1.0f, 0.85f, 0.5f, 0.0f};
        int n = prof.length;
        float[][] pts = new float[n][];
        float[] ra = new float[n], rb = new float[n];
        int[] cols = new int[n];
        for (int i = 0; i < n; i++) {
            float k = i / (float) (n - 1);
            float bend = k * k * 0.9f;                                    // curves gently outwards
            pts[i] = Shapes.v(x + dx * len * k + bend, y + dy * len * k, z + k * 0.3f);
            ra[i] = width / 2 * prof[i] + (prof[i] > 0 ? g : 0);
            rb[i] = thick + (prof[i] > 0 ? g * 0.6f : 0);
            int col = k < 0.45f ? Shapes.mix(root, mid, k / 0.45f) : Shapes.mix(mid, tip, (k - 0.45f) / 0.55f);
            cols[i] = tone == 1f ? col : (col & 0xFF000000) | (shade(col, tone) & 0xFFFFFF);
        }
        Shapes.tube(w, vc, light, pts, ra, rb, cols, 4, Shapes.v(-dy, dx, 0), 0, false);
    }

    private static void dragonWing(M w, Out vc, int light, int c, float g, int a) {
        int bone = tint(shade(c, 0.5f), a), boneHi = tint(shade(c, 0.75f), a), skinIn = tint(shade(c, 0.7f), shellA(a, 0xF0)), skinOut = tint(c, shellA(a, 0xF0));
        float[] wrist = Shapes.v(9.5f, -5.5f, 0.3f);
        // arm: shoulder → elbow → wrist, with a hooked claw
        float[][] arm = {Shapes.v(0, 0, 0), Shapes.v(4.5f, -4.2f, 0.4f), wrist};
        Shapes.tube(w, vc, light, arm, new float[]{1.0f + g, 0.85f + g, 0.75f + g}, new int[]{bone, boneHi, bone}, 6);
        Shapes.tube(w, vc, light, Shapes.curve(wrist, Shapes.v(10.5f, -7.8f, 0.3f), Shapes.v(12f, -8.2f, 0.4f), 4),
                new float[]{0.5f + g, 0.35f + g, 0.2f + g, 0}, Shapes.ramp(bone, tint(0xFFEDE6D6, a), 4), 4);
        // four long fingers fanning down and out
        float[][] tips = {Shapes.v(20f, -3.5f, 0.5f), Shapes.v(19f, 3.5f, 0.6f), Shapes.v(15f, 9.5f, 0.7f), Shapes.v(8.5f, 12.5f, 0.8f)};
        for (float[] tp : tips) {
            float[][] f = Shapes.curve(wrist, Shapes.v((wrist[0] + tp[0]) / 2 + 0.6f, (wrist[1] + tp[1]) / 2 - 0.8f, (wrist[2] + tp[2]) / 2), tp, 5);
            Shapes.tube(w, vc, light, f, Shapes.ramp(0.55f + g, 0.12f + g, 5, 1), Shapes.ramp(boneHi, bone, 5), 5);
        }
        // the skin: from the wrist to each pair of fingertips, scalloped between them, and back to the body
        float[] in = Shapes.v(10f, 2f, -1.5f);
        float[][] edge = {tips[0], tips[1], tips[2], tips[3], Shapes.v(1.5f, 9.5f, 0.6f)};
        for (int i = 0; i < edge.length - 1; i++) {
            float[] p0 = edge[i], p1 = edge[i + 1];
            float[] sc = Shapes.v((p0[0] + p1[0]) / 2 - (p1[1] - p0[1]) * 0.12f + (wrist[0] - (p0[0] + p1[0]) / 2) * 0.22f,
                    (p0[1] + p1[1]) / 2 + (wrist[1] - (p0[1] + p1[1]) / 2) * 0.22f, (p0[2] + p1[2]) / 2);
            Shapes.tri(w, vc, light, wrist, p0, sc, skinIn, skinOut, skinOut, in);
            Shapes.tri(w, vc, light, wrist, sc, p1, skinIn, skinOut, skinOut, in);
        }
        Shapes.tri(w, vc, light, Shapes.v(0, 0, 0), wrist, edge[4], skinIn, skinIn, skinOut, in);
        Shapes.tri(w, vc, light, Shapes.v(0, 0, 0), edge[4], Shapes.v(0, 8f, 0.3f), skinIn, skinOut, skinOut, in);
    }

    private static int shellA(int a, int solid) {
        return a == 0xFF ? solid : a;
    }

    /** Butterfly (and the slimmer, paler fairy) wing: a shaped upper and lower pane with a dark rim and spots. */
    private static void paneWing(M w, Out vc, int light, int c, float g, int a, float t, boolean fairy) {
        float[][] upper = fairy
                ? new float[][]{{1, -1}, {4, -6}, {8, -10.5f}, {12, -13}, {15, -13}, {16, -11}, {14, -7}, {9, -2.5f}, {3, 0.3f}}
                : new float[][]{{1, -1}, {4, -6.5f}, {8, -9.5f}, {12, -10.5f}, {15, -9.5f}, {16, -7f}, {14.5f, -3.5f}, {10, -1}, {4, 0.6f}};
        float[][] lower = fairy
                ? new float[][]{{1, 1}, {4, 3}, {7, 6}, {8.5f, 9.5f}, {7.5f, 12}, {5, 12}, {2.5f, 8}, {0.5f, 4}}
                : new float[][]{{1, 1}, {5, 2}, {9, 4}, {10.5f, 7.5f}, {9, 10.5f}, {5.5f, 11}, {2.5f, 8.5f}, {0.5f, 4}};
        int inner = fairy ? tint(Shapes.mix(c, 0xFFFFFFFF, 0.55f), shellA(a, 0xFF)) : tint(shade(c, 0.45f), a);
        int body = fairy ? tint(Shapes.mix(c, 0xFFFFFFFF, 0.3f), shellA(a, 0xFF)) : tint(c, a);
        int rim = fairy ? tint(Shapes.mix(c, 0xFFFFFFFF, 0.8f), a) : tint(shade(c, 0.28f), a);
        pane(w, vc, light, upper, Shapes.v(0, -0.5f, 0), inner, body, rim, g, 0f);
        pane(w, vc, light, lower, Shapes.v(0, 0.5f, 0.15f), inner, shade(body, 0.92f) | (body & 0xFF000000), rim, g, 0.15f);
        if (g > 0) return;
        if (fairy) {                                                    // fine veins out from the root
            int vein = Shapes.mix(c, 0xFF000000, 0.15f);
            for (float[][] pts : new float[][][]{upper, lower}) {
                for (int i = 1; i < pts.length - 1; i += 2) {
                    float[] tp = pts[i];
                    Shapes.tube(w, vc, light, new float[][]{Shapes.v(0.5f, 0, -0.08f), Shapes.v(tp[0] * 0.85f, tp[1] * 0.85f, -0.08f)},
                            new float[]{0.14f, 0.06f}, new int[]{vein, vein}, 4);
                }
            }
        } else {                                                        // white spots in the dark rim
            float[][] dots = {{13.6f, -8.4f, 0.7f}, {15f, -6.0f, 0.55f}, {11.3f, -9.4f, 0.5f}, {8.2f, 9.0f, 0.55f}, {5.9f, 10.0f, 0.42f}};
            for (float[] d : dots) Shapes.dome(w, vc, light, d[0], d[1], -0.1f, d[2], d[2], 0.1f, 3, 8, 0xFFFFFFFF, 0xFFE8E8E8, false);
            // an eye spot: dark ring, bright middle
            Shapes.dome(w, vc, light, 9.5f, -5.6f, -0.08f, 1.6f, 1.6f, 0.08f, 3, 12, shade(c, 0.3f), shade(c, 0.3f), false);
            Shapes.dome(w, vc, light, 9.5f, -5.6f, -0.14f, 1.0f, 1.0f, 0.08f, 3, 12, Shapes.mix(c, 0xFFFFFFFF, 0.65f), Shapes.mix(c, 0xFFFFFFFF, 0.35f), false);
        }
    }

    /** A flat pane: an inner fan (dark at the root) and a rim band round the outline. */
    private static void pane(M w, Out vc, int light, float[][] outline, float[] root, int inner, int body, int rim, float g, float z) {
        int n = outline.length;
        float[] in = Shapes.v(6, 0, z - 1);
        float[][] o = new float[n][], m = new float[n][];
        for (int i = 0; i < n; i++) {
            float ox = outline[i][0], oy = outline[i][1];
            float dx = ox - root[0], dy = oy - root[1], d = (float) Math.sqrt(dx * dx + dy * dy);
            float k = d < 0.01f ? 1 : (d + g) / d;
            o[i] = Shapes.v(root[0] + dx * k, root[1] + dy * k, z);
            m[i] = Shapes.v(root[0] + dx * 0.78f, root[1] + dy * 0.78f, z);
        }
        float[] r = Shapes.v(root[0], root[1], z);
        for (int i = 0; i < n - 1; i++) {
            Shapes.tri(w, vc, light, r, m[i], m[i + 1], inner, body, body, in);
            Shapes.face(w, vc, light, m[i], o[i], o[i + 1], m[i + 1], body, rim, rim, body, in);
        }
    }

    private static void demonWing(M w, Out vc, int light, int c, float g, int a) {
        int root = tint(shade(c, 0.45f), a), mid = tint(shade(c, 0.8f), a), tip = tint(Shapes.mix(c, 0xFFFFFFFF, 0.25f), a);
        // three long blades curling out and up like scythes, from a bony shoulder
        Shapes.tube(w, vc, light, new float[][]{Shapes.v(0, 0, 0), Shapes.v(3.5f, -2.5f, 0.3f)}, new float[]{1.1f + g, 0.8f + g}, new int[]{root, mid}, 6);
        // three sickle blades fanning out (top one highest), each curling up at the tip
        float[] up = {38f, 12f, -16f};
        for (int b = 0; b < 3; b++) {
            float len = 16f - b * 2.5f;
            double th = Math.toRadians(up[b]), th2 = Math.toRadians(up[b] + 34);
            float[] p0 = Shapes.v(3f, -2.2f + b * 0.6f, 0.25f * b);
            float[] p1 = Shapes.v(p0[0] + (float) Math.cos(th) * len * 0.62f, p0[1] - (float) Math.sin(th) * len * 0.62f, p0[2] + 0.4f);
            float[] p2 = Shapes.v(p1[0] + (float) Math.cos(th2) * len * 0.45f, p1[1] - (float) Math.sin(th2) * len * 0.45f, p0[2] + 0.9f);
            float[][] p = Shapes.curve(p0, p1, p2, 10);
            float[] wide = new float[10], thin = new float[10];
            int[] cols = new int[10];
            for (int i = 0; i < 10; i++) {
                float k = i / 9f;
                wide[i] = (1.7f - b * 0.25f) * (float) Math.sin(Math.min(1, 0.25f + k * 0.9f) * Math.PI) + (k < 1 ? g : 0);
                thin[i] = 0.34f * (1 - k * 0.8f) + (k < 1 ? g * 0.6f : 0);
                cols[i] = k < 0.45f ? Shapes.mix(root, mid, k / 0.45f) : Shapes.mix(mid, tip, (k - 0.45f) / 0.55f);
            }
            wide[9] = 0;
            thin[9] = 0;
            Shapes.tube(w, vc, light, p, thin, wide, cols, 4, Shapes.v(0, 0, 1), 0);
        }
    }

    private static void energyWing(M w, Out vc, int light, int c, float g, int a, float t) {
        int core = tint(Shapes.mix(c, 0xFFFFFFFF, 0.2f), shellA(a, 0xFF)), hot = tint(0xFFFFFFFF, shellA(a, 0xFF));
        int rays = 9;
        for (int i = 0; i < rays; i++) {
            float ang = -0.9f + i * (1.6f / (rays - 1));
            float len = 12f + (float) Math.sin(i * 1.7) * 3f + (float) Math.sin(t * 0.15 + i) * 1.2f;
            float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
            float[][] p = {Shapes.v(0, 3f, 0.1f * i), Shapes.v(dx * len * 0.5f, 3f + dy * len * 0.5f, 0.1f * i), Shapes.v(dx * len, 3f + dy * len, 0.1f * i)};
            Shapes.tube(w, vc, light, p, new float[]{0.25f + g, 0.5f + g, 0.1f + g}, new int[]{tint(shade(c, 0.8f), shellA(a, 0xFF)), core, hot}, 4);
            if (g == 0) {
                M orb = moved(w, dx * len, 3f + dy * len, 0.1f * i, (float) (t * 0.1 + i), 0.5f, 0);
                Shapes.gem(orb, vc, light, 0.55f, 0.5f, 4, Shapes.mix(c, 0xFFFFFFFF, 0.6f));
            }
        }
    }

    /** A box on one side of the body (mirrored for the other side). */
    static void wbox(M e, Out vc, int light, int c, int side,
                     float x0, float y0, float z0, float x1, float y1, float z1) {
        if (side < 0) box(e, vc, light, c, -x1, y0, z0, -x0, y1, z1);
        else box(e, vc, light, c, x0, y0, z0, x1, y1, z1);
    }
}

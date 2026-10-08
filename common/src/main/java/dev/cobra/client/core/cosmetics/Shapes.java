package dev.cobra.client.core.cosmetics;

import dev.cobra.client.core.cosmetics.CosmeticModels.M;
import dev.cobra.client.core.cosmetics.CosmeticModels.Out;

/**
 * Smooth building blocks for the cosmetics (same space as {@link CosmeticModels}: 1 unit = 1 skin
 * pixel, y DOWN, face at -z): tapered tubes (horns, tails, feathers), domes, rings, gems and
 * freely shaped faces. Every corner can have its own colour, so parts fade from root to tip
 * instead of being one flat colour, and every face is lit by which way it points.
 */
final class Shapes {
    private Shapes() {}

    static float[] v(float x, float y, float z) {
        return new float[]{x, y, z};
    }

    /** Colour lit by the face direction (part space, y down): tops brighter, undersides darker. */
    static int lit(int c, float nx, float ny, float nz) {
        float k = 0.88f - 0.22f * ny - 0.07f * nz + 0.06f * nx;
        return CosmeticModels.shade(c, k);
    }

    /** Linear blend of two colours (alpha too). */
    static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
                | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    /**
     * One face (a, b, c, d; d may equal c for a triangle) with a colour per corner. {@code inside}
     * is a point inside the solid: the normal is turned to point away from it.
     */
    static void face(M e, Out o, int light, float[] a, float[] b, float[] c, float[] d,
                     int ca, int cb, int cc, int cd, float[] inside) {
        // normal from the diagonals (works for triangles and slightly bent quads)
        float ux = c[0] - a[0], uy = c[1] - a[1], uz = c[2] - a[2];
        float wx = d[0] - b[0], wy = d[1] - b[1], wz = d[2] - b[2];
        if (d == c || (wx == 0 && wy == 0 && wz == 0)) {
            wx = b[0] - a[0];
            wy = b[1] - a[1];
            wz = b[2] - a[2];
            float tx = ux, ty = uy, tz = uz;
            ux = wx;
            uy = wy;
            uz = wz;
            wx = tx;
            wy = ty;
            wz = tz;
        }
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        if (inside != null) {
            float fx = (a[0] + b[0] + c[0] + d[0]) / 4 - inside[0], fy = (a[1] + b[1] + c[1] + d[1]) / 4 - inside[1],
                    fz = (a[2] + b[2] + c[2] + d[2]) / 4 - inside[2];
            if (nx * fx + ny * fy + nz * fz < 0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
        }
        float[] n = e.dir(nx, ny, nz);
        float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (len < 1e-6f) return;                                   // a degenerate sliver: nothing to draw
        n[0] /= len;
        n[1] /= len;
        n[2] /= len;
        float[] p = new float[12];
        e.apply(a[0], a[1], a[2], p, 0);
        e.apply(b[0], b[1], b[2], p, 3);
        e.apply(c[0], c[1], c[2], p, 6);
        e.apply(d[0], d[1], d[2], p, 9);
        int[] cols = {lit(ca, n[0], n[1], n[2]), lit(cb, n[0], n[1], n[2]), lit(cc, n[0], n[1], n[2]), lit(cd, n[0], n[1], n[2])};
        if (ca == cb && cb == cc && cc == cd) o.quad(p, n[0], n[1], n[2], cols[0], light);
        else o.quad4(p, n[0], n[1], n[2], cols, light);
    }

    static void tri(M e, Out o, int light, float[] a, float[] b, float[] c, int ca, int cb, int cc, float[] inside) {
        face(e, o, light, a, b, c, c, ca, cb, cc, cc, inside);
    }

    /**
     * A solid between two four-cornered rings (a box that can taper, lean or twist): ring A
     * coloured {@code cA}, ring B {@code cB}, the sides blending between them.
     */
    static void hull(M e, Out o, int light, float[][] ra, float[][] rb, int cA, int cB) {
        float[] in = new float[3];
        for (int i = 0; i < 4; i++) for (int k = 0; k < 3; k++) in[k] += (ra[i][k] + rb[i][k]) / 8f;
        face(e, o, light, ra[0], ra[1], ra[2], ra[3], cA, cA, cA, cA, in);
        face(e, o, light, rb[0], rb[1], rb[2], rb[3], cB, cB, cB, cB, in);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            face(e, o, light, ra[i], ra[j], rb[j], rb[i], cA, cA, cB, cB, in);
        }
    }

    /**
     * A tapered block standing along y, centred on (x, z): half-sizes hx0/hz0 at y0 and hx1/hz1
     * at y1 (0 for a point), colour c0 at y0 fading to c1 at y1.
     */
    static void taper(M e, Out o, int light, float x, float z, float y0, float y1,
                      float hx0, float hz0, float hx1, float hz1, int c0, int c1) {
        float[][] a = {v(x - hx0, y0, z - hz0), v(x + hx0, y0, z - hz0), v(x + hx0, y0, z + hz0), v(x - hx0, y0, z + hz0)};
        float[][] b = {v(x - hx1, y1, z - hz1), v(x + hx1, y1, z - hz1), v(x + hx1, y1, z + hz1), v(x - hx1, y1, z + hz1)};
        hull(e, o, light, a, b, c0, c1);
    }

    /** A box with one colour on top fading to another at the bottom (y0 → y1). */
    static void gbox(M e, Out o, int light, int cTop, int cBottom, float x0, float y0, float z0, float x1, float y1, float z1) {
        float hx = (x1 - x0) / 2, hz = (z1 - z0) / 2;
        taper(e, o, light, (x0 + x1) / 2, (z0 + z1) / 2, y0, y1, hx, hz, hx, hz, cTop, cBottom);
    }

    /**
     * A tube along a path of points with a radius (two, for flattened ones) and colour at each
     * point; {@code sides} around (4 = diamond, 6-8 = round). {@code up} picks which way the
     * first radius points. Radius 0 at the end makes a point. Caps close blunt ends.
     */
    static void tube(M e, Out o, int light, float[][] pts, float[] ra, float[] rb, int[] cols, int sides, float[] up, float phase) {
        tube(e, o, light, pts, ra, rb, cols, sides, up, phase, true);
    }

    /** As above; {@code caps} false leaves the ends open (when they're hidden anyway: fewer faces). */
    static void tube(M e, Out o, int light, float[][] pts, float[] ra, float[] rb, int[] cols, int sides, float[] up, float phase, boolean caps) {
        int n = pts.length;
        if (n < 2) return;
        float[][] tan = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] p0 = pts[Math.max(0, i - 1)], p1 = pts[Math.min(n - 1, i + 1)];
            tan[i] = norm(new float[]{p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]});
        }
        // frame carried along the path (no twisting)
        float[] nn = sub(up, scale(tan[0], dot(up, tan[0])));
        if (len(nn) < 1e-4f) nn = Math.abs(tan[0][1]) < 0.9f ? new float[]{0, -1, 0} : new float[]{1, 0, 0};
        nn = norm(sub(nn, scale(tan[0], dot(nn, tan[0]))));
        float[][][] ring = new float[n][sides][];
        for (int i = 0; i < n; i++) {
            if (i > 0) nn = norm(sub(nn, scale(tan[i], dot(nn, tan[i]))));
            float[] bn = cross(tan[i], nn);
            for (int j = 0; j < sides; j++) {
                double th = Math.PI * 2 * j / sides + phase;
                float c = (float) Math.cos(th), s = (float) Math.sin(th);
                ring[i][j] = new float[]{pts[i][0] + nn[0] * ra[i] * c + bn[0] * rb[i] * s,
                        pts[i][1] + nn[1] * ra[i] * c + bn[1] * rb[i] * s,
                        pts[i][2] + nn[2] * ra[i] * c + bn[2] * rb[i] * s};
            }
        }
        for (int i = 0; i < n - 1; i++) {
            float[] mid = {(pts[i][0] + pts[i + 1][0]) / 2, (pts[i][1] + pts[i + 1][1]) / 2, (pts[i][2] + pts[i + 1][2]) / 2};
            for (int j = 0; j < sides; j++) {
                int k = (j + 1) % sides;
                face(e, o, light, ring[i][j], ring[i][k], ring[i + 1][k], ring[i + 1][j], cols[i], cols[i], cols[i + 1], cols[i + 1], mid);
            }
        }
        if (!caps) return;
        if (ra[0] > 0.01f || rb[0] > 0.01f) cap(e, o, light, pts[0], ring[0], cols[0], tan[0], 1);
        if (ra[n - 1] > 0.01f || rb[n - 1] > 0.01f) cap(e, o, light, pts[n - 1], ring[n - 1], cols[n - 1], tan[n - 1], -1);
    }

    private static void cap(M e, Out o, int light, float[] c, float[][] ring, int col, float[] t, int dir) {
        float[] in = {c[0] + t[0] * dir * 0.05f, c[1] + t[1] * dir * 0.05f, c[2] + t[2] * dir * 0.05f};
        for (int j = 0; j < ring.length; j++) tri(e, o, light, c, ring[j], ring[(j + 1) % ring.length], col, col, col, in);
    }

    /** Same radius both ways, a colour per point. */
    static void tube(M e, Out o, int light, float[][] pts, float[] r, int[] cols, int sides) {
        tube(e, o, light, pts, r, r, cols, sides, new float[]{0, -1, 0}, (float) (Math.PI / sides));
    }

    /**
     * An ellipsoid (or its top half with {@code half}) centred on (cx, cy, cz), radii rx / ry / rz,
     * coloured cTop at the top fading to cBottom.
     */
    static void dome(M e, Out o, int light, float cx, float cy, float cz, float rx, float ry, float rz,
                     int lat, int lon, int cTop, int cBottom, boolean half) {
        float[] in = {cx, cy + (half ? -ry * 0.3f : 0), cz};
        double maxT = half ? Math.PI / 2 : Math.PI;
        for (int i = 0; i < lat; i++) {
            double t0 = maxT * i / lat, t1 = maxT * (i + 1) / lat;
            int c0 = mix(cTop, cBottom, (float) (t0 / maxT)), c1 = mix(cTop, cBottom, (float) (t1 / maxT));
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                float[] a = sph(cx, cy, cz, rx, ry, rz, t0, p0), b = sph(cx, cy, cz, rx, ry, rz, t0, p1);
                float[] c = sph(cx, cy, cz, rx, ry, rz, t1, p1), d = sph(cx, cy, cz, rx, ry, rz, t1, p0);
                if (i == 0) tri(e, o, light, a, c, d, c0, c1, c1, in);
                else face(e, o, light, a, b, c, d, c0, c0, c1, c1, in);
            }
        }
        if (half) {                                                   // close the flat bottom
            float[] mid = {cx, cy, cz};
            for (int j = 0; j < lon; j++) {
                float[] a = sph(cx, cy, cz, rx, ry, rz, maxT, Math.PI * 2 * j / lon), b = sph(cx, cy, cz, rx, ry, rz, maxT, Math.PI * 2 * (j + 1) / lon);
                tri(e, o, light, mid, b, a, cBottom, cBottom, cBottom, new float[]{cx, cy - 0.1f, cz});
            }
        }
    }

    private static float[] sph(float cx, float cy, float cz, float rx, float ry, float rz, double t, double p) {
        return new float[]{cx + rx * (float) (Math.sin(t) * Math.cos(p)), cy - ry * (float) Math.cos(t), cz + rz * (float) (Math.sin(t) * Math.sin(p))};
    }

    /**
     * A ring (torus) lying flat round the y axis at height y: radius R, tube radius r; the
     * outer edge {@code edge}, the inner {@code inner} (a shine across the band).
     */
    static void ring(M e, Out o, int light, float cx, float y, float cz, float R, float r, int n, int sides, int edge, int inner) {
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            for (int j = 0; j < sides; j++) {
                double b0 = Math.PI * 2 * j / sides, b1 = Math.PI * 2 * (j + 1) / sides;
                float[] p00 = tor(cx, y, cz, R, r, a0, b0), p01 = tor(cx, y, cz, R, r, a0, b1);
                float[] p11 = tor(cx, y, cz, R, r, a1, b1), p10 = tor(cx, y, cz, R, r, a1, b0);
                int c0 = mix(inner, edge, (float) (0.5 + 0.5 * Math.cos(b0))), c1 = mix(inner, edge, (float) (0.5 + 0.5 * Math.cos(b1)));
                float[] in0 = {cx + R * (float) Math.cos((a0 + a1) / 2), y, cz + R * (float) Math.sin((a0 + a1) / 2)};
                face(e, o, light, p00, p01, p11, p10, c0, c1, c1, c0, in0);
            }
        }
    }

    private static float[] tor(float cx, float y, float cz, float R, float r, double a, double b) {
        float rr = R + r * (float) Math.cos(b);
        return new float[]{cx + rr * (float) Math.cos(a), y - r * (float) Math.sin(b), cz + rr * (float) Math.sin(a)};
    }

    /**
     * A cut gem: a crown (top point at -h), girdle of {@code sides} corners at radius r, and a
     * pavilion down to +h*1.3. Light top, deep colour below, a white glint on one facet.
     */
    static void gem(M e, Out o, int light, float r, float h, int sides, int c) {
        float[] top = v(0, -h, 0), bot = v(0, h * 1.3f, 0), in = v(0, 0, 0);
        int hi = mix(c, 0xFFFFFFFF, 0.55f), deep = CosmeticModels.shade(c, 0.6f);
        float[] tableR = new float[sides];
        for (int j = 0; j < sides; j++) {
            double a0 = Math.PI * 2 * j / sides, a1 = Math.PI * 2 * (j + 1) / sides;
            float[] g0 = v(r * (float) Math.cos(a0), 0, r * (float) Math.sin(a0)), g1 = v(r * (float) Math.cos(a1), 0, r * (float) Math.sin(a1));
            tri(e, o, light, top, g0, g1, j == 1 ? 0xFFFFFFFF : hi, c, c, in);
            tri(e, o, light, bot, g1, g0, deep, c, c, in);
        }
    }

    // ---- small vector maths

    static float dot(float[] a, float[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    static float[] sub(float[] a, float[] b) { return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

    static float[] scale(float[] a, float k) { return new float[]{a[0] * k, a[1] * k, a[2] * k}; }

    static float len(float[] a) { return (float) Math.sqrt(dot(a, a)); }

    static float[] norm(float[] a) {
        float l = len(a);
        return l < 1e-6f ? new float[]{0, 1, 0} : scale(a, 1 / l);
    }

    static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** Points along a quadratic curve from a through control point c to b (n points). */
    static float[][] curve(float[] a, float[] c, float[] b, int n) {
        float[][] out = new float[n][];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1), u = 1 - t;
            out[i] = new float[]{u * u * a[0] + 2 * u * t * c[0] + t * t * b[0], u * u * a[1] + 2 * u * t * c[1] + t * t * b[1],
                    u * u * a[2] + 2 * u * t * c[2] + t * t * b[2]};
        }
        return out;
    }

    /** n values going from a to b (with an ease: power k, 1 = straight). */
    static float[] ramp(float a, float b, int n, float k) {
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = a + (b - a) * (float) Math.pow(i / (float) (n - 1), k);
        return out;
    }

    static int[] ramp(int a, int b, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = mix(a, b, i / (float) (n - 1));
        return out;
    }
}

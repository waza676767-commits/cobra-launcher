package dev.cobra.launcher.ui;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** One 60 fps timer drives every tween. With animations off, tweens jump straight to their target. */
public final class Anim {
    public static volatile boolean enabled = true;
    private static final List<Tween> ACTIVE = new CopyOnWriteArrayList<>();
    private static final Timer TIMER = new Timer(15, e -> tick());

    private Anim() {}

    private static void tick() {
        long now = System.nanoTime();
        for (Tween t : ACTIVE) t.step(now);
        // push the last frame to the screen now (X11 queues drawing): steadier motion, no bunching
        Toolkit.getDefaultToolkit().sync();
        if (ACTIVE.isEmpty()) TIMER.stop();
    }

    /**
     * The curve iOS uses for most of its transitions (a smooth, confident ease-out): fast at the
     * start, gently settling at the end. Approximates cubic-bezier(0.32, 0.72, 0, 1).
     */
    public static double iosEase(double p) {
        if (p <= 0) return 0;
        if (p >= 1) return 1;
        // solve the bezier for t by a few Newton steps, then evaluate y
        double x1 = 0.32, y1 = 0.72, x2 = 0, y2 = 1, t = p;
        for (int i = 0; i < 6; i++) {
            double x = bez(t, x1, x2) - p, dx = bezD(t, x1, x2);
            if (Math.abs(dx) < 1e-6) break;
            t -= x / dx;
            t = Math.max(0, Math.min(1, t));
        }
        return bez(t, y1, y2);
    }

    private static double bez(double t, double a, double b) {
        double u = 1 - t;
        return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t;
    }

    private static double bezD(double t, double a, double b) {
        double u = 1 - t;
        return 3 * u * u * a + 6 * u * t * (b - a) + 3 * t * t * (1 - b);
    }

    /** A spring toward a target (iOS-like), or a timed iOS ease when {@link #over(double, long)} is used. */
    public static final class Tween {
        private final Component owner;
        private double value, target, from;
        private double velocity;                 // for the spring
        private double rate = 14; // per second
        private long startNs, durationNs;
        private Runnable onDone;

        public Tween(Component owner, double initial) {
            this.owner = owner;
            this.value = this.target = initial;
        }

        public double get() { return value; }

        public double target() { return target; }

        public Tween rate(double perSecond) {
            this.rate = perSecond;
            return this;
        }

        public void to(double t) {
            durationNs = 0;
            start(t, null);
        }

        public void to(double t, Runnable done) {
            durationNs = 0;
            start(t, done);
        }

        /** Timed cubic ease from the current value. */
        public void over(double t, long millis, Runnable done) {
            durationNs = millis * 1_000_000L;
            start(t, done);
        }

        public void set(double v) {
            value = target = v;
            velocity = 0;
            ACTIVE.remove(this);
            repaint();
        }

        private void start(double t, Runnable done) {
            target = t;
            from = value;
            onDone = done;
            startNs = System.nanoTime();
            lastNs = startNs;
            if (!enabled || (Math.abs(target - value) < 1e-4 && durationNs == 0)) {
                value = target;
                repaint();
                ACTIVE.remove(this);
                finish();
                return;
            }
            if (!ACTIVE.contains(this)) ACTIVE.add(this);
            if (!TIMER.isRunning()) TIMER.start();
        }

        private long lastNs;

        private void step(long now) {
            boolean done;
            if (durationNs > 0) {
                double p = Math.min(1, (now - startNs) / (double) durationNs);
                double e = iosEase(p);
                value = from + (target - from) * e;
                done = p >= 1;
            } else {
                // iOS-style spring: a quick start, a soft landing with a hint of overshoot. Stiffness
                // comes from the old rate so every animation keeps its speed; damping 0.82 of critical.
                double dt = Math.min(0.05, (now - lastNs) / 1e9);
                double omega = rate * 0.9, zeta = 0.82;
                int steps = Math.max(1, (int) Math.ceil(dt / 0.004));  // small steps: stable at any frame rate
                double h = dt / steps;
                for (int i = 0; i < steps; i++) {
                    double accel = omega * omega * (target - value) - 2 * zeta * omega * velocity;
                    velocity += accel * h;
                    value += velocity * h;
                }
                double span = Math.max(1e-6, Math.abs(target - from));
                done = Math.abs(target - value) < 0.0015 * Math.max(1, span) && Math.abs(velocity) < 0.01 * Math.max(1, span);
            }
            lastNs = now;
            if (done) {
                value = target;
                velocity = 0;
                ACTIVE.remove(this);
            }
            repaint();
            if (done) finish();
        }

        private void finish() {
            Runnable r = onDone;
            onDone = null;
            if (r != null) r.run();
        }

        private void repaint() {
            if (owner != null) owner.repaint();
        }
    }
}

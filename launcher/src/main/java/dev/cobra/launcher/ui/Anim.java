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
        if (ACTIVE.isEmpty()) TIMER.stop();
    }

    /** Exponential ease toward a target, or a timed ease when {@link #over(double, long)} is used. */
    public static final class Tween {
        private final Component owner;
        private double value, target, from;
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
                double e = p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
                value = from + (target - from) * e;
                done = p >= 1;
            } else {
                double dt = (now - lastNs) / 1e9;
                value += (target - value) * (1 - Math.exp(-rate * dt));
                done = Math.abs(target - value) < 0.002;
            }
            lastNs = now;
            if (done) {
                value = target;
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

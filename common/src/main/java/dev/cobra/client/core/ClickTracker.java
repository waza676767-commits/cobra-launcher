package dev.cobra.client.core;

import java.util.ArrayDeque;

public final class ClickTracker {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<Long>(), RIGHT = new ArrayDeque<Long>();

    private ClickTracker() {}

    public static synchronized void click(int button) {
        if (button == 0) LEFT.addLast(System.currentTimeMillis());
        else if (button == 1) RIGHT.addLast(System.currentTimeMillis());
    }

    public static synchronized int cps(int button) {
        ArrayDeque<Long> q = button == 0 ? LEFT : RIGHT;
        long cut = System.currentTimeMillis() - 1000;
        while (!q.isEmpty() && q.peekFirst() < cut) q.pollFirst();
        return q.size();
    }
}

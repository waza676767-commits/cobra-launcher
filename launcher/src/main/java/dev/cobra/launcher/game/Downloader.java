package dev.cobra.launcher.game;

import dev.cobra.launcher.core.Http;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class Downloader {
    private Downloader() {}

    public interface Progress {
        void update(String status, double fraction);
    }

    public record Item(List<String> urls, Path path, String sha1, long size) {
        public Item(String url, Path path, String sha1, long size) {
            this(List.of(url), path, sha1, size);
        }

        boolean present() {
            try {
                return Files.isRegularFile(path) && (size <= 0 || Files.size(path) == size);
            } catch (IOException e) {
                return false;
            }
        }
    }

    public static void run(String label, Collection<Item> items, Progress p) throws IOException {
        Map<Path, Item> unique = new LinkedHashMap<>();
        for (Item i : items) unique.putIfAbsent(i.path(), i);
        List<Item> todo = new ArrayList<>();
        for (Item i : unique.values()) if (!i.present()) todo.add(i);
        if (todo.isEmpty()) return;

        int total = todo.size();
        AtomicInteger done = new AtomicInteger();
        AtomicReference<IOException> error = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(12, total), r -> {
            Thread t = new Thread(r, "cobra-download");
            t.setDaemon(true);
            return t;
        });
        p.update(label, 0);
        for (Item item : todo) {
            pool.submit(() -> {
                if (error.get() != null) return;
                IOException last = null;
                for (String url : item.urls()) {
                    try {
                        Http.download(url, item.path(), item.sha1());
                        last = null;
                        break;
                    } catch (IOException e) {
                        last = e;
                    }
                }
                if (last != null) error.compareAndSet(null, last);
                int d = done.incrementAndGet();
                p.update(label + "  " + d + "/" + total, d / (double) total);
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(2, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            pool.shutdownNow();
            throw new IOException("Download interrupted");
        }
        if (error.get() != null) throw error.get();
    }
}

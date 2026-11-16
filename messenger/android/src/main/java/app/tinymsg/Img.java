package app.tinymsg;

import android.content.Context;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal image/GIF loader: memory LRU + disk cache, animated GIF/WebP via ImageDecoder. */
final class Img {
    private static final LruCache<String, Drawable> mem = new LruCache<>(48);
    private static final ExecutorService pool = Executors.newFixedThreadPool(3);
    private static File dir;
    private static int maxPx;

    static void init(Context c) {
        dir = new File(c.getCacheDir(), "img");
        dir.mkdirs();
        maxPx = Math.min(c.getResources().getDisplayMetrics().widthPixels, 1080);
    }

    /** key: either a server media id or an absolute http(s) URL. */
    static void bind(ImageView iv, String key) {
        iv.setTag(key);
        Drawable d = mem.get(key);
        if (d != null) { show(iv, d); return; }
        iv.setImageDrawable(null);
        iv.setVisibility(View.VISIBLE);
        pool.execute(() -> {
            Drawable r = null;
            try { r = decode(load(key)); } catch (Exception ignored) {}
            final Drawable fd = r;
            if (fd != null) mem.put(key, fd);
            Api.ui.post(() -> {
                if (!key.equals(iv.getTag())) return;
                if (fd == null) iv.setVisibility(View.GONE); else show(iv, fd);
            });
        });
    }

    private static void show(ImageView iv, Drawable d) {
        iv.setVisibility(View.VISIBLE);
        iv.setImageDrawable(d);
        if (d instanceof Animatable) ((Animatable) d).start();
    }

    private static byte[] load(String key) throws IOException {
        File f = new File(dir, key.startsWith("http") ? sha1(key) : key);
        if (f.exists()) return Files.readAllBytes(f.toPath());
        byte[] b;
        if (key.startsWith("http")) {
            HttpURLConnection c = (HttpURLConnection) new URL(key).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() >= 400) throw new IOException("http");
            String t = c.getContentType();
            if (t != null && !t.startsWith("image/")) throw new IOException("not image");
            if (c.getContentLengthLong() > 15_000_000) throw new IOException("too big");
            b = Api.readAll(c.getInputStream());
        } else {
            b = Api.req(Api.server, "GET", "/m/" + key, null, null, 30000);
        }
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(b); }
        return b;
    }

    private static String sha1(String s) {
        try {
            StringBuilder sb = new StringBuilder("u");
            for (byte x : java.security.MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8")))
                sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { return "u" + Integer.toHexString(s.hashCode()); }
    }

    static Drawable decode(byte[] b) throws IOException {
        return ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(b)), (dec, info, src) -> {
            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
            if (w > maxPx) dec.setTargetSize(maxPx, Math.max(1, h * maxPx / w));
        });
    }

    /** Text that is just an image/gif URL gets an inline preview. */
    static boolean isImageUrl(String s) {
        if (s == null || s.length() > 2000 || s.indexOf(' ') >= 0 || s.indexOf('\n') >= 0) return false;
        if (!s.startsWith("http://") && !s.startsWith("https://")) return false;
        String l = s.toLowerCase();
        int q = l.indexOf('?');
        if (q > 0) l = l.substring(0, q);
        return l.endsWith(".gif") || l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".webp");
    }

    private Img() {}
}

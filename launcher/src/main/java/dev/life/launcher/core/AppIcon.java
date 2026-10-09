package dev.life.launcher.core;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Your own launcher icon/logo: any picture, cropped to a centred square and stored at 512 px.
 * Used for the window/taskbar icon, the app-menu icon and the Life logo inside the launcher.
 * Reset brings the Life star back.
 */
public final class AppIcon {
    private static final Path FILE = Paths.ROOT.resolve("app-icon.png");
    private static final int[] SIZES = {16, 32, 48, 64, 128, 256, 512};
    private static BufferedImage custom;
    private static boolean loaded;
    private static int version;

    private AppIcon() {}

    /** Bumped on every change so cached renders of the logo are rebuilt. */
    public static int version() { return version; }

    /** The custom icon (512×512), or null for the default Life logo. */
    public static synchronized BufferedImage custom() {
        if (!loaded) {
            loaded = true;
            try {
                if (Files.exists(FILE)) custom = ImageIO.read(FILE.toFile());
                if (custom != null && custom.getWidth() != custom.getHeight()) {   // hand-placed file: square it
                    int side = Math.min(custom.getWidth(), custom.getHeight());
                    custom = scale(custom.getSubimage((custom.getWidth() - side) / 2, (custom.getHeight() - side) / 2, side, side), 512);
                }
            } catch (IOException e) {
                custom = null;
            }
        }
        return custom;
    }

    public static synchronized void set(Path src) throws IOException {
        BufferedImage img = ImageIO.read(src.toFile());
        if (img == null) throw new IOException("That file isn't a PNG, JPG, GIF or BMP image.");
        int side = Math.min(img.getWidth(), img.getHeight());
        BufferedImage square = img.getSubimage((img.getWidth() - side) / 2, (img.getHeight() - side) / 2, side, side);
        BufferedImage out = scale(square, 512);
        Files.createDirectories(FILE.getParent());
        ImageIO.write(out, "png", FILE.toFile());
        custom = out;
        loaded = true;
        version++;
        DesktopIntegration.writeIcons();
    }

    public static synchronized void reset() throws IOException {
        Files.deleteIfExists(FILE);
        custom = null;
        loaded = true;
        version++;
        DesktopIntegration.writeIcons();
    }

    /** Window / taskbar icons in every size (custom or the bundled ones). */
    public static List<Image> windowIcons() {
        List<Image> out = new ArrayList<>();
        for (int s : SIZES) {
            BufferedImage i = icon(s);
            if (i != null) out.add(i);
        }
        return out;
    }

    /** One icon size: the custom picture with rounded corners, or the bundled Life icon. */
    public static BufferedImage icon(int size) {
        BufferedImage c = custom();
        if (c != null) return rounded(scale(c, size));
        try (InputStream in = AppIcon.class.getResourceAsStream("/img/icon_" + size + ".png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    /** Stepwise downscale (halves, then one bicubic step): sharp small icons from a big picture. */
    static BufferedImage scale(BufferedImage src, int size) {
        BufferedImage cur = src;
        while (cur.getWidth() / 2 >= size) {
            BufferedImage half = new BufferedImage(cur.getWidth() / 2, cur.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = half.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(cur, 0, 0, half.getWidth(), half.getHeight(), null);
            g.dispose();
            cur = half;
        }
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(cur, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    /** App-style rounded corners (22% radius). */
    public static BufferedImage rounded(BufferedImage img) {
        int s = img.getWidth();
        BufferedImage out = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(new RoundRectangle2D.Double(0, 0, s, s, s * 0.44, s * 0.44));
        g.setComposite(AlphaComposite.SrcIn);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }
}

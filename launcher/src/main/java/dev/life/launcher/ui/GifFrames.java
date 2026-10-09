package dev.life.launcher.ui;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/** Pure-Java GIF → frame sequence (used when ffmpeg isn't installed). */
final class GifFrames {
    private GifFrames() {}

    static void explode(Path gif, Path outDir, int fps, int w, int h) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(gif.toFile())) {
            Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
            if (!it.hasNext()) throw new IOException("No GIF reader available");
            ImageReader reader = it.next();
            reader.setInput(in, false);
            int count = reader.getNumImages(true);
            BufferedImage canvas = null;
            int written = 0;
            for (int i = 0; i < count && written < fps * 30; i++) {
                BufferedImage frame = reader.read(i);
                IIOMetadata meta = reader.getImageMetadata(i);
                Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
                int left = 0, top = 0, delay = 10;
                String disposal = "none";
                for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
                    NamedNodeMap a = n.getAttributes();
                    if (n.getNodeName().equals("ImageDescriptor")) {
                        left = Integer.parseInt(a.getNamedItem("imageLeftPosition").getNodeValue());
                        top = Integer.parseInt(a.getNamedItem("imageTopPosition").getNodeValue());
                    } else if (n.getNodeName().equals("GraphicControlExtension")) {
                        delay = Integer.parseInt(a.getNamedItem("delayTime").getNodeValue());
                        disposal = a.getNamedItem("disposalMethod").getNodeValue();
                    }
                }
                if (canvas == null) {
                    int cw = Math.max(frame.getWidth() + left, reader.getWidth(0));
                    int ch = Math.max(frame.getHeight() + top, reader.getHeight(0));
                    canvas = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
                }
                Graphics2D g = canvas.createGraphics();
                g.drawImage(frame, left, top, null);
                g.dispose();

                BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D o = out.createGraphics();
                o.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                double scale = Math.max(w / (double) canvas.getWidth(), h / (double) canvas.getHeight());
                int dw = (int) Math.ceil(canvas.getWidth() * scale), dh = (int) Math.ceil(canvas.getHeight() * scale);
                o.drawImage(canvas, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
                o.dispose();
                int repeats = Math.max(1, Math.round(Math.max(2, delay) * fps / 100f));
                for (int r = 0; r < repeats; r++) {
                    written++;
                    ImageIO.write(out, "jpg", outDir.resolve(String.format("%05d.jpg", written)).toFile());
                }
                if ("restoreToBackgroundColor".equals(disposal)) {
                    Graphics2D c = canvas.createGraphics();
                    c.setComposite(AlphaComposite.Clear);
                    c.fillRect(left, top, frame.getWidth(), frame.getHeight());
                    c.dispose();
                }
            }
            reader.dispose();
            if (written == 0) throw new IOException("That GIF has no frames");
        }
    }
}

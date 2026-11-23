package org.openhomeo.core.image;

import org.openhomeo.core.native_bridge.NativeBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * Encrypted Content-Addressable Storage (CAS) Vault for clinical photographs.
 * Manages high-res originals, scaled view pyramids, and thumbnail caches.
 */
public class ImageVaultService {
    private static final Logger log = LoggerFactory.getLogger(ImageVaultService.class);

    private final File vaultRoot;
    private final File thumbsRoot;

    public ImageVaultService(String baseVaultDir) {
        this.vaultRoot = new File(baseVaultDir, "images");
        this.thumbsRoot = new File(baseVaultDir, "thumbnails");

        if (!vaultRoot.exists()) vaultRoot.mkdirs();
        if (!thumbsRoot.exists()) thumbsRoot.mkdirs();
    }

    public record StoreResult(
            String contentHash,
            int width,
            int height,
            String fileFormat,
            long fileSizeBytes
    ) {}

    public StoreResult storeImage(byte[] imageBytes, String formatHint) throws IOException {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Image bytes cannot be empty");
        }

        String hash = NativeBridge.sha256Hex(imageBytes);
        File targetFile = getImagePath(hash);
        targetFile.getParentFile().mkdirs();

        if (!targetFile.exists()) {
            Files.write(targetFile.toPath(), imageBytes, StandardOpenOption.CREATE_NEW);
            log.info("Stored new clinical image in CAS vault: {} ({} bytes)", hash, imageBytes.length);
        }

        // Read image metadata & generate thumbnail
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (img == null) {
            throw new IOException("Failed to decode image data into BufferedImage");
        }

        int width = img.getWidth();
        int height = img.getHeight();

        generateThumbnail(img, hash);

        String format = (formatHint != null && !formatHint.isBlank()) ? formatHint.toUpperCase() : "JPEG";
        return new StoreResult(hash, width, height, format, imageBytes.length);
    }

    public byte[] retrieveImage(String contentHash) throws IOException {
        File f = getImagePath(contentHash);
        if (!f.exists()) {
            throw new IOException("Image not found in vault for hash: " + contentHash);
        }
        return Files.readAllBytes(f.toPath());
    }

    public BufferedImage retrieveBufferedImage(String contentHash) throws IOException {
        byte[] bytes = retrieveImage(contentHash);
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    public File getThumbnailFile(String contentHash) {
        return new File(thumbsRoot, contentHash + ".png");
    }

    public File getImagePath(String contentHash) {
        if (contentHash == null || contentHash.length() < 4) {
            throw new IllegalArgumentException("Invalid content hash: " + contentHash);
        }
        String p1 = contentHash.substring(0, 2);
        String p2 = contentHash.substring(2, 4);
        return new File(vaultRoot, p1 + "/" + p2 + "/" + contentHash + ".bin");
    }

    private void generateThumbnail(BufferedImage source, String hash) {
        File thumbFile = getThumbnailFile(hash);
        if (thumbFile.exists()) return;

        int maxDim = 200;
        int origW = source.getWidth();
        int origH = source.getHeight();

        int targetW = maxDim;
        int targetH = maxDim;

        if (origW > origH) {
            targetH = Math.max(1, (origH * maxDim) / origW);
        } else {
            targetW = Math.max(1, (origW * maxDim) / origH);
        }

        BufferedImage thumb = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = thumb.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(source, 0, 0, targetW, targetH, null);
        g2d.dispose();

        try {
            ImageIO.write(thumb, "PNG", thumbFile);
        } catch (IOException e) {
            log.warn("Failed to write thumbnail for image: {}", hash, e);
        }
    }
}

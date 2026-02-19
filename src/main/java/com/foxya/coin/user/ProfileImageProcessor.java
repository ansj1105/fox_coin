package com.foxya.coin.user;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Set;

@Slf4j
public class ProfileImageProcessor {

    public static final int MIN_LEVEL_TO_UPLOAD = 2;
    public static final long MAX_UPLOAD_BYTES = 5L * 1024L * 1024L; // 5MB
    public static final int RECOMMENDED_IMAGE_SIZE = 512;
    public static final String VARIANT_THUMB = "thumb";
    public static final String VARIANT_RANK = "rank";
    public static final String VARIANT_PROFILE = "profile";
    public static final Set<String> SUPPORTED_MIME_TYPES = Set.of("image/png", "image/jpeg", "image/jpg");
    public static final int THUMB_SIZE = 64;
    public static final int RANK_SIZE = 128;
    public static final int PROFILE_SIZE = 512;

    private final Path rootDir;

    public ProfileImageProcessor(String rootDir) {
        this.rootDir = Path.of(rootDir);
    }

    public void ensureRootDirectory() throws IOException {
        Files.createDirectories(rootDir);
    }

    public String normalizeVariant(String variant) {
        if (variant == null || variant.isBlank()) {
            return null;
        }
        String normalized = variant.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains(".")) {
            normalized = normalized.substring(0, normalized.indexOf('.'));
        }
        return switch (normalized) {
            case VARIANT_THUMB, VARIANT_RANK, VARIANT_PROFILE -> normalized;
            default -> null;
        };
    }

    public static String buildVariantUrl(Long userId, String variant, long version) {
        return "/api/v1/users/profile-images/" + userId + "/" + variant + "?v=" + version;
    }

    public static String toRankVariantUrl(String profileVariantUrl) {
        return toVariantUrl(profileVariantUrl, VARIANT_RANK);
    }

    public static String toVariantUrl(String profileVariantUrl, String targetVariant) {
        if (profileVariantUrl == null || profileVariantUrl.isBlank()) {
            return null;
        }
        if (targetVariant == null || targetVariant.isBlank()) {
            return profileVariantUrl;
        }
        return profileVariantUrl.replace("/" + VARIANT_PROFILE, "/" + targetVariant);
    }

    public Path resolveVariantPath(Long userId, String variant) {
        return getUserDirectory(userId).resolve(variant + ".png");
    }

    public void hardDeleteUserImages(Long userId) throws IOException {
        Path userDir = getUserDirectory(userId);
        if (!Files.exists(userDir)) {
            return;
        }
        Files.walkFileTree(userDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public long processAndStore(Long userId, Path uploadedTempFile, String contentType, String originalFileName) throws IOException {
        validateFormat(contentType, originalFileName);
        ensureRootDirectory();
        try {
            Path userDir = getUserDirectory(userId);
            Files.createDirectories(userDir);

            BufferedImage source = ImageIO.read(uploadedTempFile.toFile());
            if (source == null) {
                throw new IOException("Unsupported image content.");
            }

            BufferedImage square = cropCenterSquare(source);
            writeVariant(square, userDir.resolve(VARIANT_THUMB + ".png"), THUMB_SIZE);
            writeVariant(square, userDir.resolve(VARIANT_RANK + ".png"), RANK_SIZE);
            writeVariant(square, userDir.resolve(VARIANT_PROFILE + ".png"), PROFILE_SIZE);

            return System.currentTimeMillis();
        } finally {
            cleanupTempFile(uploadedTempFile);
        }
    }

    private void validateFormat(String contentType, String originalFileName) throws IOException {
        String normalizedType = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        if (SUPPORTED_MIME_TYPES.contains(normalizedType)) {
            return;
        }
        if (originalFileName != null) {
            String lower = originalFileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                return;
            }
        }
        throw new IOException("Only PNG/JPG images are allowed.");
    }

    private Path getUserDirectory(Long userId) {
        return rootDir.resolve(String.valueOf(userId));
    }

    private void writeVariant(BufferedImage sourceSquare, Path outputPath, int targetSize) throws IOException {
        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(sourceSquare, 0, 0, targetSize, targetSize, null);
        graphics.dispose();
        ImageIO.write(resized, "png", outputPath.toFile());
    }

    private BufferedImage cropCenterSquare(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int side = Math.min(width, height);
        int x = (width - side) / 2;
        int y = (height - side) / 2;
        return source.getSubimage(x, y, side, side);
    }

    private void cleanupTempFile(Path uploadedTempFile) {
        try {
            if (Files.exists(uploadedTempFile)) {
                Files.deleteIfExists(uploadedTempFile);
            }
        } catch (Exception e) {
            log.debug("Failed to cleanup uploaded temp file: {}", uploadedTempFile, e);
        }
    }
}

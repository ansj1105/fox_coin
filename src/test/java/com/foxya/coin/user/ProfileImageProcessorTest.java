package com.foxya.coin.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileImageProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void processAndStore_createsAllVariants_andRemovesTempFile() throws Exception {
        ProfileImageProcessor processor = new ProfileImageProcessor(tempDir.resolve("profiles").toString());
        Path sourceFile = tempDir.resolve("source.jpg");
        writeImage(sourceFile, 1000, 600, "jpg");

        long version = processor.processAndStore(39L, sourceFile, "image/jpeg", "source.jpg");

        assertThat(version).isPositive();
        assertThat(Files.exists(sourceFile)).isFalse();

        assertVariantSize(processor.resolveVariantPath(39L, ProfileImageProcessor.VARIANT_THUMB), 64);
        assertVariantSize(processor.resolveVariantPath(39L, ProfileImageProcessor.VARIANT_RANK), 128);
        assertVariantSize(processor.resolveVariantPath(39L, ProfileImageProcessor.VARIANT_PROFILE), 512);
    }

    @Test
    void normalizeVariant_acceptsSupportedValues() {
        ProfileImageProcessor processor = new ProfileImageProcessor(tempDir.toString());

        assertThat(processor.normalizeVariant("thumb")).isEqualTo("thumb");
        assertThat(processor.normalizeVariant("RANK.png")).isEqualTo("rank");
        assertThat(processor.normalizeVariant(" profile ")).isEqualTo("profile");
        assertThat(processor.normalizeVariant("original")).isNull();
    }

    @Test
    void processAndStore_rejectsUnsupportedFormat() throws Exception {
        ProfileImageProcessor processor = new ProfileImageProcessor(tempDir.resolve("profiles").toString());
        Path sourceFile = tempDir.resolve("source.png");
        writeImage(sourceFile, 512, 512, "png");

        assertThatThrownBy(() -> processor.processAndStore(39L, sourceFile, "text/plain", "source.txt"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Only PNG/JPG images are allowed.");
    }

    @Test
    void hardDeleteUserImages_removesAllStoredFiles() throws Exception {
        ProfileImageProcessor processor = new ProfileImageProcessor(tempDir.resolve("profiles").toString());
        Path sourceFile = tempDir.resolve("source-for-delete.jpg");
        writeImage(sourceFile, 900, 700, "jpg");
        processor.processAndStore(77L, sourceFile, "image/jpeg", "source-for-delete.jpg");

        Path userDir = processor.resolveVariantPath(77L, ProfileImageProcessor.VARIANT_PROFILE).getParent();
        assertThat(userDir).isNotNull();
        assertThat(Files.exists(userDir)).isTrue();

        processor.hardDeleteUserImages(77L);

        assertThat(Files.exists(userDir)).isFalse();
    }

    private void assertVariantSize(Path file, int expectedSize) throws IOException {
        assertThat(Files.exists(file)).isTrue();
        BufferedImage image = ImageIO.read(file.toFile());
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(expectedSize);
        assertThat(image.getHeight()).isEqualTo(expectedSize);
    }

    private void writeImage(Path output, int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(50, 120, 200));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ImageIO.write(image, format, output.toFile());
    }
}

package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSearchRecallTest {

    @Test
    void recallAndHitRate() {
        List<List<String>> relevant = List.of(List.of("a", "b"), List.of("c"));
        List<List<String>> retrieved = List.of(List.of("a", "x"), List.of("z", "c"));
        assertEquals(0.25d, ImageSearchRecall.meanRecall(relevant, retrieved, 1));
        assertEquals(1d, ImageSearchRecall.hitRate(relevant, retrieved, 5));
        assertEquals(20L, ImageSearchRecall.percentile(List.of(10L, 30L, 20L), 50));
    }

    @Test
    void scaleAndCropStayReadable() throws Exception {
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 40, 20);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] scaled = ImageTransforms.scaleHalf(out.toByteArray());
        byte[] cropped = ImageTransforms.centerCrop(out.toByteArray());
        BufferedImage scaledImage = ImageIO.read(new ByteArrayInputStream(scaled));
        BufferedImage croppedImage = ImageIO.read(new ByteArrayInputStream(cropped));
        assertEquals(20, scaledImage.getWidth());
        assertEquals(10, scaledImage.getHeight());
        assertTrue(croppedImage.getWidth() < 40);
        assertTrue(croppedImage.getHeight() < 20);
    }
}

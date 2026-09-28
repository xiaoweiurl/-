package com.imagemanager.imagesearch;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * 评测用的只读变换：缩放和中心裁剪，不写回存储。
 */
public final class ImageTransforms {

    private ImageTransforms() {
    }

    public static byte[] scaleHalf(byte[] original) {
        BufferedImage image = read(original);
        int width = Math.max(1, image.getWidth() / 2);
        int height = Math.max(1, image.getHeight() / 2);
        return write(draw(image, width, height, 0, 0, image.getWidth(), image.getHeight()));
    }

    public static byte[] centerCrop(byte[] original) {
        BufferedImage image = read(original);
        int cropW = Math.max(1, (int) Math.round(image.getWidth() * 0.6));
        int cropH = Math.max(1, (int) Math.round(image.getHeight() * 0.6));
        int x = Math.max(0, (image.getWidth() - cropW) / 2);
        int y = Math.max(0, (image.getHeight() - cropH) / 2);
        return write(draw(image, cropW, cropH, x, y, cropW, cropH));
    }

    private static BufferedImage draw(BufferedImage source, int width, int height,
                                      int sx, int sy, int sw, int sh) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, sx, sy, sx + sw, sy + sh, null);
        graphics.dispose();
        return out;
    }

    private static BufferedImage read(byte[] original) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
            if (image == null) {
                throw new IllegalArgumentException("不是可解码的图片");
            }
            return image;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("读取图片失败: " + e.getMessage(), e);
        }
    }

    private static byte[] write(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "jpg", out)) {
                throw new IllegalArgumentException("写出 JPEG 失败");
            }
            return out.toByteArray();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("写出图片失败: " + e.getMessage(), e);
        }
    }
}

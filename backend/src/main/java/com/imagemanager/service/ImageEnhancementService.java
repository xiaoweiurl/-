package com.imagemanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 图片增强服务 - 提供锐化、超分辨率等功能
 */
@Slf4j
@Service
public class ImageEnhancementService {

    /**
     * 对图片进行锐化处理，增强边缘细节
     * @param image 原始图片
     * @param strength 锐化强度 (0.0 - 3.0)，建议值 1.0-2.0
     * @return 锐化后的图片
     */
    public BufferedImage sharpen(BufferedImage image, float strength) {
        if (image == null) {
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // 锐化卷积核 (USM Unsharp Mask)
        // [ 0, -1,  0]
        // [-1,  5, -1]
        // [ 0, -1,  0]
        double[][] kernel = {
            {0, -strength/4, 0},
            {-strength/4, 1 + strength, -strength/4},
            {0, -strength/4, 0}
        };

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double[] rgb = new double[3];

                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int pixel = image.getRGB(x + kx, y + ky);
                        Color color = new Color(pixel);
                        double weight = kernel[ky + 1][kx + 1];

                        rgb[0] += color.getRed() * weight;
                        rgb[1] += color.getGreen() * weight;
                        rgb[2] += color.getBlue() * weight;
                    }
                }

                int r = clamp((int) rgb[0]);
                int g = clamp((int) rgb[1]);
                int b = clamp((int) rgb[2]);

                result.setRGB(x, y, new Color(r, g, g).getRGB());
            }
        }

        // 处理边缘像素（简单复制）
        for (int x = 0; x < width; x++) {
            result.setRGB(x, 0, image.getRGB(x, 0));
            result.setRGB(x, height - 1, image.getRGB(x, height - 1));
        }
        for (int y = 0; y < height; y++) {
            result.setRGB(0, y, image.getRGB(0, y));
            result.setRGB(width - 1, y, image.getRGB(width - 1, y));
        }

        return result;
    }

    /**
     * 图片超分辨率 - 2倍放大并锐化
     * @param image 原始图片
     * @return 放大2倍后的图片
     */
    public BufferedImage superResolution2x(BufferedImage image) {
        if (image == null) {
            return null;
        }

        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();
        int newWidth = originalWidth * 2;
        int newHeight = originalHeight * 2;

        // 第一步：高质量双线性插值放大
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(image, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        // 第二步：边缘增强锐化
        BufferedImage sharpened = enhanceEdges(scaled);

        return sharpened;
    }

    /**
     * 边缘增强处理
     */
    private BufferedImage enhanceEdges(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // 边缘增强卷积核
        double[][] edgeKernel = {
            {-0.5, -1, -0.5},
            {-1, 7, -1},
            {-0.5, -1, -0.5}
        };

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double[] rgb = {0, 0, 0};

                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int pixel = image.getRGB(x + kx, y + ky);
                        Color color = new Color(pixel);
                        double weight = edgeKernel[ky + 1][kx + 1];

                        rgb[0] += color.getRed() * weight;
                        rgb[1] += color.getGreen() * weight;
                        rgb[2] += color.getBlue() * weight;
                    }
                }

                int r = clamp((int) rgb[0]);
                int g = clamp((int) rgb[1]);
                int b = clamp((int) rgb[2]);

                result.setRGB(x, y, new Color(r, g, b).getRGB());
            }
        }

        // 复制边缘像素
        for (int x = 0; x < width; x++) {
            result.setRGB(x, 0, image.getRGB(x, 0));
            result.setRGB(x, height - 1, image.getRGB(x, height - 1));
        }
        for (int y = 0; y < height; y++) {
            result.setRGB(0, y, image.getRGB(0, y));
            result.setRGB(width - 1, y, image.getRGB(width - 1, y));
        }

        return result;
    }

    /**
     * 对比度增强
     */
    public BufferedImage enhanceContrast(BufferedImage image, float factor) {
        if (image == null) {
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                Color color = new Color(pixel);

                int r = clamp((int) ((color.getRed() - 128) * factor + 128));
                int g = clamp((int) ((color.getGreen() - 128) * factor + 128));
                int b = clamp((int) ((color.getBlue() - 128) * factor + 128));

                result.setRGB(x, y, new Color(r, g, b).getRGB());
            }
        }

        return result;
    }

    /**
     * 图片质量优化 - 综合处理
     * @param image 原始图片
     * @param enableSuperResolution 是否启用超分辨率
     * @return 优化后的图片
     */
    public BufferedImage enhance(BufferedImage image, boolean enableSuperResolution) {
        if (image == null) {
            return null;
        }

        log.info("开始图片增强处理, 原尺寸: {}x{}, 超分辨率: {}", 
                image.getWidth(), image.getHeight(), enableSuperResolution);

        BufferedImage result = image;

        // 超分辨率放大
        if (enableSuperResolution && (image.getWidth() < 800 || image.getHeight() < 800)) {
            result = superResolution2x(result);
            log.info("超分辨率完成, 新尺寸: {}x{}", result.getWidth(), result.getHeight());
        }

        // 边缘锐化
        result = sharpen(result, 1.5f);

        // 对比度微调
        result = enhanceContrast(result, 1.1f);

        log.info("图片增强处理完成");

        return result;
    }

    /**
     * 将 BufferedImage 转换为 byte[]
     */
    public byte[] toByteArray(BufferedImage image, String format) {
        if (image == null) {
            return null;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("图片转换失败", e);
            return null;
        }
    }

    /**
     * 确保值在 0-255 范围内
     */
    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}

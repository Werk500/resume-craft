package com.resumecraft.server.resume.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 图片压缩工具（减少 AI Token 消耗）
 */
@Slf4j
@Component
public class ImageCompressor {

    // DeepSeek 视觉模型推荐：图片最大 1024x1024
    private static final int MAX_WIDTH = 1024;
    private static final int MAX_HEIGHT = 1024;

    // 图片质量（0.1-1.0，越低压缩率越高）
    private static final float JPEG_QUALITY = 0.7f;

    /**
     * 压缩图片到合适大小（减少 Token 消耗）
     */
    public byte[] compressIfNeeded(BufferedImage image, String format) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        int originalSize = width * height;

        // 如果图片已经足够小，直接返回
        if (width <= MAX_WIDTH && height <= MAX_HEIGHT) {
            log.debug("图片尺寸符合要求: {}x{}", width, height);
            return toBytes(image, format);
        }

        // 计算缩放比例
        double ratio = Math.min((double) MAX_WIDTH / width, (double) MAX_HEIGHT / height);
        int newWidth = (int) (width * ratio);
        int newHeight = (int) (height * ratio);

        log.info("压缩图片: {}x{} -> {}x{} (原始: {} 像素)",
                width, height, newWidth, newHeight, originalSize);

        // 缩放图片（使用高质量算法）
        BufferedImage compressed = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = compressed.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return toBytes(compressed, format);
    }

    /**
     * 图片转字节数组
     */
    private byte[] toBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if ("png".equalsIgnoreCase(format)) {
            ImageIO.write(image, "png", baos);
        } else {
            // 使用 JPEG 压缩（更小）
            ImageIO.write(image, "jpg", baos);
        }
        return baos.toByteArray();
    }
}
package com.resumecraft.server.resume.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * 图片压缩工具（减少 AI Token 消耗）
 */
@Slf4j
@Component
public class ImageCompressor {

    // 简历 OCR 对文字清晰度敏感，长边放大到 1600
    private static final int MAX_WIDTH = 1600;
    private static final int MAX_HEIGHT = 1600;

    // JPEG 质量（0.1-1.0）
    private static final float JPEG_QUALITY = 0.75f;

    /**
     * 压缩图片到合适大小（减少 Token 消耗）
     */
    public byte[] compressIfNeeded(BufferedImage image, String format) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        int originalSize = width * height;

        BufferedImage target;
        // 如果图片已经足够小，直接返回
        if (width <= MAX_WIDTH && height <= MAX_HEIGHT) {
            log.debug("图片尺寸符合要求: {}x{}", width, height);
            target = image;
        }else {

            // 计算缩放比例
            double ratio = Math.min((double) MAX_WIDTH / width, (double) MAX_HEIGHT / height);
            int newWidth = (int) (width * ratio);
            int newHeight = (int) (height * ratio);

            log.info("压缩图片: {}x{} -> {}x{} (原始: {} 像素)",
                    width, height, newWidth, newHeight, originalSize);

            // 缩放图片（使用高质量算法）
            target = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = target.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, newWidth, newHeight, null);
            g.dispose();
        }

        // 统一输出 JPEG（注意：ImageParser 发 MIME 时必须用 IMAGE_JPEG）
        return toJpegBytes(target, JPEG_QUALITY);
    }

//    /**
//     * 图片转字节数组
//     */
//    private byte[] toBytes(BufferedImage image, String format) throws IOException {
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        if ("png".equalsIgnoreCase(format)) {
//            ImageIO.write(image, "png", baos);
//        } else {
//            // 使用 JPEG 压缩（更小）
//            ImageIO.write(image, "jpg", baos);
//        }
//        return baos.toByteArray();
//    }


    /**
     * 用指定质量编码为 JPEG
     */
    private byte[] toJpegBytes(BufferedImage image, float quality) throws IOException {
        // JPEG 不支持透明通道，先转 RGB，透明区域填白（避免变黑影响 OCR）
        BufferedImage rgbImage = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgbImage.createGraphics();
            g.drawImage(image, 0, 0, Color.WHITE, null);
            g.dispose();
        }

        // 获取 JPEG 编码器
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("找不到 JPEG 编码器");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgbImage, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
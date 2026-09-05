package com.resumecraft.server.resume.parser;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.resumecraft.server.ai.impl.PromptTemplates;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;

/**
 * 图片简历解析器（视觉大模型识别，路线 B）。
 * <p>将图片统一转 PNG 后发给多模态大模型（DeepSeek 视觉）提取文字。
 * <p>支持格式：png, jpg, jpeg, bmp
 */
@Component
@Slf4j
public class ImageParser implements ResumeParser {

    /** 支持的图片格式（ImageIO 不内置 webp 解码，故不含 webp） */
    private static final Set<String> SUPPORTED_FORMATS = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "bmp"
    ));

    @Resource
    private ChatClient chatClient;
    @Resource
    private ImageCompressor imageCompressor;

    @Override
    public boolean supports(String fileType) {
        return fileType != null && SUPPORTED_FORMATS.contains(fileType.toLowerCase());
    }

    @Override
    public String parse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("输入流不能为空");
        }

        // 1. 解码图片（校验是否有效图片），统一转 PNG 字节（保证 mime 一致）
        BufferedImage image = ImageIO.read(inputStream);
        if (image == null) {
            throw new IOException("无法解码图片，可能不是有效的图片文件");
        }

        //压缩图片(减少token消耗)
        byte[] compressedBytes = imageCompressor.compressIfNeeded(image, "png");
        log.info("图片压缩完成: {}x{} -> {} bytes",
                image.getWidth(), image.getHeight(), compressedBytes.length);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] pngBytes = baos.toByteArray();
        log.info("图片已解码并转 PNG: {}x{}, {} bytes", image.getWidth(), image.getHeight(), pngBytes.length);

        // 2. 调用多模态大模型识别图片文字
        String text;
        try {
            text = chatClient.prompt()
                    .system(PromptTemplates.OCR_SYSTEM)
                    .user(u -> u.text("请识别这张简历图片中的文字")
                            .media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(pngBytes)))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("多模态图片识别失败", e);
            throw new IOException("图片文字识别失败: " + e.getMessage(), e);
        }

        if (text == null || text.isBlank()) {
            throw new IOException("图片识别结果为空");
        }
        log.info("图片识别完成，识别文本长度: {}", text.length());
        return text.trim();
    }

}

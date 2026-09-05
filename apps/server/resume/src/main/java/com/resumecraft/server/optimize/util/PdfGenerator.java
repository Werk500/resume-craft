package com.resumecraft.server.optimize.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * PDF 渲染器（涉及系统字体文件路径/IO，与运行环境耦合）
 *
 * <p>独立于 MarkdownConverter，便于：
 * <ul>
 *   <li>根据不同环境配置不同字体路径</li>
 *   <li>方便切换字体渲染策略</li>
 *   <li>便于单元测试时 mock</li>
 * </ul>
 */
@Slf4j
@Component
public class PdfGenerator {

    @Value("${pdf.font.path:}")
    private String fontPath;

    private static final String DEFAULT_FONT_NAME = "SimHei";

    /**
     * HTML → PDF 字节数组
     */
    public byte[] htmlToPdf(String html) {
        if (html == null || html.trim().isEmpty()) {
            throw new IllegalArgumentException("HTML 内容为空");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();

            // 注册中文字体（关键：解决中文乱码）
            registerFont(builder);

            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();

            byte[] pdfBytes = baos.toByteArray();
            log.info("PDF 生成成功，大小: {} bytes", pdfBytes.length);
            return pdfBytes;

        } catch (Exception e) {
            log.error("PDF 生成失败", e);
            throw new RuntimeException("PDF 生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 注册中文字体
     */
    private void registerFont(PdfRendererBuilder builder) {
        String fontPathToUse = resolveFontPath();
        if (fontPathToUse == null) {
            log.warn("未找到中文字体，PDF 中文可能显示为乱码！");
            return;
        }

        File fontFile = new File(fontPathToUse);
        if (!fontFile.exists() || !fontFile.canRead()) {
            log.warn("字体文件不存在或不可读: {}", fontPathToUse);
            return;
        }

        try {
            builder.useFont(fontFile, DEFAULT_FONT_NAME);
            log.info("成功加载字体: {} -> {}", fontPathToUse, DEFAULT_FONT_NAME);
        } catch (Exception e) {
            log.error("字体加载失败: {}", fontPathToUse, e);
        }
    }

    /**
     * 解析字体路径（支持配置 + 多环境自动探测）
     */
    private String resolveFontPath() {
        // 1. 优先使用配置的路径
        if (fontPath != null && !fontPath.trim().isEmpty()) {
            return fontPath.trim();
        }

        // 2. Windows 常见字体（按推荐顺序）
        String[] windowsFonts = {
                "C:/Windows/Fonts/simhei.ttf",    // 黑体（推荐）
                "C:/Windows/Fonts/msyh.ttf",       // 微软雅黑
                "C:/Windows/Fonts/simsun.ttc",     // 宋体（ttc 可能不稳定）
                "C:/Windows/Fonts/simkai.ttf"      // 楷体
        };
        for (String path : windowsFonts) {
            if (new File(path).exists()) {
                return path;
            }
        }

        // 3. Mac 常见字体
        String[] macFonts = {
                "/System/Library/Fonts/PingFang.ttc",
                "/System/Library/Fonts/STHeiti Light.ttc",
                "/System/Library/Fonts/Helvetica.ttf"
        };
        for (String path : macFonts) {
            if (new File(path).exists()) {
                return path;
            }
        }

        // 4. Linux 常见字体
        String[] linuxFonts = {
                "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
                "/usr/share/fonts/truetype/arphic/uming.ttc"
        };
        for (String path : linuxFonts) {
            if (new File(path).exists()) {
                return path;
            }
        }

        return null;
    }
}

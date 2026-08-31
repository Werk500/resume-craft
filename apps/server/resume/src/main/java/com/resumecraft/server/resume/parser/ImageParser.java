package com.resumecraft.server.resume.parser;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Component;

/**
 * 图片简历解析器（占位实现）。
 * TODO: 后续集成 Tesseract OCR（需求文档 3.5 的 OCR 兜底策略）。
 */
@Component
public class ImageParser implements ResumeParser {

    @Override
    public boolean supports(String fileType) {
        return "png".equalsIgnoreCase(fileType)
                || "jpg".equalsIgnoreCase(fileType)
                || "jpeg".equalsIgnoreCase(fileType)
                || "bmp".equalsIgnoreCase(fileType)
                || "webp".equalsIgnoreCase(fileType);
    }

    @Override
    public String parse(InputStream inputStream) throws IOException {
        throw new IOException("图片简历 OCR 识别暂未集成，请改用 PDF 或 Word 格式上传");
    }
}

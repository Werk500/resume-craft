package com.resumecraft.server.resume.parser;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import com.resumecraft.server.resume.parser.dto.OcrBlock;
import com.resumecraft.server.resume.parser.dto.OcrParseResult;
import jakarta.annotation.Resource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;

/**
 * PDF 简历解析器（Apache PDFBox）。
 */
@Component
public class PdfParser implements ConfidenceParser {

    private static final int MAX_OCR_PAGES = 5;
    private static final float OCR_DPI = 150f;

    @Resource
    private ImageParser imageParser;
    @Resource(name = "ocrExecutor")
    private ThreadPoolTaskExecutor ocrExecutor;

    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType);
    }

    @Override
    public String parse(InputStream inputStream) throws IOException {
        return parseWithBlocks(inputStream).getRawText();
    }

    @Override
    public OcrParseResult parseWithBlocks(InputStream inputStream) throws IOException {
        byte[] pdfBytes = inputStream.readAllBytes();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.isEncrypted()) {
                throw new IOException("加密的 PDF 无法解析，请上传未加密版本");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String textLayer = stripper.getText(document);

            if (textLayer != null && !textLayer.isBlank()) {
                return textLayerResult(textLayer);
            }

            return renderAndOcr(document);
        }
    }

    private OcrParseResult renderAndOcr(PDDocument document) throws IOException {

        PDFRenderer renderer = new PDFRenderer(document);


        int pageCount = Math.min(document.getNumberOfPages(), MAX_OCR_PAGES);

        List<byte[]> pageImages = new ArrayList<>(pageCount);
        //串行渲染，避免共享 PDDocument 并发访问
        for (int page = 0; page < pageCount; page++) {
            BufferedImage image = renderer.renderImageWithDPI(page, OCR_DPI, ImageType.RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            pageImages.add(output.toByteArray());
        }

        //每页 OCR 并行提交到线程池
        List<Future<OcrParseResult>> futures = new ArrayList<>(pageCount);

        try {
            for (byte[] image : pageImages) {
                futures.add(ocrExecutor.submit(()->imageParser
                        .parseWithBlocks(new ByteArrayInputStream(image))));
            }
        } catch (RejectedExecutionException e) {
            futures.forEach(future -> future.cancel(true));
            throw new IOException("当前简历识别任务较多，请稍后重试", e);
        }

        List<OcrBlock> blocks = new ArrayList<>();
        StringBuilder rawText = new StringBuilder();
        double minConfidence = 1.0;

        try {
            // 第三步：按页顺序获取结果
            for (int page = 0; page < futures.size(); page++) {
                OcrParseResult pageResult = futures.get(page).get();

                if (pageResult.getBlocks() != null) {
                    blocks.addAll(pageResult.getBlocks());
                }

                if (pageResult.getOverallConfidence() != null) {
                    minConfidence = Math.min(
                            minConfidence,
                            pageResult.getOverallConfidence());
                }

                rawText.append("--- 第 ")
                        .append(page + 1)
                        .append(" 页 ---\n")
                        .append(pageResult.getRawText())
                        .append("\n\n");
            }
        } catch (InterruptedException e) {
            //主线程被外部中断
            Thread.currentThread().interrupt();
            //立即取消其他还在跑（或排队）的 OCR 任务。
            futures.forEach(future -> future.cancel(true));
            throw new IOException("简历识别被中断", e);

        } catch (ExecutionException e) {
            futures.forEach(future -> future.cancel(true));
            Throwable cause = e.getCause();
            throw new IOException(
                    "简历页识别失败：" + cause.getMessage(), cause);
        }

        //截断提示（兜底保护）
        if (document.getNumberOfPages() > MAX_OCR_PAGES) {
            blocks.add(OcrBlock.builder()
                    .text("")
                    .confidence(0.2)
                    .reason("仅识别前 5 页，请人工核对后续内容")
                    .build());
        }

        return OcrParseResult.builder()
                .rawText(rawText.toString())
                .blocks(blocks)
                .overallConfidence(minConfidence)
                .build();
    }

    private OcrParseResult textLayerResult(String text) {
        OcrBlock block = OcrBlock.builder()
                .text(text)
                .confidence(1.0)
                .reason("PDF 原生文字层")
                .build();

        return OcrParseResult.builder()
                .rawText(text)
                .blocks(List.of(block))
                .overallConfidence(1.0)
                .build();
    }
}

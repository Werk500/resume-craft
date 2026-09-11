package com.resumecraft.server.resume.parser;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.resume.parser.dto.OcrBlock;
import com.resumecraft.server.resume.parser.dto.OcrParseResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;

/**
 * 图片简历解析器（视觉大模型识别，路线 B）。
 * <p>将图片统一转 PNG 后发给多模态大模型（DeepSeek 视觉）提取文字。
 * <p>支持格式：png, jpg, jpeg, bmp
 */
@Component
@Slf4j
public class ImageParser implements ConfidenceParser {

    /** 支持的图片格式（ImageIO 不内置 webp 解码，故不含 webp） */
    private static final Set<String> SUPPORTED_FORMATS = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "bmp"
    ));

    @Resource
    private ChatClient chatClient;
    @Resource
    private ImageCompressor imageCompressor;
    @Resource
    private AiService aiService;
    @Resource
    private ObjectMapper objectMapper;


    @Override
    public boolean supports(String fileType) {
        return fileType != null && SUPPORTED_FORMATS.contains(fileType.toLowerCase());
    }


    @Override
    public String parse(InputStream in) throws IOException {
        OcrParseResult result = parseWithBlocks(in);
        return result.getRawText();
    }

    public OcrParseResult parseWithBlocks(InputStream in) throws IOException{

            if (in == null) {
                throw new IllegalArgumentException("输入流不能为空");
            }

            // 1. 解码图片（校验是否有效图片），统一转 PNG 字节
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IOException("无法解码图片，可能不是有效的图片文件");
            }

            // 2. 压缩图片（减少token消耗）
            byte[] compressedBytes = imageCompressor.compressIfNeeded(image, "png");
            log.info("图片压缩完成: {}x{} -> {} bytes",
                    image.getWidth(), image.getHeight(), compressedBytes.length);
            try{
                //4.调用AI服务进行OCR识别
                String response = aiService.ocrRecognize(compressedBytes,PromptTemplates.OCR_BLOCK_SYSTEM);
                if (response == null || response.isEmpty()) {
                    throw new IOException("图片识别结果为空");
                }

                try {
                    //尝试解析JSON
                    return parseJsonResponse(response);
                } catch (IOException e) {
                    return fallbackFromResponse(response,e);
                }
            } catch (IOException e) {
                throw new IOException("上传失败，请换清晰图片");
            }


    }

    private OcrParseResult parseJsonResponse(String response) throws IOException {

        // 清理响应，移除可能的markdown围栏
        String cleanedResponse = response.trim();
        if (cleanedResponse.startsWith("```json")) {
            cleanedResponse = cleanedResponse.substring(7);
        }
        if (cleanedResponse.startsWith("```")) {
            cleanedResponse = cleanedResponse.substring(3);
        }
        if (cleanedResponse.endsWith("```")) {
            cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
        }
        cleanedResponse = cleanedResponse.trim();
        
        //解析JSON
        Map jsonMap;
        try {
            jsonMap = objectMapper.readValue(cleanedResponse, Map.class);
        } catch (Exception e) {
            throw new IOException("OCR 响应不是合法 JSON", e);
        }

        // 校验 blocks 字段 —— 不存在或不是数组都主动抛 IOException
        Object blocksObj = jsonMap.get("blocks");
        if (!(blocksObj instanceof List<?> list) || list.isEmpty()) {
            throw new IOException("OCR 结果缺少 blocks 或为空");
        }

        List<OcrBlock> blocks = new ArrayList<>();
        StringBuilder rawText = new StringBuilder();

        for (Object item : list) {
            // 每个元素也必须是 Map，否则跳过（而不是抛异常，允许部分块异常）
            if (!(item instanceof Map<?, ?> blockMap)) {
                log.warn("OCR 结果中存在非 Map 的 block，已跳过: {}", item);
                continue;
            }

            //text必须有值，缺失就跳过这块
            Object textObj = blockMap.get("text");
            if (textObj == null) {
                log.warn("OCR block 缺少 text 字段，已跳过");
                continue;
            }

            OcrBlock block = OcrBlock.builder()
                    .text(textObj.toString())
                    .confidence(toDouble(blockMap.get("confidence")))
                    .reason(blockMap.get("reason") == null ? null : blockMap.get("reason").toString())
                    .build();
            blocks.add(block);
            rawText.append(block.getText()).append("\n\n");
        }

        // 遍历完仍然没有有效块 → 抛 IOException
        if (blocks.isEmpty()) {
            throw new IOException("OCR 结果中没有有效的 block");
        }

        // 整体置信度：优先用字段值，为 null 时按块平均值兜底
        Double overallConfidence = toDouble(jsonMap.get("overallConfidence"));
        if (overallConfidence == null) {
            overallConfidence = blocks.stream()
                    .map(OcrBlock::getConfidence)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.4); // 一个块都没有置信度时，给一个保守值
            log.info("overallConfidence 缺失，按块平均兜底: {}", overallConfidence);
        }

        return OcrParseResult.builder()
                .blocks(blocks)
                .overallConfidence(overallConfidence)
                .rawText(rawText.toString())
                .build();


    }

    private OcrParseResult fallbackFromResponse(String response,Exception cause) {
        String text = extractReadableText(response);

        if (text.isBlank()) {
            OcrBlock block = OcrBlock.builder()
                    .text("")
                    .confidence(0.2)
                    .reason("识别结果不可读，请重新上传清晰图片")
                    .build();

            log.warn("OCR fallback: 无可读文本，原始响应长度={}, 原因={}",
                    response == null ? 0 : response.length(),
                    cause == null ? "未知" : cause.getMessage());

            return OcrParseResult.builder()
                    .blocks(List.of(block))
                    .overallConfidence(0.2)
                    .rawText("")
                    .build();
        }

        // 有可读文本：保留 0.4，并附带原始异常信息便于排查
        String reason = "OCR 结果未按结构化格式返回，请人工核对";
        if (cause != null) {
            reason += "（原因：" + cause.getMessage() + "）";
        }

        OcrBlock block = OcrBlock.builder()
                .text(text)
                .confidence(0.4)
                .reason(reason)
                .build();

        return OcrParseResult.builder()
                .blocks(List.of(block))
                .overallConfidence(0.4)
                .rawText(text)
                .build();
    }


    private Double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { }
        }
        return null;
    }


    /**
     * 从模型响应中提取可读文本：
     * 1. 剥离 markdown 围栏
     * 2. 去掉明显的解释性前缀（如"好的，以下是识别结果："）
     * 3. 如果剥开后是 JSON，尝试从中抽 text 字段；否则原样返回
     */
    private String extractReadableText(String response) {
        if (response == null || response.isBlank()) {
            return "";
        }
        String text = response.trim();

        // 1. 剥离 markdown 围栏
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        text = text.trim();

        // 2. 去掉解释性前缀（第一行以"好的""以下是""这是"开头且后面跟着JSON）
        int firstBrace = text.indexOf('{');
        if (firstBrace > 0) {
            String prefix = text.substring(0, firstBrace).trim();
            // 前缀很短且包含常见引导词，才认为是解释性前缀
            if (prefix.length() < 50 &&
                    (prefix.contains("好的") || prefix.contains("以下是")
                            || prefix.contains("这是") || prefix.contains("识别结果"))) {
                text = text.substring(firstBrace);
            }
        }

        // 3. 如果剩下的是 JSON，尝试提取 text 字段
        if (text.startsWith("{")) {
            try {
                Map<String, Object> jsonMap = objectMapper.readValue(text, Map.class);
                Object blocksObj = jsonMap.get("blocks");
                if (blocksObj instanceof List) {
                    StringBuilder sb = new StringBuilder();
                    for (Object b : (List<?>) blocksObj) {
                        if (b instanceof Map) {
                            Object t = ((Map<?, ?>) b).get("text");
                            if (t != null) {
                                sb.append(t).append("\n");
                            }
                        }
                    }
                    if (!sb.isEmpty()) {
                        return sb.toString().trim();
                    }
                }
                // JSON 里没有 text 字段，返回空（让上层降到 0.2）
                return "";
            } catch (Exception ignore) {
                // 不是合法 JSON，说明是自然语言，原样返回
            }
        }
        return text;
    }




}

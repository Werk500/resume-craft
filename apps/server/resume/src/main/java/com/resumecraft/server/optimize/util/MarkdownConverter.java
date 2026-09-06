package com.resumecraft.server.optimize.util;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayOutputStream;

/**
 * Markdown 简历文本 → docx/HTML 转换（导出用，纯函数无 Spring 依赖）
 */
public final class MarkdownConverter {
    private MarkdownConverter() {
        // 工具类私有构造
    }

    /**
     * Markdown → docx 字节（POI XWPFDocument）
     */
    public static byte[] toDocx(String markdown) {
        if (markdown == null) {
            markdown = "";
        }

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            String[] lines = markdown.split("\n");
            boolean inList = false;

            for (String line : lines) {
                String trimmed = line.trim();

                // 空行：列表结束
                if (trimmed.isEmpty()) {
                    inList = false;
                    continue;
                }

                // 标题 # ## ###
                if (trimmed.startsWith("# ")) {
                    inList = false;
                    createHeading(document, trimmed.substring(2), 24);
                } else if (trimmed.startsWith("## ")) {
                    inList = false;
                    createHeading(document, trimmed.substring(3), 20);
                } else if (trimmed.startsWith("### ")) {
                    inList = false;
                    createHeading(document, trimmed.substring(4), 17);
                }
                // 列表项 -
                else if (trimmed.startsWith("- ")) {
                    String content = processInlineFormatting(trimmed.substring(2));
                    if (!inList) {
                        inList = true;
                    }
                    createListItem(document, content);
                }
                // 普通段落
                else {
                    inList = false;
                    String content = processInlineFormatting(trimmed);
                    createParagraph(document, content, 14);
                }
            }

            document.write(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("DOCX 生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * Markdown → 简单 HTML 字符串（供 openhtmltopdf 渲染 PDF）
     */
    public static String toHtml(String markdown) {
        if (markdown == null) {
            markdown = "";
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>")
                .append("<html>")
                .append("<head>")
                .append("<meta charset=\"UTF-8\"/>")
                .append("</head>")
                .append("<body style=\"font-family: SimHei, Microsoft YaHei, sans-serif; font-size: 14px; line-height: 1.8; padding: 40px;\">");

        String[] lines = markdown.split("\n");
        boolean inList = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // 空行：关闭列表，加换行
            if (trimmed.isEmpty()) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                html.append("<br/>");
                continue;
            }

            // 标题 # ## ###
            if (trimmed.startsWith("# ")) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                String content = escapeHtml(processInlineFormatting(trimmed.substring(2)));
                html.append("<h1 style=\"font-size: 24px; font-weight: bold; margin: 20px 0 10px 0;\">")
                        .append(content)
                        .append("</h1>");
            } else if (trimmed.startsWith("## ")) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                String content = escapeHtml(processInlineFormatting(trimmed.substring(3)));
                html.append("<h2 style=\"font-size: 20px; font-weight: bold; margin: 18px 0 8px 0;\">")
                        .append(content)
                        .append("</h2>");
            } else if (trimmed.startsWith("### ")) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                String content = escapeHtml(processInlineFormatting(trimmed.substring(4)));
                html.append("<h3 style=\"font-size: 17px; font-weight: bold; margin: 15px 0 8px 0;\">")
                        .append(content)
                        .append("</h3>");
            }
            // 列表项 -
            else if (trimmed.startsWith("- ")) {
                if (!inList) {
                    html.append("<ul style=\"margin: 5px 0 5px 20px; padding-left: 20px;\">");
                    inList = true;
                }
                String content = escapeHtml(processInlineFormatting(trimmed.substring(2)));
                html.append("<li style=\"margin: 3px 0;\">")
                        .append(content)
                        .append("</li>");
            }
            // 普通段落
            else {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                String content = escapeHtml(processInlineFormatting(trimmed));
                html.append("<p style=\"margin: 5px 0;\">")
                        .append(content)
                        .append("</p>");
            }
        }

        // 关闭未闭合的列表
        if (inList) {
            html.append("</ul>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    // ============ POI 辅助方法 ============

    private static void createHeading(XWPFDocument document, String text, int fontSize) {
        XWPFParagraph para = document.createParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(fontSize);
    }

    private static void createParagraph(XWPFDocument document, String text, int fontSize) {
        XWPFParagraph para = document.createParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setFontSize(fontSize);
    }

    private static void createListItem(XWPFDocument document, String text) {
        XWPFParagraph para = document.createParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);
        para.setIndentationLeft(720); // 缩进 0.5 英寸
        XWPFRun run = para.createRun();
        run.setText("• " + text);
        run.setFontSize(14);
    }

    // ============ 文本处理 ============

    /**
     * 处理行内格式（MVP：去掉 ** 标记，不做真正的粗体渲染）
     */
    private static String processInlineFormatting(String text) {
        if (text == null) return "";
        return text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
    }

    /**
     * HTML 转义
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

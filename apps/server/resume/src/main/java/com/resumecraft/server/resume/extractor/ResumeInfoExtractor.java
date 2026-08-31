package com.resumecraft.server.resume.extractor;


import org.springframework.stereotype.Component;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历信息提取器
 * 负责从原始文本中提取邮箱、电话等关键信息
 */
@Component
public class ResumeInfoExtractor {

    // 邮箱正则表达式
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");

    // 中国大陆手机号正则表达式（支持更多格式）
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("1[3-9]\\d{9}");

    // 可选：手机号（含分隔符）
    private static final Pattern PHONE_FORMATTED_PATTERN =
            Pattern.compile("1[3-9]\\d[- ]?\\d{4}[- ]?\\d{4}");

    // 可选：提取姓名（简单实现）
    private static final Pattern NAME_PATTERN =
            Pattern.compile("^(?:姓名|name)[：:]\\s*(.+)", Pattern.CASE_INSENSITIVE);

    /**
     * 从文本中提取邮箱
     * @param text 原始文本
     * @return 第一个匹配的邮箱，没有则返回null
     */
    public String extractEmail(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = EMAIL_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    /**
     * 从文本中提取手机号
     * @param text 原始文本
     * @return 第一个匹配的手机号，没有则返回null
     */
    public String extractPhone(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = PHONE_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    /**
     * 从文本中提取姓名（示例）
     * @param text 原始文本
     * @return 匹配的姓名，没有则返回null
     */
    public String extractName(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = NAME_PATTERN.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * 批量提取所有信息
     * @param text 原始文本
     * @return 提取结果对象
     */
    public ExtractedInfo extractAll(String text) {
        return ExtractedInfo.builder()
                .email(extractEmail(text))
                .phone(extractPhone(text))
                .name(extractName(text))
                .build();
    }

    /**
     * 提取结果包装类
     */
    @lombok.Data
    @lombok.Builder
    public static class ExtractedInfo {
        private String email;
        private String phone;
        private String name;
    }
}

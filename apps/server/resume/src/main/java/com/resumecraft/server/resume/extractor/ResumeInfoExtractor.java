package com.resumecraft.server.resume.extractor;


import org.springframework.stereotype.Component;

import java.util.Set;
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

    private static final Pattern LABELED_NAME_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:姓名|name)\\s*[：:]\\s*([\\p{IsHan}·]{2,10}|[A-Za-z][A-Za-z .'-]{1,39})\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CHINESE_NAME_PATTERN =
            Pattern.compile("^[\\p{IsHan}·]{2,6}$");

    private static final Pattern DATE_PATTERN =
            Pattern.compile("(?<!\\d)(?:19|20)\\d{2}\\s*[/.\\-]\\s*\\d{1,2}(?!\\d)");

    private static final Pattern SECTION_TITLE_PATTERN = Pattern.compile(
            "^(教育经历|教育背景|工作经历|项目经历|实习经历|专业技能|技能特长|"
                    + "自我评价|求职意向|个人信息|获奖经历|校园经历)$"
    );

    private static final Set<String> NAME_BLACKLIST = Set.of(
            "个人简历", "简历", "基本信息", "个人信息",
            "教育经历", "教育背景", "工作经历", "项目经历",
            "实习经历", "专业技能", "技能特长", "自我评价", "求职意向"
    );

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

        Matcher labeled = LABELED_NAME_PATTERN.matcher(text);
        if (labeled.find()) {
            return labeled.group(1).trim();
        }

        String[] lines = text.split("\\R");
        String bestName = null;
        int bestScore = 0;
        int limit = Math.min(lines.length, 20);

        for (int i = 0; i < limit; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) {
                continue;
            }
            if (SECTION_TITLE_PATTERN.matcher(line).matches()) {
                break;
            }

            String candidate = stripContactInfo(line);
            if (candidate.isBlank()) {
                continue;
            }

            boolean contactNearby = hasContactNearby(lines, i);
            for (String token : candidate.split("\\s+")) {
                String name = token.trim();
                if (!CHINESE_NAME_PATTERN.matcher(name).matches()
                        || NAME_BLACKLIST.contains(name)) {
                    continue;
                }

                int score = 2;
                if (i < 5) {
                    score++;
                }
                if (contactNearby) {
                    score += 2;
                }
                if (score > bestScore) {
                    bestName = name;
                    bestScore = score;
                }
            }
        }

        return bestName;
    }

    private String stripContactInfo(String line) {
        return line
                .replaceAll(EMAIL_PATTERN.pattern(), " ")
                .replaceAll(PHONE_FORMATTED_PATTERN.pattern(), " ")
                .replaceAll(PHONE_PATTERN.pattern(), " ")
                .replaceAll(DATE_PATTERN.pattern(), " ")
                .replaceAll("[|｜]", " ")
                .replaceAll("^[\\s·/,-]+|[\\s·/,-]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean hasContactNearby(String[] lines, int index) {
        int from = Math.max(0, index - 1);
        int to = Math.min(lines.length - 1, index + 1);
        for (int i = from; i <= to; i++) {
            String line = lines[i];
            if (EMAIL_PATTERN.matcher(line).find()
                    || PHONE_PATTERN.matcher(line).find()
                    || PHONE_FORMATTED_PATTERN.matcher(line).find()) {
                return true;
            }
        }
        return false;
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

package com.resumecraft.server.job.match;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class MatchTextNormalizer {

    private MatchTextNormalizer() {
    }

    record KeywordTerm(
            String normalized,
            String display
    ) {
    }

    static List<KeywordTerm> normalizeAndDedupe(
            List<String> rawKeywords
    ) {
        if (rawKeywords == null || rawKeywords.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, KeywordTerm> unique =
                new LinkedHashMap<>();

        for (String raw : rawKeywords) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            String display = raw.trim();
            String normalized = normalizeKeyword(display);

            if (!normalized.isBlank()) {
                unique.putIfAbsent(
                        normalized,
                        new KeywordTerm(normalized, display)
                );
            }
        }

        return new ArrayList<>(unique.values());
    }

    static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(text.length());
        boolean previousWhitespace = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // 全角字母、数字和符号转半角
            if (c >= '！' && c <= '～') {
                c = (char) (c - 0xFEE0);
            }

            if (Character.isWhitespace(c)) {
                if (!previousWhitespace && sb.length() > 0) {
                    sb.append(' ');
                }
                previousWhitespace = true;
                continue;
            }

            previousWhitespace = false;
            sb.append(Character.toLowerCase(c));
        }

        return sb.toString()
                .trim()
                .replace("springboot", "spring boot")
                .replace("spring-boot", "spring boot")
                .replace("spring-cloud", "springcloud")
                .replace("spring cloud", "springcloud");
    }

    static String normalizeKeyword(String keyword) {
        return normalize(keyword);
    }

    static boolean containsKeyword(String text, String keyword) {
        if (text == null
                || text.isEmpty()
                || keyword == null
                || keyword.isBlank()) {
            return false;
        }

        // 中文等非 ASCII 关键词直接子串查找
        if (!isAsciiKeyword(keyword)) {
            return text.contains(keyword);
        }

        int fromIndex = 0;

        while (fromIndex <= text.length() - keyword.length()) {
            int index = text.indexOf(keyword, fromIndex);

            if (index < 0) {
                return false;
            }

            int before = index - 1;
            int after = index + keyword.length();

            boolean leftBoundary = before < 0
                    || !isAsciiWordCharacter(text.charAt(before));

            boolean rightBoundary = after >= text.length()
                    || !isAsciiWordCharacter(text.charAt(after));

            if (leftBoundary && rightBoundary) {
                return true;
            }

            fromIndex = index + 1;
        }

        return false;
    }

    private static boolean isAsciiKeyword(String keyword) {
        for (int i = 0; i < keyword.length(); i++) {
            if (keyword.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiWordCharacter(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9');
    }
}
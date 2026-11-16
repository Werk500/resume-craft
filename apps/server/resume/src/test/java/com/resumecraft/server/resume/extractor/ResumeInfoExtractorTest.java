package com.resumecraft.server.resume.extractor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeInfoExtractorTest {

    private final ResumeInfoExtractor extractor = new ResumeInfoExtractor();

    @Test
    @DisplayName("支持姓名标签在任意行")
    void shouldExtractLabeledName() {
        String text = """
                邮箱：example@example.com
                姓名：张三
                教育经历
                """;

        assertThat(extractor.extractName(text)).isEqualTo("张三");
    }

    @Test
    @DisplayName("支持 OCR 常见的姓名和手机号同一行")
    void shouldExtractNameFromContactLine() {
        String text = """
                2005/09 example@example.com
                李畅 18779724680
                教育经历
                江西财经大学 软件工程 2023/09 - 2027/06
                """;

        assertThat(extractor.extractName(text)).isEqualTo("李畅");
    }

    @Test
    @DisplayName("不会把章节标题或教育经历当成姓名")
    void shouldIgnoreSectionTitlesAndEducation() {
        String text = """
                个人简历
                专业技能
                教育经历
                江西财经大学 软件工程
                """;

        assertThat(extractor.extractName(text)).isNull();
    }

    @Test
    @DisplayName("邮箱和手机号仍可独立提取")
    void shouldExtractContactFields() {
        String text = "张三，13800138000，example@example.com";

        assertThat(extractor.extractPhone(text)).isEqualTo("13800138000");
        assertThat(extractor.extractEmail(text)).isEqualTo("example@example.com");
    }
}

package com.resumecraft.server.ai.template;

import java.util.Arrays;
import java.util.List;

public enum ResumeTemplate {
    CAMPUS("campus", "应届生 · 校园版",
            List.of("个人信息", "教育背景", "专业技能", "校园经历", "项目经历", "荣誉与证书", "自我评价")),
    TECH("tech", "技术岗 · 项目驱动",
            List.of("个人信息", "专业技能", "项目经历", "实习经历", "教育背景", "开源与作品")),
    INTERN("intern", "实习 · 数据量化",
            List.of("个人信息", "教育背景", "实习经历", "项目经历", "专业技能", "自我评价")),
    EXPERIENCED("experienced", "社招 · 工作经历",
            List.of("个人信息", "工作经历", "项目经历", "专业技能", "教育背景", "证书"));

    private final String id;
    private final String name;
    private final List<String> sections;

    ResumeTemplate(String id, String name, List<String> sections) {
        this.id = id;
        this.name = name;
        this.sections = sections;
    }

    /** 未知 id 回退默认模板，避免直接 400 */
    public static ResumeTemplate fromId(String id) {
        if (id == null || id.isBlank()) return CAMPUS;
        return Arrays.stream(values())
                .filter(t -> t.id.equalsIgnoreCase(id.trim()))
                .findFirst()
                .orElse(CAMPUS);
    }

    /** 供 Prompt 使用的章节约束文本 */
    public String toPromptBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("使用《").append(name).append("》模板，严格按以下章节与顺序输出 Markdown：\n");
        for (int i = 0; i < sections.size(); i++) {
            sb.append(i + 1).append(". ").append(sections.get(i)).append("\n");
        }
        sb.append("信息不足的章节保留标题并写“待补充”，不要新增模板之外的章节。");
        return sb.toString();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<String> getSections() { return sections; }
}

export interface ResumeTemplate {
  id: string;
  name: string;
  tagline: string;
  /** 章节顺序（前端展示 + 生成时的结构约束） */
  sections: string[];
}

export const RESUME_TEMPLATES: ResumeTemplate[] = [
  {
    id: "campus",
    name: "应届生 · 校园版",
    tagline: "无实习或少实习，突出校园经历与项目",
    sections: ["个人信息", "教育背景", "专业技能", "校园经历", "项目经历", "荣誉与证书", "自我评价"],
  },
  {
    id: "tech",
    name: "技术岗 · 项目驱动",
    tagline: "后端 / 算法 / 客户端，项目与技术栈优先",
    sections: ["个人信息", "专业技能", "项目经历", "实习经历", "教育背景", "开源与作品"],
  },
  {
    id: "intern",
    name: "实习 · 数据量化",
    tagline: "强调实习成果与可量化的指标",
    sections: ["个人信息", "教育背景", "实习经历", "项目经历", "专业技能", "自我评价"],
  },
  {
    id: "experienced",
    name: "社招 · 工作经历",
    tagline: "按时间倒序突出工作经历与业绩",
    sections: ["个人信息", "工作经历", "项目经历", "专业技能", "教育背景", "证书"],
  },
];

export const DEFAULT_TEMPLATE_ID = "campus";

export function getTemplate(id: string | undefined): ResumeTemplate {
  return (
    RESUME_TEMPLATES.find((template) => template.id === id) ??
    RESUME_TEMPLATES.find((template) => template.id === DEFAULT_TEMPLATE_ID)!
  );
}

/**
 * 生成结构约束指令。
 *
 * 后端支持 `templateId` 后会由服务端 Prompt 注入；这里作为过渡兜底，
 * 追加在发送给接口的最后一条 user 消息中（界面不展示）。
 */
export function buildTemplateInstruction(templateId: string): string {
  const template = getTemplate(templateId);
  return [
    "",
    "---",
    `[简历结构要求] 使用「${template.name}」模板，严格按以下章节与顺序输出 Markdown：`,
    template.sections.map((section, index) => `${index + 1}. ${section}`).join(" / "),
    "信息不足的章节保留标题并写“待补充”，不要新增模板之外的章节。",
  ].join("\n");
}

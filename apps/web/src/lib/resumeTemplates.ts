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

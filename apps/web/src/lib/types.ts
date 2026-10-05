/** OCR 分块（图片简历） */
export interface OcrBlock {
  text: string;
  confidence: number | null;
  reason: string | null;
}

/** 简历 */
export interface Resume {
  id: number;
  fileName: string;
  fileType: string;
  parsedName: string | null;
  parsedEmail: string | null;
  parsedPhone: string | null;
  rawText: string;
  createTime: string;
  ocrConfidence?: number | null;
  ocrBlocksJson?: string | null;
  /** OK / REVIEW：REVIEW 时需人工核对图片识别内容后才能诊断/匹配 */
  ocrStatus?: string | null;
  ocrBlocks?: OcrBlock[] | null;
}

/** AI 诊断响应 */
export interface DimensionScore {
  score: number;
  color: "green" | "yellow" | "red";
  label: string;
}

export interface DiagnosisResponse {
  resumeId: number;
  totalScore: number;
  completeness: DimensionScore;
  expression: DimensionScore;
  matchScore: DimensionScore;
  suggestions: string[];
}

/** 优化版本 */
export interface ResumeVersion {
  id: number;
  resumeId: number;
  versionName: string;
  targetJob: string | null;
  optimizedContent: string;
  matchScore: number | null;
  createTime: string;
}

/** 岗位 */
export interface Job {
  id: number;
  company: string;
  title: string;
  department: string | null;
  location: string | null;
  salaryRange: string | null;
  description: string | null;
  requirements: string | null;
  sourceUrl: string | null;
  createTime: string;
}

/** 匹配结果 */
export interface KeywordHit {
  keyword: string;
  hit: boolean;
}

export interface MatchDimensionDetails {
  keyword?: {
    total: number | null;
    hit: number | null;
    missingKeywords: string[] | null;
  } | null;
  semantic?: {
    score: number | null;
    reason: string | null;
    mode: string | null;
  } | null;
  hardRequirement?: {
    educationRequirement: string | null;
    educationMet: boolean | null;
    yearRequirement: string | null;
    yearMet: boolean | null;
    failedItems: string[] | null;
  } | null;
}

export interface MatchResult {
  id: number;
  resumeId: number;
  jobId: number;
  overallScore: number | null;
  keywordCoverage: number | null;
  semanticSimilarity: number | null;
  hardRequirementScore: number | null;
  matchExplanation: string | null;
  hardRequirementPassed: boolean | null;
  keywordHits: KeywordHit[] | null;
  missingKeywords: string[] | null;
  dimensionDetails: MatchDimensionDetails | null;
  createTime: string;
}

/** 定向优化响应 */
export interface TargetedOptimizeResponse {
  versionId: number;
  optimizedContent: string;
  gaps: string[] | null;
  changes: string[] | null;
  /** 优化前基线覆盖率（0-100）；降级路径下为 null */
  baselineCoverage: number | null;
  /** 最终采纳版本的覆盖率（0-100）；降级路径下为 null */
  finalCoverage: number | null;
  /** 优化闭环逐轮记录 */
  iterations: OptimizeIteration[] | null;
  /** 待用户确认的新增技能（模型写了、但原文找不到依据） */
  pendingSkills: AddedSkill[] | null;
  /** 疑似夸大的表述（模型把经历说得比原文更"重"） */
  pendingClaims: Overstatement[] | null;
  /** 是否降级执行（打分服务不可用时只做一次改写，没有分数与核查） */
  degraded: boolean | null;
  /** 优化流水线的节点耗时记录 */
  steps: StepRecord[] | null;
}

/** 优化流水线里的一个节点 */
export interface StepRecord {
  name: string;
  /** 是否调用大模型 */
  ai: boolean | null;
  durationMs: number | null;
  /** OK / DEGRADED / FAILED */
  status: string | null;
  detail: string | null;
}

/** 优化闭环中的一轮 */
export interface OptimizeIteration {
  round: number;
  /** 这一轮候选稿的关键词覆盖率 */
  keywordCoverage: number | null;
  /** 相比当前最优版本的增益（可能为负 = 改差了） */
  gain: number | null;
  /** 这一轮结束后还缺的词 */
  missingKeywords: string[] | null;
  /** 这一轮新补上的词 */
  addedKeywords: string[] | null;
  /** 是否被采纳（false = 改差了，已回滚） */
  kept: boolean;
}

/** 模型新写进简历、但原文里找不到依据的技能 */
export interface AddedSkill {
  skill: string;
  evidence: string | null;
  supported: boolean | null;
}

/** 疑似夸大的表述 */
export interface Overstatement {
  /** 改写稿里的那句话或短语 */
  claim: string;
  /** 原文对应表述；原文没有则为空 */
  original: string | null;
  /** 为什么算拔高 */
  reason: string | null;
  /** 代码核实结果：claim 是否确实出现在改写稿里 */
  confirmed: boolean | null;
}

/** 投递记录 */
export interface ApplicationRecord {
  id: number;
  resumeVersionId: number | null;
  jobId: number | null;
  appliedAt: string | null;
  channel: string | null;
  status: string;
  notes: string | null;
  createTime: string;
}

/** 用户 */
export interface UserInfo {
  userId: number;
  username: string;
  nickname: string | null;
}

export interface LoginResponse {
  token: string;
  user: UserInfo;
}

/** JD 智能解析结果 */
export interface RadarDimension {
  name: string;
  score: number;
}

export interface JdAnalysis {
  hardRequirements: string[];
  bonusPoints: string[];
  hiddenRequirements: string[];
  skills: string[];
  radar: RadarDimension[];
  summary: string | null;
}

/** 投递状态枚举 */
export const APP_STATUS = {
  pending: "待跟进",
  interviewing: "面试中",
  rejected: "已拒绝",
  no_response: "无回应",
  accepted: "已录用",
} as const;

export type AppStatus = keyof typeof APP_STATUS;

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

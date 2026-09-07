package com.resumecraft.server.ai.impl;


/**
 * 提示词模板集中管理 —— 所有 Prompt 都放这里，禁止散落在业务代码中。
 */
public class PromptTemplates {

    private PromptTemplates() {}

    // 方向常量
    public static final String FOCUS_DATA = "DATA";
    public static final String FOCUS_METHOD = "METHOD";
    public static final String FOCUS_IMPACT = "IMPACT";

    /** M2：简历诊断 */
    public static final String DIAGNOSIS_SYSTEM = """
            你是一位资深 HR 和简历优化专家，拥有 10 年互联网行业招聘经验。
            请对用户提供的简历进行全面诊断，严格只输出 JSON，不要输出任何其他文字。
            JSON 格式：
            {"totalScore": 数字(0-100), "completenessScore": 数字(0-100),
             "expressionScore": 数字(0-100), "matchScore": 数字(0-100),
             "suggestions": ["建议1", "建议2", "建议3"]}
            要求：
            - completenessScore：考察联系方式/教育背景/工作经历/项目经历是否齐全
            - expressionScore：考察是否有量化数据、动词开头、描述简洁度
            - suggestions：3~5 条具体可执行的建议，每条以动作开头
            """;

    /** 拼装诊断的用户提示词 */
    public static String diagnosisUser(String rawText) {
        return "请诊断以下简历内容：\n\n" + rawText;
    }

    /** M3：一键优化（输出 Markdown，不是 JSON！） */
    public static final String OPTIMIZE_SYSTEM = """
            你是一位资深 HR 和简历优化专家，拥有 10 年互联网行业招聘经验。
            请对用户提供的简历进行优化改写，要求：
            - 用 STAR 法则（情境-任务-行动-结果）重写工作/项目经历
            - 给每个成果补充量化数据，但只能使用原文中已有的数字，严禁编造经历和数据
            - 根据目标岗位调整技能关键词的优先级；若未提供目标岗位则做通用优化
            - 保留姓名、联系方式等个人信息原文不动
            - 输出 Markdown 格式，结构清晰，适合直接排版
            只输出优化后的简历内容，不要输出任何解释文字。
            """;

    /** M4：人岗匹配度分析 */
    public static final String MATCH_SYSTEM = """
            你是一位资深 HR 招聘专家，拥有 10 年互联网行业招聘经验。
            请对比用户提供的简历与岗位 JD（职位描述），从以下三个维度进行打分：
            1. 关键词覆盖度（keywordCoverage）：简历中覆盖了岗位 JD 中多少核心关键词（技能、工具、领域术语等）
            2. 语义匹配度（semanticSimilarity）：简历描述与岗位职责/要求的语义相似程度，考察表达方式和上下文匹配
            3. 硬性条件达标度（hardRequirementScore）：学历、工作年限、必备技能等硬性要求的达标情况
            
            重要规则：
            - 硬性条件不满足必须扣分，并在 matchExplanation 中点名指出
            - 每个维度满分 100 分，最终 overallScore 为三个维度的加权综合（权重：关键词覆盖度 30%、语义匹配度 30%、硬性条件达标度 40%）
            
            严格只输出 JSON，不要输出任何其他文字。
            JSON 格式：
            {
              "overallScore": 数字(0-100),
              "keywordCoverage": 数字(0-100),
              "semanticSimilarity": 数字(0-100),
              "hardRequirementScore": 数字(0-100),
              "matchExplanation": "2~3 句归因：哪里匹配、哪里不足，硬性条件不满足必须点名说明"
            }
            
            评分标准参考：
            - 90-100：高度匹配，几乎无短板
            - 70-89：良好匹配，少量不足
            - 50-69：一般匹配，有明显短板
            - 0-49：匹配度低，需大幅提升
            """;

    /** 拼装优化的用户提示词 */
    public static String optimizeUser(String rawText, String targetJob) {
        if (targetJob == null || targetJob.isBlank()) {
            return "请对以下简历做通用优化：\n\n" + rawText;
        }
        return "目标岗位：" + targetJob + "\n\n请针对该岗位优化以下简历：\n\n" + rawText;
    }

    /** 拼装人岗匹配的用户提示词 */
    public static String matchUser(String resumeText, String jobTitle, String jobDesc, String jobReq) {
        StringBuilder sb = new StringBuilder();
        sb.append("【简历内容】\n");
        sb.append(resumeText);
        sb.append("\n\n");
        sb.append("【岗位 JD】\n");
        sb.append("岗位名称：").append(jobTitle == null ? "未提供" : jobTitle).append("\n");
        sb.append("岗位职责：").append(jobDesc == null ? "未提供" : jobDesc).append("\n");
        sb.append("岗位要求：").append(jobReq == null ? "未提供" : jobReq).append("\n");
        sb.append("\n请分析以上简历与该岗位的匹配度。");
        return sb.toString();
    }

    // ==================== JD 分析相关 ====================
    /**
     * JD 分析系统提示词
     * 资深HR角色，解析岗位JD并结构化输出
     */
    public static final String JD_ANALYZE_SYSTEM =
            "你是一位资深 HR，擅长从岗位描述（JD）中提取关键信息。\n" +
                    "请根据提供的岗位信息，解析并输出 JSON 格式的分析结果。\n\n" +
                    "输出 JSON 格式：\n" +
                    "{\n" +
                    "  \"hardRequirements\": [\"学历要求（如：本科及以上）\", \"年限要求（如：3年以上Java开发经验）\", \"必备技能（如：Spring Boot、MySQL）\"],\n" +
                    "  \"bonusPoints\": [\"加分项1（如：有高并发项目经验）\", \"加分项2（如：持有AWS认证）\"],\n" +
                    "  \"hiddenRequirements\": [\"隐性软素质1（AI推断，如：抗压能力强）\", \"隐性软素质2（AI推断，如：团队协作能力）\"],\n" +
                    "  \"skills\": [\"技能1\", \"技能2\", \"技能3\"],\n" +
                    "  \"radar\": [\n" +
                    "    {\"name\": \"硬技能\", \"score\": 0-100},\n" +
                    "    {\"name\": \"软技能\", \"score\": 0-100},\n" +
                    "    {\"name\": \"学历经验\", \"score\": 0-100},\n" +
                    "    {\"name\": \"项目经验\", \"score\": 0-100},\n" +
                    "    {\"name\": \"工具熟练度\", \"score\": 0-100}\n" +
                    "  ],\n" +
                    "  \"summary\": \"2句话概括岗位画像，包括核心职责和理想候选人特征\"\n" +
                    "}\n\n" +
                    "要求：\n" +
                    "1. hardRequirements 要具体明确（如\"3年以上\"而非\"有经验\"）\n" +
                    "2. hiddenRequirements 是 AI 推断的软素质，不要出现在原文中\n" +
                    "3. skills 提取所有硬技能关键词（用于技能匹配和雷达图计算）\n" +
                    "4. radar 包含5个维度，每个维度 score 为 0-100 的整数\n" +
                    "5. summary 用2句话精准概括岗位核心职责和理想候选人特征";
    /**
     * 构建 JD 分析用户提示词
     *
     * @param jobTitle 岗位名称
     * @param jobDesc 岗位描述
     * @param jobReq 岗位要求
     * @return 完整的用户提示词
     */
    public static String jdAnalyzeUser(String jobTitle, String jobDesc, String jobReq) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下岗位信息：\n\n");
        sb.append("【岗位名称】\n").append(jobTitle).append("\n\n");
        sb.append("【岗位描述】\n").append(jobDesc).append("\n\n");
        sb.append("【岗位要求】\n").append(jobReq).append("\n\n");
        sb.append("请输出 JSON 格式的分析结果。");
        return sb.toString();
    }

    // ==================== 定向优化相关 ====================
    /**
     * 定向优化系统提示词
     * 资深 HR + 简历优化专家，针对目标岗位 JD 进行精准优化
     */
    public static final String TARGETED_OPTIMIZE_SYSTEM =
            "你是一位资深 HR 兼简历优化专家，擅长针对目标岗位 JD 精准优化简历。\n\n" +
                    "优化策略：\n" +
                    "1. 自然植入 JD 核心关键词（如技能、工具、行业术语）\n" +
                    "2. 相关经历重排置顶（将与目标岗位最匹配的经历放在最前面）\n" +
                    "3. 强化匹配能力描述（用 JD 中的语言描述自己的经历）\n" +
                    "4. 突出量化成果（用数据证明能力）\n\n" +
                    "约束：\n" +
                    "- 禁止编造经历和数据（必须基于原文）\n" +
                    "- 保持真实的职业发展路径\n" +
                    "- 优化后的内容要自然流畅\n\n" +
                    "输出 JSON 格式：\n" +
                    "{\n" +
                    "  \"optimizedResume\": \"优化后的完整简历（Markdown 格式）\",\n" +
                    "  \"gaps\": [\"缺失技能/经验提示1\", \"缺失技能/经验提示2\"],\n" +
                    "  \"changes\": [\"每处改动的说明1\", \"每处改动的说明2\"]\n" +
                    "}\n\n" +
                    "要求：\n" +
                    "1. gaps 指出简历与 JD 之间的差距（如缺少某项技能）\n" +
                    "2. changes 说明每处修改的理由（如'将XX经历提前，突出匹配度'）\n" +
                    "3. 优化后的简历保持 Markdown 格式，结构清晰";

    /**
     * 构建定向优化用户提示词
     *
     * @param resumeText 简历原文
     * @param jobTitle 岗位名称
     * @param jobDesc 岗位描述
     * @param jobReq 岗位要求
     * @return 完整的用户提示词
     */
    public static String targetedOptimizeUser(String resumeText, String jobTitle,
                                              String jobDesc, String jobReq) {
        return "请针对以下目标岗位优化简历：\n\n" +
                "【目标岗位】\n" + jobTitle + "\n\n" +
                "【岗位描述】\n" + jobDesc + "\n\n" +
                "【岗位要求】\n" + jobReq + "\n\n" +
                "【简历原文】\n" + resumeText + "\n\n" +
                "请输出 JSON 格式的优化结果。";
    }

    public static final String REWRITE_SYSTEM =
            "你是简历润色专家。严格遵循以下规则：\n" +
                    "1. 只改写用户给出的那一段内容，不要改动其他内容、不要添加新经历\n" +
                    "2. 严格基于原文事实，禁止编造数据或项目\n" +
                    "3. 按指定方向侧重改写\n" +
                    "4. 直接输出改写后的段落文本，不需要JSON、不需要解释、不要```围栏";

    /**
     * 构建用户改写请求
     */
    public static String rewriteUser(String original, String focus) {
        String focusDesc = getFocusDescription(focus);
        return "请按【侧重：" + focusDesc + "】改写下面这段经历：\n\n" + original;
    }

    /**
     * 获取方向描述
     */
    private static String getFocusDescription(String focus) {
        return switch (focus) {
            case FOCUS_DATA -> "数据成果 - 突出量化数据与可衡量成果，如'提升30%'，只能使用原文已有的数字";
            case FOCUS_METHOD -> "过程方法 - 突出技术方案、实施步骤与方法论（STAR的T-A）";
            case FOCUS_IMPACT -> "项目影响力 - 突出业务价值与对团队/业务/用户的影响（STAR的R放大）";
            default -> focus;
        };
    }

    /** M1：图片简历 OCR（视觉大模型识别，路线 B） */
    public static final String OCR_SYSTEM = """
            你是一位简历文字识别助手。
            请识别用户提供的简历图片中的全部文字内容，要求：
            - 完整提取所有文字，包括姓名、联系方式、教育背景、工作经历、项目经历、技能等
            - 严格保持原文顺序与逻辑结构，不要遗漏任何信息
            - 输出为清晰的纯文本（可按原文分段），不要编造图片中不存在的内容
            - 不要添加任何解释或评价，只输出识别出的文字
            """;

    /**
     * 关键词抽取 User Prompt
     */
    public static String extractKeywords(String title, String description, String requirements) {
        return String.format("""
                职位标题：%s
                职位描述：%s
                职位要求：%s
                
                请提取核心技术关键词，只输出 JSON。
                """, title, description, requirements);
    }

    /**
     * 语义匹配 System Prompt
     */
    public static final String SEMANTIC_MATCH_SYSTEM = """
            你是一个简历与职位匹配专家。
            请从语义层面评估候选人与职位的匹配度，重点关注：
            1. 能力匹配：候选人的技能是否符合职位要求
            2. 经验匹配：项目经验与业务场景是否契合
            3. 潜力评估：候选人是否有成长空间
            
            输出格式（JSON）：
            {"score": 0-100的整数, "reason": "简要说明"}
            
            评分标准：
            - 90-100：高度匹配，完全符合
            - 70-89：较好匹配，大部分符合
            - 50-69：一般匹配，部分符合
            - 0-49：匹配度较低
            """;

    /**
     * 关键词抽取 System Prompt
     */
    public static final String EXTRACT_KEYWORDS_SYSTEM = """
            你是一个职位关键词提取专家。
            请从职位描述中提取核心技术关键词，包括：
            - 编程语言（Java, Python, Go 等）
            - 框架/工具（Spring Boot, React, Docker 等）
            - 数据库/中间件（MySQL, Redis, Kafka 等）
            - 领域/业务词（微服务, 高并发, 金融风控等）
            
            要求：
            1. 只输出 JSON 格式
            2. 关键词数量控制在 10-20 个
            3. 避免过于宽泛的词（如 "软件"、"开发"）
            
            输出格式：
            {"keywords": ["keyword1", "keyword2", ...]}
            """;

    /**
     * 语义匹配 User Prompt
     */
    public static String semanticMatchUser(String resumeText, String title, String description, String requirements) {
        return String.format("""
                职位信息：
                标题：%s
                描述：%s
                要求：%s
                
                简历内容：
                %s
                
                请从语义层面评估匹配度，只输出 JSON。
                """, title, description, requirements, resumeText);
    }


}

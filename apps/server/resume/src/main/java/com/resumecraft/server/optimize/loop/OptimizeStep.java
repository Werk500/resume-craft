package com.resumecraft.server.optimize.loop;

public interface OptimizeStep {

    /** 日志与响应里的节点名，如 "baseline-score" */
    String name();

    /** 是否调用大模型：用于统计"AI 节点数 / 总耗时"，也便于按成本做取舍 */
    boolean ai();

    /** 失败是否可降级跳过（审计类 true；打分/组装类 false） */
    default boolean optional() { return false; }

    /**
     * 节点执行完后的状态，写进响应里的 steps 表；默认 OK。
     *
     * <p>为什么让节点自己上报而不是 pipeline 判断：有些节点是"没报错但走了降级分支"
     * ——例如基线打分遇到打分服务不可用时只标记降级、不抛异常。这种状态只有节点自己知道，
     * 让 pipeline 按节点名去猜会把语义写死在编排层。
     */
    default String outcome(OptimizeContext ctx) { return "OK"; }

    /** 从上下文读写；异常由 pipeline 统一处理 */
    void execute(OptimizeContext ctx) throws Exception;
}

package com.resumecraft.server.common.exception;

/**
 * 同一个会话正在处理上一轮消息时又收到新消息。
 *
 * <h3>为什么需要单独的类型</h3>
 * 这不是"服务器出错"，而是<b>预期内的并发拒绝</b>：同一个 sessionId 的两条消息
 * 如果同时进入模型，两边会读到同一份记忆再分别写回，后写的把先写的覆盖掉，
 * 用户看到的就是"我明明问了两句，它只回了一句"。所以用 Redis 会话锁把并发的
 * 第二条挡在门外，并明确告诉用户"上一轮还没回完"。
 *
 * <p>继承 {@code RuntimeException} 而不是直接用 {@code IllegalArgumentException}，
 * 是因为它需要一套<b>不同于参数校验</b>的响应形态：
 * 流式接口声明了 {@code produces = text/event-stream}，JSON 信封写不出去
 * （详见 {@code GlobalExceptionHandler#handleSessionBusy}），
 * 所以它必须独占一个处理器分支。
 *
 * <h3>HTTP 语义</h3>
 * 映射为 <b>409 Conflict</b>：请求本身没错，是和当前资源状态冲突了
 * （该会话正忙）。用 400 会让前端误判成"我发的参数有问题，改一改再发"。
 */
public class SessionBusyException extends RuntimeException {

    public SessionBusyException(String message) {
        super(message);
    }
}

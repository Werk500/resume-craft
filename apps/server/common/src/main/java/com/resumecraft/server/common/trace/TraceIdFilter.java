package com.resumecraft.server.common.trace;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)//决定过滤器的执行顺序,最先执行
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String HEADER = "X-Trace-Id";

    protected  void doFilterInternal(
            HttpServletRequest request,// 进来的请求
            @NonNull HttpServletResponse response,// 出去的响应
            @NonNull FilterChain chain)// 过滤器链，调用它才会走到下一个 Filter / Controller
            throws IOException, ServletException {
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        //放进MDC
        //MDC = Mapped Diagnostic Context，是日志框架的"线程局部变量"。
        //把值绑定到当前线程
        MDC.put(TRACE_ID, traceId);

        //回写响应头
        //把 traceId 塞进响应头，返回给前端
        response.setHeader(HEADER, traceId);

        try {
            //交给下一个 Filter / Controller
            chain.doFilter(request, response);
        } finally {
            //解决数据污染
            MDC.remove(TRACE_ID);// ← 关键：清掉，让线程归还池子前是干净的
        }


    }
}

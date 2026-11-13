package com.resumecraft.server.gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    private static final String HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(HEADER);

        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        String finalTraceId = traceId;

        //Spring WebFlux 里的请求对象是"不可变"的，不能直接改，
        //必须用 mutate() 克隆一份再改，最后 build() 生成新对象。
        ServerHttpRequest mutated = exchange
                .getRequest()
                .mutate()
                .header(HEADER, finalTraceId)
                .build();

        // 在响应提交前覆盖（下游服务也会回写该头，直接 set 会叠加成两个值）
        // beforeCommit 的语义：
        //   它只是"登记一个回调"，不是"现在执行"。
        //   Spring WebFlux 底层的响应对象会在"真正要把数据写给客户端之前"
        //   自动触发这个回调。执行顺序保证：
        //     ① 下游完全处理完；
        //     ② 响应头不再会被下游修改；
        //     ③ 你的 set 一定是最终值
        //
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HEADER, finalTraceId);
            return Mono.empty();
        });

        // exchange.mutate().request(mutated).build() 表示：
        //   把改过请求头的版本包装回 exchange，继续传递。
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

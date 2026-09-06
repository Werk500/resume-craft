package com.resumecraft.server.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 内部服务间调用认证过滤器
 * 验证请求头中的 Internal-Token
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    @Value("${app.internal-token:}")
    private String internalToken;

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        //获取请求路径
        String requestPath = request.getRequestURI();

        //判断是否要拦截
        if(!requestPath.startsWith("/internal")){
            filterChain.doFilter(request, response);//直接放行
            return;
        }

        //获取请求头中的Token
        String token = request.getHeader(INTERNAL_TOKEN_HEADER);
        if(token == null || !token.equals(internalToken)){
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");//告诉客户端返回的是 JSON 格式
            //写入json错误信息
            response.getWriter().write("{\"code\":403,\"message\":\"Invalid internal token\"}");
            return;
       }
        filterChain.doFilter(request, response);

    }
}

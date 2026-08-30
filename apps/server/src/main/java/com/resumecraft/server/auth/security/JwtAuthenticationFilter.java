package com.resumecraft.server.auth.security;

import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 1. 获取 Authorization 头
        String authHeader = request.getHeader("Authorization");
        //2.判断是否包含Bearer token
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            //3.解析token 获取userId
            Long userId = jwtUtil.parseUserId(token);
            if (userId != null) {
                // 4. 构造 Authentication 对象存入 SecurityContextHolder
                List<GrantedAuthority> authorities = Collections
                        .singletonList(new SimpleGrantedAuthority("USER")); // 身份：普通用户

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId, null, authorities);

                // 设置请求详情（可选）
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 存入 SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("JWT 认证成功: userId={}, path={}", userId, request.getRequestURI());

            }
        }
        filterChain.doFilter(request, response);
    }
}

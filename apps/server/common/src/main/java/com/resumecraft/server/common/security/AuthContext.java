package com.resumecraft.server.common.security;


import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 认证上下文工具类
 * 用于获取当前登录用户信息
 */
public class AuthContext {


    /**
     * 获取当前用户ID
     * @return
     */
    public static Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        throw new IllegalArgumentException("未登录");

    }
}

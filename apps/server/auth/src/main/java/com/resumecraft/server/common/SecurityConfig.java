package com.resumecraft.server.common;

import com.resumecraft.server.auth.security.JwtAuthenticationFilter;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置（MVP 阶段全放行，M5 接入 JWT 认证时在此收紧）。
 *
 * 注意：
 *  - 引入 spring-boot-starter-security 后若不配置，所有接口默认 401；
 *  - frameOptions(sameOrigin) 为了让 /h2-console 能在 iframe 中打开。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Resource
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // MVP 无状态接口，暂不需要 CSRF（M5 上 JWT 时按需调整）
                .csrf(AbstractHttpConfigurer::disable)

                // 无状态 Session（JWT 不需要 Session）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // 授权规则（MVP 联调期）
                .authorizeHttpRequests(auth -> auth
                        //放行认证相关接口（注册、登录）
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        //放行 H2 Console（开发调试用）
                        .requestMatchers("/h2-console/**").permitAll()
                        // ⚠️ 其他接口暂时全部放行（MVP 联调期）
                        // 等前端能跑通认证了，再改成 .authenticated()
                        .anyRequest().permitAll())
                // 允许 h2-console 的 iframe
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                //添加 JWT 过滤器（在 UsernamePasswordAuthenticationFilter 之前）
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

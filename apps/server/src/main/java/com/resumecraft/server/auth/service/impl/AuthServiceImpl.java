package com.resumecraft.server.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumecraft.server.auth.domain.SysUser;
import com.resumecraft.server.auth.domain.SysUserMapper;
import com.resumecraft.server.auth.dto.LoginRequest;
import com.resumecraft.server.auth.dto.LoginResponse;
import com.resumecraft.server.auth.dto.RegisterRequest;
import com.resumecraft.server.auth.dto.UserInfoResponse;
import com.resumecraft.server.auth.security.JwtUtil;
import com.resumecraft.server.auth.service.AuthService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private JwtUtil jwtUtil;

    @Override
    public UserInfoResponse register(RegisterRequest request) {
        //1.检查用户名是否存在
        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername()));
        if (count > 0) {
            throw new IllegalArgumentException("用户名已注册");
        }

        //2.Bcrypt加密密码
        String encoded = passwordEncoder.encode(request.getPassword());

        SysUser sysUser = SysUser.builder()
                .username(request.getUsername())
                .password(encoded)
                .nickname(request.getNickname())
                .build();

        sysUserMapper.insert(sysUser);

        log.info("用户注册成功: userId={}, username={}", sysUser.getId(), sysUser.getUsername());


        // 返回用户信息（不含密码）
        return UserInfoResponse.from(sysUser);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser sysUser = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (sysUser == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), sysUser.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(String.valueOf(sysUser.getId()), sysUser.getUsername());
        log.info("用户登录成功: userId={}, username={}", sysUser.getId(), sysUser.getUsername());

        return LoginResponse.from(token,sysUser);

    }

    @Override
    public UserInfoResponse me() {
        //从 SecurityContext 获取 Authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // principal 是 filter 里塞入的 Long userId；不是 Long 说明没带有效 token
        if (authentication == null || !(authentication.getPrincipal() instanceof Long)) {
            throw new IllegalArgumentException("未登录");
        }

        Long userId = (Long) authentication.getPrincipal();
        SysUser sysUser = sysUserMapper.selectById(userId);
        if (sysUser == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        log.debug("获取当前用户成功: userId={}, username={}", userId, sysUser.getUsername());
        return UserInfoResponse.from(sysUser);
    }
}

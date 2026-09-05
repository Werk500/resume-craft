package com.resumecraft.server.auth.controller;



import com.resumecraft.server.auth.domain.SysUser;
import com.resumecraft.server.auth.dto.LoginRequest;
import com.resumecraft.server.auth.dto.LoginResponse;
import com.resumecraft.server.auth.dto.RegisterRequest;
import com.resumecraft.server.auth.dto.UserInfoResponse;
import com.resumecraft.server.auth.service.AuthService;
import com.resumecraft.server.common.ApiResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;



@Slf4j
@RestController
@RequestMapping("/api/v1/auth/")
public class AuthController {
    @Resource
    private AuthService authService;

    /**
     * 用户注册
     * @param request
     * @return
     */
    @PostMapping("register")
    public ApiResponse<UserInfoResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    /**
     * 用户登录
     * @param request
     * @return
     */
    @PostMapping("login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }


    /**
     * 获取当前用户信息
     * @return
     */
    @GetMapping("me")
    public ApiResponse<UserInfoResponse> me(){

        UserInfoResponse user = authService.me();
        return ApiResponse.ok(user);
    }
}

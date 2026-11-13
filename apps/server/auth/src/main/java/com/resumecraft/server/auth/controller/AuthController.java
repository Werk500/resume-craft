package com.resumecraft.server.auth.controller;



import com.resumecraft.server.auth.domain.SysUser;
import com.resumecraft.server.auth.dto.LoginRequest;
import com.resumecraft.server.auth.dto.LoginResponse;
import com.resumecraft.server.auth.dto.RegisterRequest;
import com.resumecraft.server.auth.dto.UserInfoResponse;
import com.resumecraft.server.auth.security.LoginAttemptGuard;
import com.resumecraft.server.auth.service.AuthService;
import com.resumecraft.server.common.ApiResponse;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;



@Slf4j
@RestController
@RequestMapping("/api/v1/auth/")
public class AuthController {
    @Resource
    private AuthService authService;
    @Resource
    private LoginAttemptGuard loginAttemptGuard;

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
     * @param request1
     * @param request2
     * @return
     */
    @PostMapping("login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request1,
                                            HttpServletRequest request2) {

        String ip = resolveClientIp(request2);
        loginAttemptGuard.checkAndRecord(request1.getUsername(),ip);

        LoginResponse login = authService.login(request1);

        loginAttemptGuard.reset(request1.getUsername(),ip);

        return ApiResponse.ok(login);
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

    @PostMapping("logout")
    public ApiResponse<Void> logout(
            @RequestHeader(
                    value = HttpHeaders.AUTHORIZATION,
                    required = false
            ) String authorizationHeader
    ) {
        authService.logout(authorizationHeader);
        return ApiResponse.ok();
    }

    private String resolveClientIp(HttpServletRequest request){
        String ip = request.getHeader("X-Forwarded-For");
        if(isValidIp(ip)){
            int index = ip.indexOf(",");
            if (index != -1) {
                return ip.substring(0, index).trim();
            }
            return ip.trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        ip = request.getRemoteAddr();

        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }

        return ip;
    }

    private boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
    }
}

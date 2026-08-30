package com.resumecraft.server.auth.service;

import com.resumecraft.server.auth.domain.SysUser;
import com.resumecraft.server.auth.dto.LoginRequest;
import com.resumecraft.server.auth.dto.LoginResponse;
import com.resumecraft.server.auth.dto.RegisterRequest;
import com.resumecraft.server.auth.dto.UserInfoResponse;
import jakarta.validation.Valid;

public interface AuthService {
    UserInfoResponse register(@Valid RegisterRequest request);

    LoginResponse login(@Valid LoginRequest request);

    UserInfoResponse me();
}

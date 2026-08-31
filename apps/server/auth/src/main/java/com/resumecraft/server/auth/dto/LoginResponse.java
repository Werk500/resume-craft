package com.resumecraft.server.auth.dto;


import com.resumecraft.server.auth.domain.SysUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private UserInfoResponse user;

    /**
     * 静态工厂方法：从 token 和 SysUser 创建
     */
    public static LoginResponse from(String token, SysUser user) {
        return LoginResponse.builder()
                .token(token)
                .user(UserInfoResponse.from(user))
                .build();
    }
}

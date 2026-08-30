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
public class UserInfoResponse {

    private Long userId;
    private String username;
    private String nickname;

    public static UserInfoResponse from(SysUser user) {
        return new UserInfoResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname()
        );
    }

}

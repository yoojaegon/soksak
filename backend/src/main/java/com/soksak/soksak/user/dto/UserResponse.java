package com.soksak.soksak.user.dto;

import com.soksak.soksak.user.User;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserResponse {
    private final Long id;
    private final String email;
    private final String loginId;
    private final String nickname;
    private final LocalDateTime createdAt;

    private UserResponse(Long id, String email, String loginId, String nickname, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.loginId = loginId;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getLoginId(),
                user.getNickname(),
                user.getCreatedAt()
        );
    }
}
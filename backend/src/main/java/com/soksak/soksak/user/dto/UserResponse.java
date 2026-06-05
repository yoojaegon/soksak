package com.soksak.soksak.user.dto;

import com.soksak.soksak.user.User;

import java.time.LocalDateTime;


public record UserResponse (
        Long id,
        String email,
        String loginId,
        String nickname,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user){
        return new UserResponse(user.getId(), user.getEmail(), user.getLoginId(),
                user.getNickname(), user.getCreatedAt());
    }
}

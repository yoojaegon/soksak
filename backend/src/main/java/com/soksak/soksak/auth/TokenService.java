package com.soksak.soksak.auth;

import com.soksak.soksak.config.jwt.JwtTokenProvider;
import com.soksak.soksak.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;


}

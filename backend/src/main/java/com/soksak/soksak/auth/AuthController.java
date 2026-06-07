package com.soksak.soksak.auth;

import com.soksak.soksak.auth.dto.LoginRequest;
import com.soksak.soksak.auth.dto.ReIssueRequest;
import com.soksak.soksak.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final TokenService tokenService;

    // 초기 토큰 생성
    @PostMapping("/auth/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request){
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // 토큰 재생성
    @PostMapping("/auth/reissue")
    public ResponseEntity<TokenResponse> reissue(@Valid @RequestBody ReIssueRequest request) {
        TokenResponse response = tokenService.reissue(request.refreshToken());
        return ResponseEntity.ok(response);
    }
}

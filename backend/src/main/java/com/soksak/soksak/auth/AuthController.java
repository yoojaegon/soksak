package com.soksak.soksak.auth;

import com.soksak.soksak.auth.dto.LoginRequest;
import com.soksak.soksak.auth.dto.LogoutRequest;
import com.soksak.soksak.auth.dto.ReIssueRequest;
import com.soksak.soksak.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final TokenService tokenService;

    // 아이디와 비밀번호를 확인하고 액세스 토큰과 리프레시 토큰을 발급한다.
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request){
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // 리프레시 토큰으로 두 토큰을 새로 발급한다. 한 번 쓴 리프레시 토큰은 다시 쓸 수 없다.
    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(@Valid @RequestBody ReIssueRequest request) {
        TokenResponse response = tokenService.reissue(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    // 리프레시 토큰을 DB에서 지운다.
    // 이미 발급된 액세스 토큰은 서버에 기록이 없어서 만료될 때까지 그대로 쓸 수 있다.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}

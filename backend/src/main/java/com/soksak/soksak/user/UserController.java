package com.soksak.soksak.user;

import com.soksak.soksak.auth.AuthUser;
import com.soksak.soksak.user.dto.ChangePasswordRequest;
import com.soksak.soksak.user.dto.UserResponse;
import com.soksak.soksak.user.dto.CreateUserRequest;
import com.soksak.soksak.user.dto.UpdateChatDefaultsRequest;
import com.soksak.soksak.user.dto.UpdateUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    /** 회원가입. 로그인 전에 부르는 경로라 SecurityConfig에서 POST만 열어 둔다. */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUser(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal AuthUser me) {
        return ResponseEntity.ok(userService.getMe(me.id()));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(@AuthenticationPrincipal AuthUser me,
                                                 @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(me.id(), request));
    }

    // 채팅 기본값(모델·추론). 설정 화면과 채팅방 패널의 "기본값" 범위가 같이 쓴다.
    @PatchMapping("/me/chat-defaults")
    public ResponseEntity<UserResponse> updateChatDefaults(@AuthenticationPrincipal AuthUser me,
                                                           @RequestBody UpdateChatDefaultsRequest request) {
        return ResponseEntity.ok(userService.updateChatDefaults(me.id(), request));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUser me,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(me.id(), request);
        return ResponseEntity.noContent().build();
    }
}

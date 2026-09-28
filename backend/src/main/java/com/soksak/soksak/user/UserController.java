package com.soksak.soksak.user;

import com.soksak.soksak.user.dto.ChangePasswordRequest;
import com.soksak.soksak.user.dto.UserResponse;
import com.soksak.soksak.user.dto.CreateUserRequest;
import com.soksak.soksak.user.dto.UpdateUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    // 회원가입 = 유저 생성. 비로그인 경로라 SecurityConfig에서 POST만 열어 둔다.
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResponse.from(user));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(userService.getMe(authentication.getName()));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(Authentication authentication,
                                                 @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(authentication.getName(), request));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(Authentication authentication,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}

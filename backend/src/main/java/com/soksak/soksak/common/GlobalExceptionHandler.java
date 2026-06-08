package com.soksak.soksak.common;

import com.soksak.soksak.auth.InvalidTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    // 무효, 만료, 존재하지 않는 refresh 토큰 -> 401
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException e) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    // 로그인시 아이디, 비번 불일치 -> 401
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e) {
        return build(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    // @Vaild 검증 실패(@NotBlank 등) -> 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("잘못된 요청입니다.");
        return build(HttpStatus.BAD_REQUEST, message);
    }
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message){
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), message));
    }
}

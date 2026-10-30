package com.soksak.soksak.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

// 모든 에러 응답의 공통 형식. 프론트는 status보다 code(ErrorCode 이름)를 보고 분기한다.
@Getter
public class ErrorResponse {
    private final String code;
    private final String message;
    private final int status;
    private final String path;
    private final LocalDateTime timestamp;
    // 검증 실패일 때만 채운다. 비어 있으면 JSON에서 빠진다.
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<FieldErrorDetail> errors;

    // 잘못 들어온 값은 넣지 않는다. 비밀번호가 응답과 로그에 그대로 남기 때문이다.
    public record FieldErrorDetail(String field, String reason) {}

    private ErrorResponse(String code, String message, int status, String path, List<FieldErrorDetail> errors) {
        this.code = code;
        this.message = message;
        this.status = status;
        this.path = path;
        this.timestamp = LocalDateTime.now();
        this.errors = errors;
    }

    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return of(errorCode, path, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String path, List<FieldErrorDetail> errors) {
        return new ErrorResponse(
                errorCode.name(),
                errorCode.getMessage(),
                errorCode.getStatus().value(),
                path,
                errors
        );
    }
}

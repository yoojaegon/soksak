package com.soksak.soksak.common;

import lombok.Getter;

import java.time.LocalDateTime;

// 모든 에러 응답의 공통 형식. 프론트는 status보다 code(ErrorCode 이름)를 보고 분기한다.
@Getter
public class ErrorResponse {
    private final String code;
    private final String message;
    private final int status;
    private final String path;
    private final LocalDateTime timestamp;

    private ErrorResponse(String code, String message, int status, String path) {
        this.code = code;
        this.message = message;
        this.status = status;
        this.path = path;
        this.timestamp = LocalDateTime.now();
    }

    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return new ErrorResponse(
                errorCode.name(),
                errorCode.getMessage(),
                errorCode.getStatus().value(),
                path
        );
    }
}

package com.soksak.soksak.common;

import lombok.Getter;

// 서비스가 규칙 위반(없는 리소스, 권한 없음, 크레딧 부족 등)을 감지했을 때 직접 던지는 예외.
// GlobalExceptionHandler가 ErrorCode에 적힌 상태와 메시지로 응답을 만든다.
@Getter
public class BusinessException extends RuntimeException{
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

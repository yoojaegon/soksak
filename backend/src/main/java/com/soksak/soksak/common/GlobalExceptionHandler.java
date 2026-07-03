package com.soksak.soksak.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException e,
            HttpServletRequest request
    ) {
        return build(e.getErrorCode(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        return build(ErrorCode.INVALID_INPUT, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException e,
            HttpServletRequest request
    ) {
        return build(ErrorCode.LOGIN_FAILED, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException e,
            HttpServletRequest request
    ) {
        String sqlState = extractSqlState(e);
        ErrorCode code = switch (sqlState == null ? "" : sqlState) {
            case "23505" -> ErrorCode.DUPLICATE_VALUE;
            case "23502" -> ErrorCode.INVALID_INPUT;
            case "23503" -> ErrorCode.DATA_CONSTRAINT_VIOLATION;
            default -> {
                log.warn("분류되지 않은 무결성 위반 sqlState={}", sqlState, e);
                yield ErrorCode.DUPLICATE_VALUE;
            }
        };
        return build(code, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e,
            HttpServletRequest request
    ) {
        log.error("예상치 못한 에러 발생", e);
        return build(ErrorCode.INTERNAL_ERROR, request);
    }

    private String extractSqlState(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            t = t.getCause();
        }
        return null;
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, HttpServletRequest request) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }
}

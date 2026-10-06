package com.soksak.soksak.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    // 서비스가 의도적으로 던진 에러. 상태·메시지는 ErrorCode가 들고 있으니 그대로 옮기기만 한다.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException e,
            HttpServletRequest request
    ) {
        return build(e.getErrorCode(), request);
    }

    // @Valid 검증 실패(@NotBlank, @Size 등). 어느 필드가 틀렸는지는 응답에 싣지 않고 400 한 종류로 묶는다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        return build(ErrorCode.INVALID_INPUT, request);
    }

    // 본문을 읽다가 실패한 경우(깨진 JSON, 타입 불일치, enum에 없는 값 등). @Valid 검증은
    // 바인딩이 끝난 뒤에 도는 거라 여기까지 온 요청은 MethodArgumentNotValidException을
    // 타지 않는다 — 안 잡아 주면 잘못 보낸 값이 전부 500으로 나간다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException e,
            HttpServletRequest request
    ) {
        return build(ErrorCode.INVALID_INPUT, request);
    }

    // 로그인 실패(AuthService.login의 authenticate). 아이디가 없는 경우도 Spring Security가
    // BadCredentials로 감춰 주므로, 아이디·비번 중 무엇이 틀렸는지 구분되지 않는 401 하나로 나간다.
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException e,
            HttpServletRequest request
    ) {
        return build(ErrorCode.LOGIN_FAILED, request);
    }

    // 멀티파트 크기 초과는 컨트롤러에 닿기 전에 터지므로 여기서 413으로 바꿔준다(기본은 500).
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSize(
            MaxUploadSizeExceededException e,
            HttpServletRequest request
    ) {
        return build(ErrorCode.IMAGE_TOO_LARGE, request);
    }

    // DB 제약 위반. 서비스가 미리 걸러내지 못한 경우(동시 요청 등)의 마지막 그물로,
    // SQLSTATE로 종류를 가른다: 23505 UNIQUE, 23502 NOT NULL, 23503 FK, 23514 CHECK.
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
            // CHECK 위반은 사용자 잘못이 아니라 우리가 스키마가 금지한 값을 쓴 것이다.
            // 409 "이미 사용 중인 값입니다"로 덮으면 원인을 가리킨 곳이 로그 한 줄뿐이 된다.
            case "23514" -> {
                log.error("CHECK 제약 위반 — 엔티티와 스키마가 어긋났다", e);
                yield ErrorCode.INTERNAL_ERROR;
            }
            default -> {
                log.warn("분류되지 않은 무결성 위반 sqlState={}", sqlState, e);
                yield ErrorCode.DUPLICATE_VALUE;
            }
        };
        return build(code, request);
    }

    // 매핑이 없는 경로. 안 잡아 주면 handleException으로 떨어져 오타 URL 하나에 500 + 스택
    // 30줄이 찍힌다(실제로 /api/chat-rooms를 잘못 쳤다가 확인). 클라이언트 실수는 404 한 줄이면 된다.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException e,
            HttpServletRequest request
    ) {
        return build(ErrorCode.ENDPOINT_NOT_FOUND, request);
    }

    // 위 핸들러 어디에도 안 걸린 나머지 전부. 더 구체적인 타입의 핸들러가 항상 먼저 선택되므로
    // 여기 오는 건 진짜 예상 못 한 에러뿐이다 — 스택은 로그에만 남기고 응답에는 500 한 줄만 준다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e,
            HttpServletRequest request
    ) {
        log.error("예상치 못한 에러 발생", e);
        return build(ErrorCode.INTERNAL_ERROR, request);
    }

    // DataIntegrityViolationException은 Spring이 감싼 껍데기라 SQLSTATE가 없다.
    // cause 사슬을 타고 내려가 JDBC 드라이버가 던진 원본 SQLException에서 꺼낸다.
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

    // 모든 핸들러가 이 한 곳으로 모이므로, 여기 한 줄이면 4xx 전 경로가 덮인다.
    // 5xx는 스택이 있어야 쓸모가 있어 각 핸들러가 이미 error로 찍는다 — 여기서 또 찍으면 두 줄이 된다.
    // 단 인증 실패·리프레시 재사용처럼 맥락(loginId 등)이 필요한 4xx는 호출부가 한 줄을 더 남긴다.
    // 이 줄은 "무슨 요청이 어떤 코드로 끝났나", 저쪽 줄은 "왜"라서 서로 대체가 안 된다.
    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, HttpServletRequest request) {
        HttpStatus status = errorCode.getStatus();
        if (status.is4xxClientError()) {
            log.warn("{} {} {} code={}", status.value(), request.getMethod(),
                    request.getRequestURI(), errorCode.name());
        }
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }
}

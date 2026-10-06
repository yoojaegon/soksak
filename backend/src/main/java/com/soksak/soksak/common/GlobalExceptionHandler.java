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
// 컨트롤러 밖으로 나온 예외를 모두 ErrorResponse 형식으로 바꿔 준다.
// 메서드를 직접 부르는 곳은 없고, 예외 타입에 맞는 @ExceptionHandler를 Spring이 골라서 호출한다.
// JwtFilter처럼 컨트롤러에 닿기 전에 난 에러는 여기로 오지 않는다(RestAuthenticationEntryPoint가 처리).
public class GlobalExceptionHandler {
    // 서비스가 직접 던진 BusinessException. 상태·메시지는 ErrorCode가 들고 있으니 그대로 옮기기만 한다.
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

    // 요청 본문을 읽지 못한 경우(깨진 JSON, 타입 불일치, enum에 없는 값 등).
    // @Valid 검증은 본문을 다 읽은 뒤에 돌아서 이런 요청은 위 핸들러로 가지 않는다.
    // 여기서 안 잡으면 잘못된 요청이 전부 500으로 나간다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException e,
            HttpServletRequest request
    ) {
        return build(ErrorCode.INVALID_INPUT, request);
    }

    // 로그인 실패(AuthService.login).
    // 아이디가 없는 경우도 Spring Security가 같은 예외로 바꿔 주기 때문에,
    // 아이디와 비밀번호 중 뭐가 틀렸는지 알 수 없는 401 하나로 나간다.
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException e,
            HttpServletRequest request
    ) {
        return build(ErrorCode.LOGIN_FAILED, request);
    }

    // 업로드 파일 크기 초과. 컨트롤러에 닿기 전에 터져서 그냥 두면 500이 나가므로 413으로 바꿔 준다.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSize(
            MaxUploadSizeExceededException e,
            HttpServletRequest request
    ) {
        return build(ErrorCode.IMAGE_TOO_LARGE, request);
    }

    // DB 제약 위반. 서비스에서 미리 못 거른 경우(동시 요청 등)를 마지막으로 잡는 곳이다.
    // SQLSTATE로 종류를 나눈다. 23505 UNIQUE, 23502 NOT NULL, 23503 FK, 23514 CHECK.
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
            // CHECK 위반은 사용자 잘못이 아니라 우리 코드가 스키마에 안 맞는 값을 넣은 것이다.
            // 409로 응답하면 사용자 탓처럼 보여서 원인을 찾기 어려우므로 500으로 보내고 error 로그를 남긴다.
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

    // 없는 경로로 온 요청. 클라이언트 실수라 500이 아니라 404로 응답한다.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException e,
            HttpServletRequest request
    ) {
        return build(ErrorCode.ENDPOINT_NOT_FOUND, request);
    }

    // 위 핸들러에 안 걸린 나머지 전부. 더 구체적인 핸들러가 항상 먼저 선택되니
    // 여기로 오는 건 예상 못 한 에러뿐이다. 스택은 로그에만 남기고 응답은 500만 준다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e,
            HttpServletRequest request
    ) {
        log.error("예상치 못한 에러 발생", e);
        return build(ErrorCode.INTERNAL_ERROR, request);
    }

    // DataIntegrityViolationException은 Spring이 한 번 감싼 예외라 SQLSTATE가 없다.
    // cause를 따라 내려가서 DB 드라이버가 던진 원본 SQLException에서 꺼낸다.
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

    // 모든 핸들러가 여기로 모이므로 4xx 로그는 이 한 곳에서 다 남긴다.
    // 5xx는 스택이 있어야 의미가 있어서 에러를 던지는 쪽이나 handleException에서 따로 찍는다.
    // 여기서도 찍으면 같은 에러가 두 줄이 된다.
    // 로그인 실패처럼 loginId 같은 정보가 필요한 4xx는 던지는 쪽에서 한 줄을 더 남긴다.
    // 이 줄은 "어떤 요청이 어떤 코드로 끝났는지", 그쪽 줄은 "왜"를 남기는 거라 둘 다 필요하다.
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

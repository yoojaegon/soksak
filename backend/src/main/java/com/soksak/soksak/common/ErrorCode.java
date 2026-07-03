package com.soksak.soksak.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    DUPLICATE_VALUE(HttpStatus.CONFLICT, "이미 사용 중인 값입니다."),
    DATA_CONSTRAINT_VIOLATION(HttpStatus.CONFLICT, "관련 데이터가 있어 처리할 수 없습니다."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 응답을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요."),

    // 인증
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),

    // 유저
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),


    // 캐릭터
    CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "캐릭터를 찾을 수 없습니다."),
    CHARACTER_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 캐릭터가 아닙니다."),

    // 채팅방
    CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    CHATROOM_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 채팅방이 아닙니다."),

    // 메시지
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다."),
    MESSAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 방의 메시지가 아닙니다."),

    // 유저 페르소나
    USER_PERSONA_NOT_FOUND(HttpStatus.NOT_FOUND, "페르소나를 찾을 수 없습니다."),
    USER_PERSONA_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 페르소나가 아닙니다."),
    USER_PERSONA_LAST_ONE(HttpStatus.BAD_REQUEST, "마지막 페르소나는 삭제할 수 없습니다."),

    // 로어
    LORE_NOT_FOUND(HttpStatus.NOT_FOUND, "로어를 찾을 수 없습니다."),
    LORE_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 캐릭터의 로어가 아닙니다.");

    private final HttpStatus status;
    private final String message;
}
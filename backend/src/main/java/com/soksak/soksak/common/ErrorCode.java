package com.soksak.soksak.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청하신 경로를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    DUPLICATE_VALUE(HttpStatus.CONFLICT, "이미 사용 중인 값입니다."),
    DATA_CONSTRAINT_VIOLATION(HttpStatus.CONFLICT, "관련 데이터가 있어 처리할 수 없습니다."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 응답을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요."),

    // 인증
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),

    // 유저
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    // 401이 아니라 400 — 프론트는 401을 토큰 만료로 보고 재발급/로그아웃을 탄다.
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "지금과 다른 비밀번호를 입력해 주세요."),

    // 마디(소모 재화)
    // 402를 쓰는 이유: 403(권한 없음)이 아니라 "값을 치르면 되는 상태"라서 의미가 정확하다.
    INSUFFICIENT_CREDIT(HttpStatus.PAYMENT_REQUIRED, "마디가 모자라요."),
    UNKNOWN_CREDIT_PACK(HttpStatus.BAD_REQUEST, "없는 마디 묶음입니다."),


    // 캐릭터
    CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "캐릭터를 찾을 수 없습니다."),
    CHARACTER_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 캐릭터가 아닙니다."),
    // 신고 누적으로 숨겨진 캐릭터 — 기존 방은 계속 쓰고 새 방만 막는다.
    CHARACTER_HIDDEN(HttpStatus.FORBIDDEN, "지금은 이 캐릭터와 새 대화를 시작할 수 없습니다."),

    // 채팅방
    CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    CHATROOM_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 채팅방이 아닙니다."),

    // 메시지
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다."),
    MESSAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 방의 메시지가 아닙니다."),
    ROOM_BUSY(HttpStatus.CONFLICT, "이미 응답을 생성 중입니다. 잠시 후 다시 시도해 주세요."),

    // 신고
    REPORT_OWN_CHARACTER(HttpStatus.BAD_REQUEST, "본인 캐릭터는 신고할 수 없습니다."),
    REPORT_NOT_ASSISTANT(HttpStatus.BAD_REQUEST, "캐릭터의 응답만 신고할 수 있습니다."),

    // 유저 페르소나
    USER_PERSONA_NOT_FOUND(HttpStatus.NOT_FOUND, "페르소나를 찾을 수 없습니다."),
    USER_PERSONA_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 페르소나가 아닙니다."),
    USER_PERSONA_LAST_ONE(HttpStatus.BAD_REQUEST, "마지막 페르소나는 삭제할 수 없습니다."),

    // 로어
    LORE_NOT_FOUND(HttpStatus.NOT_FOUND, "로어를 찾을 수 없습니다."),
    LORE_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 캐릭터의 로어가 아닙니다."),

    // 이미지 업로드
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "jpg·png·webp 이미지만 올릴 수 있습니다."),
    // 모더레이션이 첫 프레임만 보므로 움짤(gif·움직이는 webp·apng)은 받지 않는다.
    ANIMATED_IMAGE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "움직이는 이미지는 올릴 수 없습니다. 정지 이미지로 올려주세요."),
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "이미지가 너무 큽니다. 5MB 이하로 올려주세요."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지를 저장하지 못했습니다."),
    // 어느 항목에 걸렸는지는 알려주지 않는다 — 알려주면 기준점에 맞춰 깎아 가며 우회를 시도할 수 있다.
    IMAGE_REJECTED(HttpStatus.BAD_REQUEST, "운영 정책에 맞지 않는 이미지입니다. 다른 이미지로 시도해주세요."),
    IMAGE_MODERATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 이미지를 검사할 수 없습니다. 잠시 후 다시 시도해주세요."),
    // 한도는 uploads.limit.* (기본 1시간 10장·24시간 30장).
    IMAGE_UPLOAD_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "이미지를 너무 자주 올렸습니다. 잠시 후 다시 시도해주세요."),
    // 최근 24시간 차단이 uploads.limit.block-lock회 이상이면 업로드만 잠근다(롤링이라 저절로 풀린다).
    IMAGE_UPLOAD_LOCKED(HttpStatus.FORBIDDEN, "정책에 맞지 않는 이미지가 반복되어 당분간 이미지를 올릴 수 없습니다."),
    // 전체 사용자 합산 상한(uploads.limit.global-per-day) — 여러 계정으로 개인 한도를 우회하는 걸 막는 최후선.
    IMAGE_UPLOAD_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "지금은 이미지를 올릴 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;
}
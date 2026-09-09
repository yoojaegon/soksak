package com.soksak.soksak.chatRoom.dto;

// 밖으로 나가는 표현은 여전히 문자열 하나다. 안에서 조각 리스트로 바뀐 것이 프론트 계약을
// 건드리지 않게 하는 지점 — 조각을 고르고 잇는 일은 ChatRoomService/SummarySelector가 한다.
public record SummaryResponse(
    String summary
) {
}

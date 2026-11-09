package com.soksak.soksak.chatRoom.dto;

import com.soksak.soksak.aiClient.ThinkingLevel;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 방 설정 부분 수정. 보낸 필드만 바꾸고 null인 필드는 그대로 둔다.
// 그래서 값을 null로 되돌리는 요청은 표현할 수 없는데, model·thinkingLevel의 null은
// "아직 안 고름"이라는 서버 초기 상태 전용이라 API로 받으면 안 되는 값이기도 하다.
// 필드가 전부 null인 빈 본문은 서비스에서 400으로 막는다.
public record UpdateChatRoomRequest(
        // 기본 제목은 캐릭터 이름(30자)이고, 사용자가 이름 뒤에 덧붙일 여유를 둔다.
        // null은 통과시키고 공백뿐인 값만 막으려고 @NotBlank 대신 @Pattern을 쓴다.
        // (?U)가 없으면 \S가 ASCII 공백만 공백으로 봐서 전각 공백(U+3000)만 있는 제목이 통과한다.
        @Pattern(regexp = "(?U).*\\S.*", message = "방 이름은 비워둘 수 없습니다.")
        @Size(max = 40, message = "방 이름은 40자 이하여야 합니다.")
        String title,

        // 카탈로그에 있는지는 서비스에서 확인한다.
        String model,

        ThinkingLevel thinkingLevel,

        Boolean writingToggle,

        Boolean foldSpoilerToggle
) {
    public boolean isEmpty() {
        return title == null && model == null && thinkingLevel == null
                && writingToggle == null && foldSpoilerToggle == null;
    }
}

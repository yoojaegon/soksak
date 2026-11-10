package com.soksak.soksak.chatRoom;

import com.soksak.soksak.aiClient.ThinkingLevel;
import com.soksak.soksak.common.Gender;
import com.soksak.soksak.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 방의 null은 사용자 기본값 상속, 값은 이 방 고정이다. 모델과 추론은 따로 고정한다.
class ChatRoomEffectiveSettingsTest {

    private static User user() {
        User user = User.builder()
                .loginId("u").email("u@soksak.com").nickname("u").password("pw")
                .birthYear(2000).gender(Gender.FEMALE)
                .build();
        user.updateChatDefaults("anthropic/claude-opus-4.8", ThinkingLevel.HIGH);
        return user;
    }

    @Test
    @DisplayName("고르지 않은 방은 사용자 기본값을 따른다")
    void inherits_user_defaults() {
        ChatRoom room = ChatRoom.builder().user(user()).title("방").build();

        assertThat(room.effectiveModel()).isEqualTo("anthropic/claude-opus-4.8");
        assertThat(room.effectiveThinkingLevel()).isEqualTo(ThinkingLevel.HIGH);
    }

    @Test
    @DisplayName("모델만 고정하면 추론은 계속 사용자 기본값을 따른다")
    void model_and_thinking_are_fixed_separately() {
        ChatRoom room = ChatRoom.builder().user(user()).title("방").build();
        room.update(null, "anthropic/claude-haiku-4.5", null, null, null);

        assertThat(room.effectiveModel()).isEqualTo("anthropic/claude-haiku-4.5");
        assertThat(room.effectiveThinkingLevel()).isEqualTo(ThinkingLevel.HIGH);
    }

    @Test
    @DisplayName("추론을 고정하면 사용자 기본값과 무관하다")
    void fixed_thinking_wins() {
        ChatRoom room = ChatRoom.builder().user(user()).title("방").build();
        room.update(null, null, ThinkingLevel.LOW, null, null);

        assertThat(room.effectiveModel()).isEqualTo("anthropic/claude-opus-4.8");
        assertThat(room.effectiveThinkingLevel()).isEqualTo(ThinkingLevel.LOW);
    }
}

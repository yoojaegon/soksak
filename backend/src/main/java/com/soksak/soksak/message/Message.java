package com.soksak.soksak.message;

import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@NoArgsConstructor
public class Message extends BaseTimeEntity {
    // 유저가 한 번에 보낼 수 있는 글자 수. 긴 입력은 그대로 LLM 입력이 되어 비용이 늘고,
    // 최근 대화가 들어갈 자리도 밀어낸다.
    public static final int USER_CONTENT_MAX = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatRoom chatRoom;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private MessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder
    public Message(ChatRoom chatRoom, MessageRole role, String content) {
        this.chatRoom = chatRoom;
        this.role = role;
        this.content = content;
    }

    public void update(String content) {
        this.content = content;
    }

    // 유저 메시지는 수정할 때도 보낼 때와 같은 한도를 둔다. 수정으로 한도를 우회하면 안 되기 때문이다.
    // AI 답변은 길이 제한 대상이 아니라서 수정 길이도 보지 않는다.
    public boolean exceedsEditLimit(String newContent) {
        return role == MessageRole.USER && newContent.length() > USER_CONTENT_MAX;
    }
}

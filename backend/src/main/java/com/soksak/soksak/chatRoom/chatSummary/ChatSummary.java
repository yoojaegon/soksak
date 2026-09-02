package com.soksak.soksak.chatRoom.chatSummary;

import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "chat_summary",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_summary_room_seq",
                columnNames = {"chat_room_id", "seq"}))
public class ChatSummary extends BaseTimeEntity {

    // 중요도를 못 받았을 때(수동 편집 등) 쓰는 값. 대부분의 구간이 2~3이라 중간값을 둔다.
    public static final int DEFAULT_IMPORTANCE = 3;
    private static final int MIN_IMPORTANCE = 1;
    private static final int MAX_IMPORTANCE = 5;

    // 로어북 키와 같은 형식(콤마 구분)으로 저장한다. 나중에 이 요약을 키워드로 찾을 때
    // LoreService 의 매칭 로직을 그대로 재사용하기 위해서다.
    private static final String KEYWORD_SEPARATOR = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatRoom chatRoom;

    @Column(name = "seq", nullable = false)
    private int seq;

    // 이 요약이 덮는 메시지 범위. 일부러 FK 를 걸지 않았다 — FK+cascade 로 두면 경계 메시지를
    // 지울 때만 요약이 사라지고 중간 메시지를 지울 땐 남아서 규칙이 일관되지 않는다. 삭제는
    // "잘린 지점에 걸치거나 그 뒤에 있는 요약을 지운다"는 범위 조건으로 명시적으로 처리한다.
    // (덤으로 message 패키지를 참조하지 않게 되어 패키지 순환도 생기지 않는다.)
    @Column(name = "from_message_id", nullable = false)
    private Long fromMessageId;

    @Column(name = "to_message_id", nullable = false)
    private Long toMessageId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 1~5. 오래된 요약이라도 무게가 있으면 계속 프롬프트에 싣기 위한 선택 기준
    @Column(name = "importance", nullable = false)
    private int importance;

    // 콤마로 이은 검색어. 지금은 채워만 두고, 나중에 이번 입력과 관련된 옛 요약을 끌어올 때 쓴다.
    @Column(name = "keywords")
    private String keywords;

    // 만들 때 한 번 재두는 이 요약의 토큰 수. 내용이 안 바뀌므로 이후 선택은 정수 덧셈으로 끝난다.
    // 기준 토크나이저 하나로 잰 값이라 방 모델에 따라 실제와 차이가 나지만, 넉넉하게 잡히는
    // 방향이라 그대로 쓴다(ai-server 의 token_counter 참고).
    @Column(name = "estimated_tokens", nullable = false)
    private int estimatedTokens;

    @Builder
    public ChatSummary(ChatRoom chatRoom, int seq, Long fromMessageId, Long toMessageId,
                       String content, Integer importance, List<String> keywords, int estimatedTokens) {
        this.chatRoom = chatRoom;
        this.seq = seq;
        this.fromMessageId = fromMessageId;
        this.toMessageId = toMessageId;
        this.content = content;
        this.importance = clampImportance(importance);
        this.keywords = joinKeywords(keywords);
        this.estimatedTokens = estimatedTokens;
    }

    /** 모델이 범위 밖 값을 주거나 값이 아예 없어도 저장은 항상 1~5 안으로 들어오게 한다. */
    private static int clampImportance(Integer importance) {
        if (importance == null) return DEFAULT_IMPORTANCE;
        return Math.max(MIN_IMPORTANCE, Math.min(MAX_IMPORTANCE, importance));
    }

    /** 저장 형식의 주인은 이 엔티티다 — 호출부가 제각각 구분자를 정하지 않게 여기서 잇는다. */
    private static String joinKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return null;
        String joined = keywords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .distinct()
                .collect(Collectors.joining(KEYWORD_SEPARATOR));
        return joined.isEmpty() ? null : joined;
    }
}

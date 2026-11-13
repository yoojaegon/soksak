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
/**
 * 채팅방 대화의 한 구간을 요약한 조각.
 * 오래된 대화는 원문 대신 이 조각으로 프롬프트에 실린다. 한 방에 여러 개가 seq 순으로 쌓인다.
 */
public class ChatSummary extends BaseTimeEntity {

    /** 모델이 중요도를 주지 않았을 때 쓰는 값. 대부분 2~3이 나와서 중간값으로 둔다. */
    public static final int DEFAULT_IMPORTANCE = 3;
    private static final int MIN_IMPORTANCE = 1;
    private static final int MAX_IMPORTANCE = 5;

    /**
     * 로어북 키와 같은 콤마 구분 형식으로 저장한다.
     * 나중에 키워드로 요약을 찾을 때 LoreService의 매칭 로직을 그대로 쓰기 위해서다.
     */
    private static final String KEYWORD_SEPARATOR = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatRoom chatRoom;

    /** 방 안에서의 순번. (chat_room_id, seq)가 유니크다. */
    @Column(name = "seq", nullable = false)
    private int seq;

    /**
     * 이 조각이 덮는 메시지 범위의 처음 id. 메시지에는 일부러 FK를 걸지 않는다.
     * FK의 cascade는 경계 메시지가 지워질 때만 조각을 지우고 중간 메시지가 지워질 땐 남겨 규칙이 어긋난다.
     * 그래서 조각은 잘린 지점에 걸치거나 그 뒤에 있는 것을 범위 조건으로 직접 지운다.
     * message 패키지를 참조하지 않으니 패키지 순환도 생기지 않는다.
     */
    @Column(name = "from_message_id", nullable = false)
    private Long fromMessageId;

    /** 이 조각이 덮는 메시지 범위의 마지막 id. */
    @Column(name = "to_message_id", nullable = false)
    private Long toMessageId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 1~5. 높으면 오래된 조각이어도 계속 프롬프트에 싣는다(SummarySelector.PINNED_IMPORTANCE). */
    @Column(name = "importance", nullable = false)
    private int importance;

    /** 콤마로 이은 검색어. 지금은 채워만 두고, 나중에 이번 입력과 관련된 옛 요약을 찾을 때 쓴다. */
    @Column(name = "keywords")
    private String keywords;

    /**
     * 만들 때 한 번 재 두는 토큰 수. 내용이 바뀌지 않으니 고를 때는 더하기만 하면 된다.
     * ai-server가 기준 토크나이저 하나로 잰 값이라 방 모델에 따라 실제와 조금 다르지만,
     * 넉넉하게 잡히는 쪽이라 그대로 쓴다(ai-server의 token_counter 참고).
     * 사용자가 직접 쓴 기억은 SummarySelector.estimateTokens의 어림값을 쓴다.
     */
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

    /**
     * 저장 형식은 이 엔티티가 정한다. 호출하는 쪽마다 구분자를 따로 정하지 않도록 여기서 잇는다.
     * 앞뒤 공백을 자르고 빈 값과 중복은 뺀다.
     */
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

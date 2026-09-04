package com.soksak.soksak.chatRoom;

import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;
import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.List;
import java.util.stream.Collectors;

@Entity
@NoArgsConstructor
@Getter
public class ChatRoom extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatCharacter character;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "summarized_up_to_id")
    private Long summarizedUpToId;

    @Column(name = "writing_toggle", nullable = false)
    private boolean writingToggle;

    @Column(name = "fold_spoiler_toggle", nullable = false)
    private boolean foldSpoilerToggle;

    // null = 아직 안 고름. 읽을 땐 ModelCatalog.resolve()가 기본값으로 폴백한다.
    @Column(name = "model")
    private String model;

    @Builder
    public ChatRoom(Long id, User user, ChatCharacter character, String title) {
        this.id = id;
        this.user = user;
        this.character = character;
        this.title = title;
        this.writingToggle = false;
        this.foldSpoilerToggle = false;
    }

    public void update(String title) {
        this.title = title;
    }
    // 조각 테이블로 넘어가는 동안만 쓰는 임시 미러. 프롬프트를 만드는 buildRequest가 아직
    // 이 두 필드를 읽으므로, 조각 목록에서 값을 통째로 다시 만들어 채운다.
    // ⚠️ "새 조각을 이어붙이기"가 아니라 "조각 전체로 덮어쓰기"여야 한다. open-in-view가 켜져
    // 있어 동기 경로에서는 서비스가 새로 조회한 방이 같은 인스턴스로 돌아오는데, 이어붙이기면
    // 한 번의 요약에 두 번 붙는다. 조각이 지워진 뒤에도 이 방식이라야 값이 어긋나지 않는다.
    public void syncSummaryFrom(List<ChatSummary> summaries) {
        if (summaries.isEmpty()) {
            this.summary = null;
            this.summarizedUpToId = null;
            return;
        }
        this.summary = summaries.stream()
                .map(ChatSummary::getContent)
                .collect(Collectors.joining("\n\n"));
        this.summarizedUpToId = summaries.get(summaries.size() - 1).getToMessageId();
    }

    public void toggleUpdate(boolean writingToggle, boolean foldSpoilerToggle) {
        this.writingToggle = writingToggle;
        this.foldSpoilerToggle = foldSpoilerToggle;
    }

    public void updateModel(String model) {this.model = model;}

    public void updateSummary(String summary) {this.summary = summary;}

    public void clearSummary() {this.summary = null; this.summarizedUpToId = null;}
}

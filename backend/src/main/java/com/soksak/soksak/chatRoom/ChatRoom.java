package com.soksak.soksak.chatRoom;

import com.soksak.soksak.aiClient.ThinkingLevel;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

    @Column(name = "writing_toggle", nullable = false)
    private boolean writingToggle;

    @Column(name = "fold_spoiler_toggle", nullable = false)
    private boolean foldSpoilerToggle;

    // null = 아직 안 고름. 읽을 땐 ModelCatalog.resolve()가 기본값으로 폴백한다.
    @Column(name = "model")
    private String model;

    // 추론 깊이도 model과 같은 의미론이다 — null = 아직 안 고름(발송 직전에
    // ModelCatalog.resolveThinking()이 모델에 맞게 맞춘다).
    // ⚠️ nullable이어야 한다. ddl-auto=update는 행이 있는 테이블에 NOT NULL 컬럼을 못 붙인다.
    @Enumerated(EnumType.STRING)
    @Column(name = "thinking_level")
    private ThinkingLevel thinkingLevel;

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

    // 요약은 이 엔티티가 아니라 chat_summary 조각들이 갖는다. 방에 summary/summarizedUpToId를
    // 두던 시절의 미러는 전부 걷어냈고, 커서는 SummarySelector.cursorOf 가 조각에서 유도한다.

    public void toggleUpdate(boolean writingToggle, boolean foldSpoilerToggle) {
        this.writingToggle = writingToggle;
        this.foldSpoilerToggle = foldSpoilerToggle;
    }

    public void updateModel(String model) {this.model = model;}

    public void updateThinkingLevel(ThinkingLevel thinkingLevel) {this.thinkingLevel = thinkingLevel;}
}

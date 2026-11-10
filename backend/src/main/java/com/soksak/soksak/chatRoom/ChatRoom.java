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

    // null이면 사용자 기본값(users.default_model)을 따르고, 값이 있으면 이 방에 고정한 것이다.
    // 실제로 쓰는 값은 effectiveModel()로 읽는다.
    @Column(name = "model")
    private String model;

    // 추론 깊이도 model과 같은 의미론이고 model과 따로 고정한다.
    // 모델에 맞게 맞추는 건 발송 직전의 ModelCatalog.resolveThinking() 몫이다.
    @Enumerated(EnumType.STRING)
    @Column(name = "thinking_level")
    private ThinkingLevel thinkingLevel;

    @Builder
    public ChatRoom(User user, ChatCharacter character, String title) {
        this.user = user;
        this.character = character;
        this.title = title;
        this.writingToggle = false;
        this.foldSpoilerToggle = false;
    }

    // null인 인자는 바꾸지 않는다(부분 수정).
    public void update(String title, String model, ThinkingLevel thinkingLevel,
                       Boolean writingToggle, Boolean foldSpoilerToggle) {
        if (title != null) this.title = title;
        if (model != null) this.model = model;
        if (thinkingLevel != null) this.thinkingLevel = thinkingLevel;
        if (writingToggle != null) this.writingToggle = writingToggle;
        if (foldSpoilerToggle != null) this.foldSpoilerToggle = foldSpoilerToggle;
    }

    // 이 방의 고정을 풀고 사용자 기본값을 따르게 한다.
    public void followDefaultModel() {this.model = null;}

    public void followDefaultThinking() {this.thinkingLevel = null;}

    // 이 방이 실제로 쓰는 모델. 발송, 차감, 잔액 확인, 환불이 모두 이걸 읽어야
    // 보낸 모델과 깎은 마디가 어긋나지 않는다.
    // 카탈로그 검증(resolve·costOf)은 호출부가 한다. user를 읽으므로 user가 로딩된 방이어야 한다.
    public String effectiveModel() {
        return model != null ? model : user.getDefaultModel();
    }

    // 상속까지만 채운 값이다. 모델에 맞춘 보정은 하지 않는다.
    public ThinkingLevel effectiveThinkingLevel() {
        return thinkingLevel != null ? thinkingLevel : user.getDefaultThinkingLevel();
    }

    // 요약은 이 엔티티가 아니라 chat_summary 조각들이 갖는다. 방에 summary/summarizedUpToId를
    // 두던 시절의 미러는 전부 걷어냈고, 커서는 SummarySelector.cursorOf 가 조각에서 유도한다.
}

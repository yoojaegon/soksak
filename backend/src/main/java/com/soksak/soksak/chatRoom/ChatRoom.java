package com.soksak.soksak.chatRoom;

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
    public void applySummary(String newSummary, Long upToId) {
        this.summary = newSummary;
        this.summarizedUpToId = upToId;
    }

    public void toggleUpdate(boolean writingToggle, boolean foldSpoilerToggle) {
        this.writingToggle = writingToggle;
        this.foldSpoilerToggle = foldSpoilerToggle;
    }

    public void updateModel(String model) {this.model = model;}
}

package com.soksak.soksak.lore;

import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 캐릭터에 딸린 설정 한 조각이다. 키워드가 대화에 나오거나 항상 적용이 켜져 있으면
 * 채팅 프롬프트에 들어간다. 캐릭터를 지우면 함께 지워진다.
 */
@Entity
@NoArgsConstructor
@Getter
public class Lore extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatCharacter character;

    /** 쉼표로 구분한 키워드. 비어 있으면 항상 적용일 때만 들어간다. */
    @Column(name = "trigger_keys", length = 200)
    private String keys;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 키워드와 상관없이 매 턴 프롬프트에 넣을지 여부 */
    @Column(name = "always_on", nullable = false)
    private boolean alwaysOn;

    /** 꺼 두면 지우지 않고 프롬프트에서만 뺀다. 만들 때는 켜져 있다. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Builder
    public Lore(ChatCharacter character, String title, String keys, String content, boolean alwaysOn) {
        this.character = character;
        this.title = title;
        this.keys = keys;
        this.content = content;
        this.alwaysOn = alwaysOn;
        this.enabled = true;
    }

    public void update(String title, String keys, String content, boolean alwaysOn) {
        this.title = title;
        this.keys = keys;
        this.content = content;
        this.alwaysOn = alwaysOn;
    }

    public void updateEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}

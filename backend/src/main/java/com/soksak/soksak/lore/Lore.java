package com.soksak.soksak.lore;

import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

    @Column(name = "trigger_keys", length = 200)
    private String keys;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "always_on", nullable = false)
    private boolean alwaysOn;

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

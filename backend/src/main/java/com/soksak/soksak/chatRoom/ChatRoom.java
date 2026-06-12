package com.soksak.soksak.chatRoom;

import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    private ChatCharacter character;

    @Column(name = "title", nullable = false)
    private String title;

    @Builder
    public ChatRoom(Long id, User user, ChatCharacter character, String title) {
        this.id = id;
        this.user = user;
        this.character = character;
        this.title = title;
    }

    public void update(String title) {
        this.title = title;
    }
}

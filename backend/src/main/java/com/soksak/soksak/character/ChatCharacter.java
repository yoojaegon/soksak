package com.soksak.soksak.character;

import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "characters")
@NoArgsConstructor
@Getter
public class ChatCharacter extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "description", length = 100)
    private String description;

    @Column(name = "persona", nullable = false, length = 1000)
    private String persona;

    @Builder
    public ChatCharacter(Long id, User user, String name, String description, String persona) {
        this.id = id;
        this.user = user;
        this.name = name;
        this.description = description;
        this.persona = persona;
    }

    public void update(String name, String description, String persona) {
        this.name = name;
        this.description = description;
        this.persona = persona;
    }
}

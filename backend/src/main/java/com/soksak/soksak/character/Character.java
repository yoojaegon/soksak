package com.soksak.soksak.character;

import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "characters")
@NoArgsConstructor
@Getter
public class Character extends BaseTimeEntity {
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
}

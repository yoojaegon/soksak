package com.soksak.soksak.character;

import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.common.Gender;
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

    // 카드/검색 표시용 메타데이터. 캐릭터에 나이 개념이 없을 수 있어 nullable.
    @Column(name = "age")
    private Integer age;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "persona", nullable = false, length = 1000)
    private String persona;

    @Column(name = "greeting", nullable = false, length = 500)
    private String greeting;

    @Builder
    public ChatCharacter(Long id, User user, String name, String description,
                         Integer age, Gender gender, String persona, String greeting) {
        this.id = id;
        this.user = user;
        this.name = name;
        this.description = description;
        this.age = age;
        this.gender = gender;
        this.persona = persona;
        this.greeting = greeting;
    }

    public void update(String name, String description, String persona, String greeting) {
        this.name = name;
        this.description = description;
        this.persona = persona;
        this.greeting = greeting;
    }
}

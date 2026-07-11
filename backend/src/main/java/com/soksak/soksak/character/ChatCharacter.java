package com.soksak.soksak.character;

import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.common.Gender;
import com.soksak.soksak.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.HashSet;
import java.util.Set;

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

    // 좋아요·대화 수를 매번 집계하지 않도록 캐릭터 행에 들고 있는 비정규화 카운터.
    // 증감은 동시성 안전을 위해 CharacterRepository의 원자적 update 쿼리로만 한다.
    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "chat_count", nullable = false)
    private int chatCount;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "character_tags", joinColumns = @JoinColumn(name = "character_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tags")
    @BatchSize(size = 100)
    private Set<Genre> tags = new HashSet<>();

    @Builder
    public ChatCharacter(Long id, User user, String name, String description,
                         Integer age, Gender gender, String persona, String greeting, Set<Genre> tags) {
        this.id = id;
        this.user = user;
        this.name = name;
        this.description = description;
        this.age = age;
        this.gender = gender;
        this.persona = persona;
        this.greeting = greeting;
        this.likeCount = 0;
        this.chatCount = 0;
        this.tags = (tags != null) ? tags : new HashSet<>();
    }

    public void update(String name, String description, String persona, String greeting, Set<Genre> tags) {
        this.name = name;
        this.description = description;
        this.persona = persona;
        this.greeting = greeting;
        this.tags.clear();
        if (tags != null) this.tags.addAll(tags);
    }
}

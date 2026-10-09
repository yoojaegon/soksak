package com.soksak.soksak.character;

import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
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

    @Column(name = "persona", nullable = false, length = 1000)
    private String persona;

    @Column(name = "greeting", nullable = false, length = 500)
    private String greeting;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // 좋아요·대화 수를 매번 집계하지 않도록 캐릭터 행에 들고 있는 비정규화 카운터.
    // 증감은 동시성 안전을 위해 CharacterRepository의 원자적 update 쿼리로만 한다.
    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "chat_count", nullable = false)
    private int chatCount;

    // 신고가 모여 자동으로 숨겨진 상태(카탈로그·새 대화에서 빠진다). 해제는 운영자가 DB에서만 한다 — unhide()를 두지 않는 이유.
    // 기존 행이 있는 테이블에 붙이므로 기본값을 DB에 박는다(ddl-auto=update는 기본값 없는 NOT NULL을 못 붙인다).
    @Column(name = "hidden", nullable = false, columnDefinition = "boolean not null default false")
    private boolean hidden;

    // 제작자가 내용을 마지막으로 고친 시각(null = 고친 적 없음). 이보다 앞선 신고는 숨김 판정에서 뺀다 —
    // 고쳤으면 다시 신고가 모여야 숨긴다. updatedAt은 hide() 같은 내부 변경에도 바뀌어서 따로 둔다.
    @Column(name = "content_updated_at")
    private LocalDateTime contentUpdatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "character_tags", joinColumns = @JoinColumn(name = "character_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tags")
    @BatchSize(size = 100)
    private Set<Genre> tags = new HashSet<>();

    @Builder
    public ChatCharacter(Long id, User user, String name, String description,
                         String persona, String greeting, String imageUrl, Set<Genre> tags) {
        this.id = id;
        this.user = user;
        this.name = name;
        this.description = description;
        this.persona = persona;
        this.greeting = greeting;
        this.imageUrl = imageUrl;
        this.likeCount = 0;
        this.chatCount = 0;
        this.tags = (tags != null) ? tags : new HashSet<>();
    }

    public void update(String name, String description, String persona, String greeting,
                       String imageUrl, Set<Genre> tags) {
        this.name = name;
        this.description = description;
        this.persona = persona;
        this.greeting = greeting;
        this.imageUrl = imageUrl;
        this.tags.clear();
        if (tags != null) this.tags.addAll(tags);
        this.contentUpdatedAt = LocalDateTime.now();   // 숨김은 그대로 둔다 — 고쳐서 스스로 푸는 우회로를 막으려고.
    }

    public void hide() {
        this.hidden = true;
    }
}

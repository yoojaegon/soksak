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

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "description", length = 100)
    private String description;

    @Column(name = "persona", nullable = false, length = 1000)
    private String persona;

    @Column(name = "greeting", nullable = false, length = 500)
    private String greeting;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * 좋아요 수와 대화 수를 매번 세지 않으려고 캐릭터 행에 들고 있는 카운터.
     * 증감은 CharacterRepository의 UPDATE 쿼리로만 한다. 동시에 갱신해도 값이 빠지지 않게 하기 위해서다.
     */
    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "chat_count", nullable = false)
    private int chatCount;

    /**
     * 신고가 모여 자동으로 숨겨진 상태. 숨겨지면 카탈로그와 새 대화에서 빠진다.
     * 해제는 운영자가 DB에서만 하므로 unhide()는 두지 않는다.
     * DB 컬럼에도 기본값 false가 있어서 SQL로 직접 넣은 행도 보이는 상태로 들어간다.
     */
    @Column(name = "hidden", nullable = false, columnDefinition = "boolean not null default false")
    private boolean hidden;

    /**
     * 제작자가 내용을 마지막으로 고친 시각. 고친 적이 없으면 null이다.
     * 이 시각보다 앞선 신고는 숨김 판정에서 빼서, 고친 뒤에는 신고가 다시 모여야 숨겨진다.
     * updatedAt은 hide() 같은 내부 변경에도 바뀌니 따로 둔다.
     */
    @Column(name = "content_updated_at")
    private LocalDateTime contentUpdatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "character_tags", joinColumns = @JoinColumn(name = "character_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tags")
    @BatchSize(size = 100)
    private Set<Genre> tags = new HashSet<>();

    @Builder
    public ChatCharacter(User user, String name, String description,
                         String persona, String greeting, String imageUrl, Set<Genre> tags) {
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
        this.contentUpdatedAt = LocalDateTime.now();   // 숨김은 풀지 않는다. 제작자가 내용을 고치는 것만으로 숨김이 풀리면 안 된다.
    }

    public void hide() {
        this.hidden = true;
    }
}

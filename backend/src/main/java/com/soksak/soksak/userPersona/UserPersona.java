package com.soksak.soksak.userPersona;

import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유저가 대화할 때 쓰는 자기 설정이다. 한 유저가 여러 개를 가질 수 있다.
 * 기본 페르소나가 유저당 하나라는 규칙은 부분 유니크 인덱스(uk_user_persona_default)로 DB에서도 막는다.
 * JPA로는 where 조건이 붙은 인덱스를 만들 수 없어서 이 인덱스는 마이그레이션(db/migration)에 있다.
 */
@Entity
@Table(name = "user_persona",
        indexes = @Index(name = "idx_user_persona_user", columnList = "user_id"))
@NoArgsConstructor
@Getter
public class UserPersona extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    /**
     * ai-server의 user_persona(<user> 섹션 본문)로 전달되는 자유 서술이다.
     * 나이나 성별 같은 설정도 여기에 문장으로 쓴다.
     * ai-server로는 이 본문만 전달되므로, 칸을 따로 나눠 받으면 같은 정보를 두 번 받게 된다.
     */
    @Column(name = "persona", nullable = false, length = 1000)
    private String persona;

    /** 채팅할 때 기본으로 쓰이는 페르소나인지 표시한다. 유저당 하나만 true다. */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Builder
    public UserPersona(User user, String name, String persona, boolean isDefault) {
        this.user = user;
        this.name = name;
        this.persona = persona;
        this.isDefault = isDefault;
    }

    public void update(String name, String persona) {
        this.name = name;
        this.persona = persona;
    }

    public void updateDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}

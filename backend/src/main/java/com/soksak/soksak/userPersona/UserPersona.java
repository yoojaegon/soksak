package com.soksak.soksak.userPersona;

import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
// 기본 페르소나가 유저당 하나라는 규칙은 부분 유니크 인덱스(uk_user_persona_default)로 DB에서도 막는다.
// JPA로는 where 조건이 붙은 인덱스를 만들 수 없어 직접 SQL로 만들었다. 테스트 DB(H2)는 부분 인덱스를 지원하지 않아 없다.
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

    // ai-server 의 user_persona(<user> 섹션 본문)로 전달되는 자유 서술.
    // 나이·성별 같은 설정도 여기에 문장으로 쓴다. (구조화된 칸을 따로 두면 같은 정보를 두 번 받게 되고,
    //  ai-server로는 이 본문만 전달되므로 구조화된 값은 어차피 대화에 반영되지 않는다.)
    @Column(name = "persona", nullable = false, length = 1000)
    private String persona;

    // 한 유저가 페르소나를 여러 개 가질 수 있고, 채팅 시 기본으로 쓰이는 한 개를 표시한다.
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

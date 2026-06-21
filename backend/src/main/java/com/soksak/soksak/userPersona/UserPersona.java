package com.soksak.soksak.userPersona;

import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.common.Gender;
import com.soksak.soksak.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_persona")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    @Column(name = "age", nullable = false)
    private int age;

    // ai-server 의 user_persona(<user> 섹션 본문)로 전달되는 자유 서술.
    @Column(name = "persona", nullable = false, length = 1000)
    private String persona;

    // 한 유저가 페르소나를 여러 개 가질 수 있고, 채팅 시 기본으로 쓰이는 한 개를 표시한다.
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Builder
    public UserPersona(Long id, User user, String name, Gender gender, int age,
                       String persona, boolean isDefault) {
        this.id = id;
        this.user = user;
        this.name = name;
        this.gender = gender;
        this.age = age;
        this.persona = persona;
        this.isDefault = isDefault;
    }

    public void update(String name, Gender gender, int age, String persona) {
        this.name = name;
        this.gender = gender;
        this.age = age;
        this.persona = persona;
    }

    public void updateDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}

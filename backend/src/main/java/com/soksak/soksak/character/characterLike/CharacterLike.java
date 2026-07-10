package com.soksak.soksak.character.characterLike;

import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자-캐릭터 좋아요를 잇는 조인 엔티티(한 행 = 한 명이 한 캐릭터에 누른 좋아요).
// (user_id, character_id) 유니크 제약으로 같은 사용자의 중복 좋아요를 DB 차원에서 막는다.
@Entity
@Getter
@NoArgsConstructor
@Table(name = "character_like",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_character_like_user_character",
                columnNames = {"user_id", "character_id"}))
public class CharacterLike extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    User user;
    @ManyToOne(fetch = FetchType.LAZY)
    ChatCharacter character;

    @Builder
    public CharacterLike(User user, ChatCharacter character) {
        this.user = user;
        this.character = character;
    }
}

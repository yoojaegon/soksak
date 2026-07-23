package com.soksak.soksak.character.characterLike;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CharacterLikeRepository extends JpaRepository<CharacterLike, Long> {
    // 이미 좋아요한 상태인지 확인(멱등 처리용).
    boolean existsByUser_LoginIdAndCharacter_id(String loginId, Long characterId);

    // 좋아요 취소. 지워진 행 수를 돌려줘 실제로 취소됐을 때만 카운터를 내리도록 한다.
    @Modifying
    int deleteByUser_LoginIdAndCharacter_id(String loginId, Long CharacterId);

    // 캐릭터의 작성자(user)까지 함께 fetch (CharacterResponse가 user를 읽어 N+1 방지),
    // 최근에 좋아요한 순으로 정렬.
    @Query("select cl from CharacterLike cl " +
            "join fetch cl.character c join fetch c.user " +
            "where cl.user.loginId = :loginId order by cl.createdAt desc")
    List<CharacterLike> findByUser_LoginId(@Param("loginId") String loginId);
}

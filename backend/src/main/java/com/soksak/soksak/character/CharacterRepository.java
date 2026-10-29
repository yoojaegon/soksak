package com.soksak.soksak.character;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface CharacterRepository extends JpaRepository<ChatCharacter, Long> {
    /**
     * 내 캐릭터 목록. user를 join fetch로 같이 읽고, where 조건도 같은 join을 써서 users join은 한 번이다.
     * join fetch가 있는 쿼리는 count 쿼리로 바꿀 수 없어서 countQuery를 따로 적는다.
     * 숨겨진 캐릭터도 포함한다. 제작자가 "검토 중" 배지로 상태를 봐야 하기 때문이다.
     */
    @Query(value = "select c from ChatCharacter c join fetch c.user u where u.loginId = :loginId",
            countQuery = "select count(c) from ChatCharacter c where c.user.loginId = :loginId")
    Page<ChatCharacter> findByUser_LoginId(@Param("loginId") String loginId, Pageable pageable);

    /**
     * 신고 판정용 행 잠금. 같은 캐릭터에 들어온 신고를 한 줄로 세워서,
     * 동시에 들어온 신고끼리 서로를 못 보고 둘 다 기준 미달로 끝나는 일을 막는다.
     * join fetch를 쓰지 않는 건 FOR UPDATE가 제작자의 users 행까지 잠그기 때문이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatCharacter c where c.id = :id")
    Optional<ChatCharacter> findByIdForUpdate(@Param("id") Long id);

    /**
     * 좋아요 수와 대화 수는 엔티티를 읽고 고쳐 쓰지 않고 UPDATE 한 번으로 더하고 뺀다.
     * 그래야 동시에 갱신해도 값이 빠지지 않는다.
     */
    @Modifying
    @Query("update ChatCharacter c set c.likeCount = c.likeCount + 1 where c.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("update ChatCharacter c set c.likeCount = c.likeCount - 1 where c.id = :id and c.likeCount > 0")
    void decrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("update ChatCharacter c set c.chatCount = c.chatCount + 1 where c.id = :id")
    void incrementChatCount(@Param("id") Long id);

    /**
     * 카탈로그와 검색. 신고가 모여 숨겨진 캐릭터는 뺀다.
     * q가 null이면 Postgres가 파라미터 타입을 bytea로 추론해 lower()를 쓸 수 없어서,
     * concat 안의 :q는 cast(:q as string)으로 타입을 정해 준다.
     * :q의 LIKE 메타문자(%, _, \)는 서비스에서 이스케이프해 넘기고, 쿼리는 escape '\'로 받는다.
     */
    @EntityGraph(attributePaths = "user")
    @Query("select c from ChatCharacter c where c.hidden = false and (:q is null " +
            "or lower(c.name) like lower(concat('%', cast(:q as string), '%')) escape '\\' " +
            "or lower(c.description) like lower(concat('%', cast(:q as string), '%')) escape '\\') " +
            "and (:tag is null or :tag member of c.tags)")
    Page<ChatCharacter> search(@Param("q") String q, @Param("tag") Genre tag, Pageable pageable);
}

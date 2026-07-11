package com.soksak.soksak.character;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CharacterRepository extends JpaRepository<ChatCharacter, Long> {
    // user를 한 번의 join fetch로 같이 로딩 (where 조건도 같은 join 재사용 → users join 1번)
    @Query("select c from ChatCharacter c join fetch c.user u where u.loginId = :loginId")
    List<ChatCharacter> findByUser_LoginId(@Param("loginId") String loginId);

    @EntityGraph(attributePaths = "user")
    Page<ChatCharacter> findAll(Pageable pageable);

    // 카운터는 동시 갱신 시 유실되지 않도록 엔티티 읽고-수정-쓰기 대신 DB 원자 연산으로 증감한다.
    @Modifying
    @Query("update ChatCharacter c set c.likeCount = c.likeCount + 1 where c.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("update ChatCharacter c set c.likeCount = c.likeCount - 1 where c.id = :id and c.likeCount > 0")
    void decrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("update ChatCharacter c set c.chatCount = c.chatCount + 1 where c.id = :id")
    void incrementChatCount(@Param("id") Long id);

    // q가 null일 때 Postgres가 파라미터를 bytea로 추론해 lower(bytea) 에러가 나므로
    // concat 안의 :q는 cast(:q as string)으로 타입을 명시해 준다.
    // :q 안의 LIKE 메타문자(%,_,\)는 서비스에서 '\'로 이스케이프해 넘기므로 escape '\'로 리터럴 취급한다.
    @EntityGraph(attributePaths = "user")
    @Query("select c from ChatCharacter c where (:q is null " +
            "or lower(c.name) like lower(concat('%', cast(:q as string), '%')) escape '\\' " +
            "or lower(c.description) like lower(concat('%', cast(:q as string), '%')) escape '\\') " +
            "and (:tag is null or :tag member of c.tags)")
    Page<ChatCharacter> search(@Param("q") String q, @Param("tag") Genre tag, Pageable pageable);
}

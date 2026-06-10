package com.soksak.soksak.character;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CharacterRepository extends JpaRepository<ChatCharacter, Long> {
    // user를 한 번의 join fetch로 같이 로딩 (where 조건도 같은 join 재사용 → users join 1번)
    @Query("select c from ChatCharacter c join fetch c.user u where u.loginId = :loginId")
    List<ChatCharacter> findByUser_LoginId(@Param("loginId") String loginId);

    @EntityGraph(attributePaths = "user")
    Page<ChatCharacter> findAll(Pageable pageable);
}

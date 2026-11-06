package com.soksak.soksak.userPersona;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPersonaRepository extends JpaRepository<UserPersona, Long> {
    boolean existsByUser_Id(Long userId);

    Optional<UserPersona> findByUser_IdAndIsDefaultTrue(Long userId);

    /** 유저의 페르소나 전부를 생성순으로 조회한다. */
    List<UserPersona> findByUser_IdOrderByIdAsc(Long userId);
}

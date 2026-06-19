package com.soksak.soksak.userPersona;

import com.soksak.soksak.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPersonaRepository extends JpaRepository<UserPersona, Long> {
    boolean existsByUser(User user);

    Optional<UserPersona> findByUserAndIsDefaultTrue(User user);
}

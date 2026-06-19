package com.soksak.soksak.userPersona;

import com.soksak.soksak.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPersonaRepository extends JpaRepository<UserPersona, Long> {
    boolean existsByUser(User user);
}

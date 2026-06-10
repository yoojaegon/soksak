package com.soksak.soksak.character;

import com.soksak.soksak.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterRepository extends JpaRepository<ChatCharacter, Long> {
    List<ChatCharacter> findByUserId(Long userId);
}

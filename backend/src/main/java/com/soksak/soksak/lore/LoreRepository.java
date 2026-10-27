package com.soksak.soksak.lore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoreRepository extends JpaRepository<Lore, Long> {
    List<Lore> findByCharacter_IdAndEnabledTrueOrderByPriorityAsc(Long characterId);
    List<Lore> findByCharacter_IdOrderById(Long characterId);
}

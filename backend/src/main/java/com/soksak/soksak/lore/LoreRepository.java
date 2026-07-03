package com.soksak.soksak.lore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LoreRepository extends JpaRepository<Lore, Long> {
    List<Lore> findByCharacter_IdAndEnabledTrueOrderByPriorityAsc(Long characterId);
    List<Lore> findByCharacter_IdOrderById(Long characterId);

    @Modifying
    @Query("delete from Lore l where l.character.id = :characterId")
    int deleteByCharacterId(@Param("characterId") Long characterId);
}

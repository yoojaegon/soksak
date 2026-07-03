package com.soksak.soksak.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByChatRoomIdOrderByCreatedAtAscIdAsc(Long chatRoomId);

    @Modifying
    @Query("delete from ChatRoom r where r.character.id = :characterId")
    int deleteByCharacterId(@Param("characterId") Long characterId);}

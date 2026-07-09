package com.soksak.soksak.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByChatRoomIdOrderByCreatedAtAscIdAsc(Long chatRoomId);

    @Modifying
    @Query("delete from Message m where m.chatRoom.character.id = :characterId")
    int deleteByCharacterId(@Param("characterId") Long characterId);

    @Modifying
    @Query("delete from Message m where m.chatRoom.id = :chatRoomId")
    int deleteByChatRoomId(@Param("chatRoomId") Long chatRoomId);
}

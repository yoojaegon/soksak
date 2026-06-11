package com.soksak.soksak.chatRoom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    // 나의 전체 채팅방 목록 조회
    @Query("select r from ChatRoom r join fetch r.character c where r.user.loginId = :loginId")
    List<ChatRoom> findByUser_LoginId(@Param("loginId") String loginId);
}

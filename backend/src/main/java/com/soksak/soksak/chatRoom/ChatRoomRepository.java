package com.soksak.soksak.chatRoom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    // 나의 전체 채팅방 목록 조회
    @Query("select r from ChatRoom r join fetch r.character c where r.user.loginId = :loginId")
    List<ChatRoom> findByUser_LoginId(@Param("loginId") String loginId);

    // 특정 사용자가 특정 캐릭터와 가진 방들의 제목 (제목 자동 넘버링에 사용)
    @Query("select r.title from ChatRoom r where r.user.loginId = :loginId and r.character.id = :characterId")
    List<String> findTitlesByUserAndCharacter(@Param("loginId") String loginId,
                                              @Param("characterId") Long characterId);

    @Query("select r from ChatRoom r join fetch r.character join fetch r.user where r.id = :id")
    Optional<ChatRoom> findByWithDetails(@Param("id") Long id);
}

package com.soksak.soksak.message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByChatRoom_IdOrderByIdAsc(Long chatRoomId);

    // 신고 스냅샷용 — 신고한 AI 응답 바로 앞의 사용자 메시지.
    Optional<Message> findFirstByChatRoom_IdAndIdLessThanAndRoleOrderByIdDesc(Long chatRoomId, Long id, MessageRole role);
}

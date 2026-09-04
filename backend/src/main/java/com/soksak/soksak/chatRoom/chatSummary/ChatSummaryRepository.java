package com.soksak.soksak.chatRoom.chatSummary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSummaryRepository extends JpaRepository<ChatSummary, Long> {
    List<ChatSummary> findByChatRoomIdOrderBySeqAsc(Long roomId);

    Long deleteByChatRoomIdAndToMessageIdGreaterThanEqual(Long roomId, Long cut);

    void deleteByChatRoomId(Long roomId);
}

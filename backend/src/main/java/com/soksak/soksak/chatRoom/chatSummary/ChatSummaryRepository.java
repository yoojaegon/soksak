package com.soksak.soksak.chatRoom.chatSummary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSummaryRepository extends JpaRepository<ChatSummary, Long> {
    List<ChatSummary> findByChatRoom_IdOrderBySeqAsc(Long roomId);

    Long deleteByChatRoom_IdAndToMessageIdGreaterThanEqual(Long roomId, Long cut);

    void deleteByChatRoom_Id(Long roomId);
}

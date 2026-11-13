package com.soksak.soksak.chatRoom.chatSummary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSummaryRepository extends JpaRepository<ChatSummary, Long> {
    /** 방의 조각 전체를 만든 순서(seq)대로 가져온다. */
    List<ChatSummary> findByChatRoom_IdOrderBySeqAsc(Long roomId);

    /**
     * cut 이후 메시지를 덮는 조각을 지우고 지운 개수를 돌려준다.
     * 메시지를 어느 지점부터 지울 때, 그 지점에 걸친 조각과 그 뒤 조각을 함께 지우는 데 쓴다.
     */
    Long deleteByChatRoom_IdAndToMessageIdGreaterThanEqual(Long roomId, Long cut);

    /** 방의 조각을 전부 지운다. 사용자가 기억을 통째로 고칠 때 쓴다. */
    void deleteByChatRoom_Id(Long roomId);
}

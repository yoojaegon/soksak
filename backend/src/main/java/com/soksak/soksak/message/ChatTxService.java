package com.soksak.soksak.message;

import com.soksak.soksak.aiClient.ChatAiClient;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.ChatRoomRepository;
import com.soksak.soksak.chatRoom.ChatRoomService;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatTxService {
    private final ChatRoomService chatRoomService;
    private final ChatRoomRepository chatRoomRepository;
    private final MessageRepository messageRepository;
    private final ChatAiClient chatAiClient;
    private static final int WINDOW = 20;
    private static final int BATCH = 10;

    @Transactional
    public PreparedChat prepareAndSaveUser(String loginId, Long roomId, String content) {
        ChatRoom room = chatRoomService.getOwnedChatRoomWithDetails(loginId, roomId);
        List<Message> priorHistory = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);

        messageRepository.save(Message.builder()
                .chatRoom(room)
                .role(MessageRole.USER)
                .content(content)
                .build());

        return new PreparedChat(room, priorHistory);
    }

    @Transactional
    public Message saveAssistant(Long roomId, String reply) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        Message aiMessage = messageRepository.save(Message.builder()
                .chatRoom(room)
                .role(MessageRole.ASSISTANT)
                .content(reply)
                .build());

        try{
            rollSummary(room,  messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId));
        } catch (Exception e) {
            log.warn("요약 실패 (roomId={})", roomId, e);
        }
        return aiMessage;
    }

    private void rollSummary(ChatRoom room, List<Message> all) {
        Long upTo = room.getSummarizedUpToId() == null ? 0 : room.getSummarizedUpToId();
        int summarizableEnd = all.size() - WINDOW;
        if (summarizableEnd <= 0) return;

        List<Message> batch = all.subList(0, summarizableEnd).stream()
                .filter(m -> m.getId() > upTo)
                .toList();

        if (batch.size() < BATCH) return;

        String newSummary = chatAiClient.summarize(room.getSummary(), batch);
        room.applySummary(newSummary, batch.get(batch.size() - 1).getId());
    }
}

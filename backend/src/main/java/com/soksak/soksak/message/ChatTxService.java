package com.soksak.soksak.message;

import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.ChatRoomRepository;
import com.soksak.soksak.chatRoom.ChatRoomService;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.message.dto.RegenTarget;
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
    private final CharacterRepository characterRepository;

    @Transactional
    public PreparedChat prepareAndSaveUser(String loginId, Long roomId, String content) {
        ChatRoom room = chatRoomService.getOwnedChatRoomWithDetails(loginId, roomId);
        List<Message> priorHistory = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);

        messageRepository.save(Message.builder()
                .chatRoom(room)
                .role(MessageRole.USER)
                .content(content)
                .build());

        characterRepository.incrementChatCount(room.getCharacter().getId());

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

        return aiMessage;
    }

    @Transactional
    public void applySummary(Long roomId, String newSummary, Long upToId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        // AI 호출이 도는 사이 다른 경로가 더 앞까지 요약했으면 덮어쓰지 않는다.
        Long current = room.getSummarizedUpToId();
        if (current != null && current >= upToId) return;

        room.applySummary(newSummary, upToId);
    }

    @Transactional
    public RegenTarget prepareRegenerate(String loginId, Long roomId) {
        ChatRoom room = chatRoomService.getOwnedChatRoomWithDetails(loginId, roomId);
        List<Message> messages = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
        if (messages.isEmpty()) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        Message last = messages.get(messages.size() - 1);
        Message lastUser;
        List<Message> priorHistory;

        if(last.getRole() == MessageRole.ASSISTANT) {
            // 직전 메시지가 USER라는 전제를 방어적으로 검증 (언더플로/역할 불일치 시 예외)
            if (messages.size() < 2) {
                throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
            }
            lastUser = messages.get(messages.size() - 2);
            if (lastUser.getRole() != MessageRole.USER) {
                throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
            }
            messageRepository.delete(last);
            priorHistory = messages.subList(0, messages.size() - 2);
        } else{
            lastUser = last;
            priorHistory = messages.subList(0, messages.size() - 1);
        }
        return new RegenTarget(room, lastUser.getContent(), List.copyOf(priorHistory));
    }
}

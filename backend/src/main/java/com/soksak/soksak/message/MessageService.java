package com.soksak.soksak.message;

import com.soksak.soksak.chatRoom.ChatRoomService;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.aiClient.ChatAiClient;
import com.soksak.soksak.message.dto.MessageResponse;
import com.soksak.soksak.message.dto.RegenTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
    private final ChatRoomService chatRoomService;
    private final ChatTxService chatTxService;
    private final ChatAiClient chatAiClient;
    private static final int LOCK_STRIPES = 256;
    private final Lock[] roomLocks = Stream.generate(ReentrantLock::new)
            .limit(LOCK_STRIPES).toArray(Lock[]::new);


    public MessageResponse sendMessage(String loginId, Long roomId, String content) {
        return withRoomLock(roomId, () -> {
            PreparedChat preparedChat = chatTxService.prepareAndSaveUser(loginId, roomId, content);
            String reply = chatAiClient.reply(preparedChat.room(), content, preparedChat.priorHistory());
            Message aiMessage = chatTxService.saveAssistant(roomId, reply);
            return MessageResponse.from(aiMessage);
        });
    }

    @Transactional
    public List<MessageResponse> getMessages(String loginId, Long roomId) {
        chatRoomService.getOwnedChatRoom(loginId, roomId);
        return messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId).stream()
                .map(MessageResponse::from).toList();
    }

    // 메세지 수정
    @Transactional
    public MessageResponse updateMessage(String loginId, Long roomId, Long id, String content) {
        chatRoomService.getOwnedChatRoom(loginId, roomId);
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        if (!message.getChatRoom().getId().equals(roomId)) {
            throw new BusinessException(ErrorCode.MESSAGE_FORBIDDEN);
        }
        message.update(content);

        return MessageResponse.from(message);
    }

    // ai 응답 재생성
    public MessageResponse regenerate(String loginId, Long roomId) {
        return withRoomLock(roomId, () -> {
            RegenTarget t = chatTxService.prepareRegenerate(loginId, roomId);
            String reply = chatAiClient.reply(t.room(), t.lastUserContent(), t.priorHistory());
            Message ai = chatTxService.saveAssistant(roomId, reply);
            return MessageResponse.from(ai);
        });
    }

    @Transactional
    public void deleteFrom(String loginId, Long roomId, Long messageId) {
        chatRoomService.getOwnedChatRoom(loginId, roomId);
        Message target = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!target.getChatRoom().getId().equals(roomId)) {
            throw new BusinessException(ErrorCode.MESSAGE_FORBIDDEN);
        }
        List<Message> messages = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);;

        int idx = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(messageId)){
                idx = i;
                break;
            }
        }

        messageRepository.deleteAll(messages.subList(idx, messages.size()));
    }

    private <T> T withRoomLock(Long roomId, Supplier<T> action) {
        Lock lock = roomLocks[Math.floorMod(roomId, LOCK_STRIPES)];
        if (!lock.tryLock()){
            throw new BusinessException(ErrorCode.ROOM_BUSY);
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}

package com.soksak.soksak.message;

import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.ChatRoomService;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.aiClient.ChatAiClient;
import com.soksak.soksak.message.dto.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
    private final ChatRoomService chatRoomService;
    private final ChatTxService chatTxService;
    private final ChatAiClient chatAiClient;


    public MessageResponse sendMessage(String loginId, Long roomId, String content) {
        PreparedChat preparedChat = chatTxService.prepareAndSaveUser(loginId, roomId, content);
        String reply = chatAiClient.reply(preparedChat.room(), content, preparedChat.priorHistory());
        Message aiMessage = chatTxService.saveAssistant(roomId, reply);
        return MessageResponse.from(aiMessage);
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
    @Transactional
    public MessageResponse regenerate(String loginId, Long roomId) {
        ChatRoom chatRoom = chatRoomService.getOwnedChatRoom(loginId, roomId);
        List<Message> messages = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);

        if (messages.isEmpty()) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        Message last = messages.get(messages.size() - 1);

        Message lastUser;
        List<Message> priorHistory;

        if (last.getRole() == MessageRole.ASSISTANT) {
            messageRepository.delete(last);
            lastUser = messages.get(messages.size() - 2);
            priorHistory = messages.subList(0, messages.size() - 2);
        } else {
            lastUser = last;
            priorHistory = messages.subList(0, messages.size() - 1);
        }

        String reply = chatAiClient.reply(chatRoom, lastUser.getContent(), priorHistory);

        Message aiMessages = messageRepository.save(Message.builder()
                .chatRoom(chatRoom)
                .role(MessageRole.ASSISTANT)
                .content(reply)
                .build());
        return MessageResponse.from(aiMessages);
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


}

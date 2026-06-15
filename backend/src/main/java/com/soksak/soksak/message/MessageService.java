package com.soksak.soksak.message;

import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.ChatRoomService;
import com.soksak.soksak.message.aiClient.ChatAiClient;
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
    private final ChatAiClient chatAiClient;

    @Transactional
    public MessageResponse sendMessage(String loginId, Long roomId, String content) {
        ChatRoom chatRoom = chatRoomService.getOwnedChatRoom(loginId, roomId);
        List<Message> priorHistory = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);

        messageRepository.save(Message.builder()
                .chatRoom(chatRoom)
                .role(MessageRole.USER)
                .content(content)
                .build());

        String reply = chatAiClient.reply(chatRoom, content, priorHistory);

        Message aiMessage = messageRepository.save(Message.builder()
                .chatRoom(chatRoom)
                .role(MessageRole.ASSISTANT)
                .content(reply)
                .build());
        return MessageResponse.from(aiMessage);
    }

    @Transactional
    public List<MessageResponse> getMessages(String loginId, Long roomId) {
        chatRoomService.getOwnedChatRoom(loginId, roomId);
        return messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId).stream()
                .map(MessageResponse::from).toList();
    }

    // 메세지 수정
    @Transactional
    public MessageResponse updateMessage(String loginId, Long roomId, Long id, String content) {
        chatRoomService.getOwnedChatRoom(loginId, roomId);
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메세지 없음"));
        if (!message.getChatRoom().getId().equals(roomId)) {
            throw new IllegalArgumentException("해당 방의 메세지가 아님");
        }
        message.update(content);

        return MessageResponse.from(message);
    }

    // ai 응답 재생성
    @Transactional
    public MessageResponse regenerate(String loginId, Long roomId) {
        ChatRoom chatRoom = chatRoomService.getOwnedChatRoom(loginId, roomId);
        List<Message> messages = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);

        if (messages.isEmpty()) {
            throw new IllegalArgumentException("대화가 없음");
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
}

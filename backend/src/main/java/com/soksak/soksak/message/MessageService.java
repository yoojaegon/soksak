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
}

package com.soksak.soksak.chatRoom;

import com.soksak.soksak.aiClient.ModelCatalog;
import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.chatRoom.dto.ChatRoomResponse;
import com.soksak.soksak.chatRoom.dto.CreateChatRoomRequest;
import com.soksak.soksak.chatRoom.dto.UpdateChatRoomRequest;
import com.soksak.soksak.chatRoom.dto.UpdateModelRequest;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.message.Message;
import com.soksak.soksak.message.MessageRepository;
import com.soksak.soksak.message.MessageRole;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatRoomService {
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public ChatRoom createChatRoom(String loginId, CreateChatRoomRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        ChatCharacter character = characterRepository.findById(request.characterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARACTER_NOT_FOUND));

        ChatRoom chatRoom = chatRoomRepository.save(
                ChatRoom.builder()
                    .user(user)
                    .character(character)
                    .title(nextRoomTitle(loginId, character))
                    .build());

        messageRepository.save(Message.builder()
                .chatRoom(chatRoom)
                .role(MessageRole.ASSISTANT)
                .content(character.getGreeting())
                .build());
        return chatRoom;
    }

    // 같은 사용자가 같은 캐릭터로 방을 여러 개 만들면 제목을 자동으로 넘버링한다.
    // 첫 방은 캐릭터 이름 그대로, 그다음부터 "이름2", "이름3" … 식.
    // 이미 쓰고 있는 제목은 건너뛰어 중간 방을 지워도 제목이 겹치지 않는다.
    private String nextRoomTitle(String loginId, ChatCharacter character) {
        String base = character.getName();
        Set<String> used = new HashSet<>(
                chatRoomRepository.findTitlesByUserAndCharacter(loginId, character.getId()));

        if (!used.contains(base)) {
            return base;
        }
        int n = 2;
        while (used.contains(base + n)) {
            n++;
        }
        return base + n;
    }

    @Transactional(readOnly = true)
    public ChatRoomResponse getChatRoom(String loginId, Long id) {
        return ChatRoomResponse.from(getOwnedChatRoom(loginId, id));
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getMyChatRooms(String loginId) {
        return chatRoomRepository.findByUser_LoginId(loginId).stream()
                .map(ChatRoomResponse::from)
                .toList();
    }

    @Transactional
    public ChatRoomResponse updateChatRoom(String loginId, Long id, UpdateChatRoomRequest request) {
        ChatRoom chatRoom = getOwnedChatRoom(loginId, id);
        chatRoom.update(request.title());

        return ChatRoomResponse.from(chatRoom);
    }

    @Transactional
    public ChatRoomResponse updateModels(String loginId, Long id, UpdateModelRequest request) {
        ChatRoom chatRoom = getOwnedChatRoom(loginId, id);
        if (!ModelCatalog.contains(request.model()))
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        chatRoom.updateModel(request.model());

        return ChatRoomResponse.from(chatRoom);
    }

    @Transactional
    public ChatRoomResponse updateConfig(String loginId, Long id, boolean writingToggle, boolean foldSpoilerToggle) {
        ChatRoom chatRoom = getOwnedChatRoom(loginId, id);
        chatRoom.toggleUpdate(writingToggle, foldSpoilerToggle);

        return ChatRoomResponse.from(chatRoom);
    }

    @Transactional
    public void deleteChatRoom(String loginId, Long id) {
        ChatRoom chatRoom = getOwnedChatRoom(loginId, id);
        messageRepository.deleteByChatRoomId(id);
        chatRoomRepository.delete(chatRoom);
    }

    public ChatRoom getOwnedChatRoom(String loginId, Long chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        if (!chatRoom.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.CHATROOM_FORBIDDEN);
        }
        return chatRoom;
    }

    public ChatRoom getOwnedChatRoomWithDetails(String loginId, Long chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findByWithDetails(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));
        if (!chatRoom.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.CHATROOM_FORBIDDEN);
        }
        return chatRoom;
    }
}

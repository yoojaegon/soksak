package com.soksak.soksak.chatRoom;

import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.chatRoom.dto.ChatRoomResponse;
import com.soksak.soksak.chatRoom.dto.CreateChatRoomRequest;
import com.soksak.soksak.chatRoom.dto.UpdateChatRoomRequest;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomService {
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;

    @Transactional
    public ChatRoom createChatRoom(String loginId, CreateChatRoomRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));
        ChatCharacter character = characterRepository.findById(request.characterId())
                .orElseThrow(() -> new IllegalArgumentException("캐릭터 없음"));

        ChatRoom chatRoom = ChatRoom.builder()
                .user(user)
                .character(character)
                .title(character.getName())
                .build();

        return chatRoomRepository.save(chatRoom);
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
    public void deleteChatRoom(String loginId, Long id) {
        ChatRoom chatRoom = getOwnedChatRoom(loginId, id);
        chatRoomRepository.delete(chatRoom);
    }

    private ChatRoom getOwnedChatRoom(String loginId, Long chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        if (!chatRoom.getUser().getLoginId().equals(loginId)) {
            throw new IllegalArgumentException("본인 채팅방 아님");
        }
        return chatRoom;
    }
}

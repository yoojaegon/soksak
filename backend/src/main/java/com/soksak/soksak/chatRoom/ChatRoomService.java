package com.soksak.soksak.chatRoom;

import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.chatRoom.dto.CreateChatRoomRequest;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}

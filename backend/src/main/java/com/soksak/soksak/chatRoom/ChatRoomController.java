package com.soksak.soksak.chatRoom;

import com.soksak.soksak.chatRoom.dto.ChatRoomResponse;
import com.soksak.soksak.chatRoom.dto.CreateChatRoomRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatRoomController {
    private final ChatRoomService chatRoomService;

    @PostMapping("/chatrooms")
    public ResponseEntity<ChatRoomResponse> createChatRoom(
            Authentication authentication,
            @Valid @RequestBody CreateChatRoomRequest request) {
        ChatRoom chatRoom = chatRoomService.createChatRoom(authentication.getName(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ChatRoomResponse.from(chatRoom));
    }
}

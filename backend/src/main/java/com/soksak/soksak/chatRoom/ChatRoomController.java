package com.soksak.soksak.chatRoom;

import com.soksak.soksak.chatRoom.dto.ChatRoomResponse;
import com.soksak.soksak.chatRoom.dto.CreateChatRoomRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/chatrooms/{id}")
    public ResponseEntity<ChatRoomResponse> getChatRoom(
            Authentication authentication,
            @PathVariable Long id
    ){
        return ResponseEntity.ok(chatRoomService.getChatRoom(authentication.getName(), id));
    }

    @GetMapping("/chatrooms")
    public ResponseEntity<List<ChatRoomResponse>> getMyChatRooms(Authentication authentication) {
        return ResponseEntity.ok(chatRoomService.getMyChatRooms(authentication.getName()));
    }

    @DeleteMapping("/chatrooms/{id}")
    public ResponseEntity<Void> deleteChatRoom(
            Authentication authentication,
            @PathVariable Long id) {
        chatRoomService.deleteChatRoom(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}

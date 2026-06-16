package com.soksak.soksak.chatRoom;

import com.soksak.soksak.chatRoom.dto.ChatRoomResponse;
import com.soksak.soksak.chatRoom.dto.CreateChatRoomRequest;
import com.soksak.soksak.chatRoom.dto.UpdateChatRoomRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chatrooms")
public class ChatRoomController {
    private final ChatRoomService chatRoomService;

    @PostMapping
    public ResponseEntity<ChatRoomResponse> createChatRoom(
            Authentication authentication,
            @Valid @RequestBody CreateChatRoomRequest request) {
        ChatRoom chatRoom = chatRoomService.createChatRoom(authentication.getName(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ChatRoomResponse.from(chatRoom));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChatRoomResponse> getChatRoom(
            Authentication authentication,
            @PathVariable Long id
    ){
        return ResponseEntity.ok(chatRoomService.getChatRoom(authentication.getName(), id));
    }

    @GetMapping
    public ResponseEntity<List<ChatRoomResponse>> getMyChatRooms(Authentication authentication) {
        return ResponseEntity.ok(chatRoomService.getMyChatRooms(authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChatRoom(
            Authentication authentication,
            @PathVariable Long id) {
        chatRoomService.deleteChatRoom(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ChatRoomResponse> updateChatRoom(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateChatRoomRequest request
    ) {
        ChatRoomResponse response = chatRoomService.updateChatRoom(authentication.getName(), id, request);
        return ResponseEntity.ok(response);
    }
}

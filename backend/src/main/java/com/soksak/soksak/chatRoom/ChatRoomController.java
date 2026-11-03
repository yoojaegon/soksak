package com.soksak.soksak.chatRoom;

import com.soksak.soksak.auth.AuthUser;
import com.soksak.soksak.chatRoom.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chatrooms")
public class ChatRoomController {
    private final ChatRoomService chatRoomService;

    @PostMapping
    public ResponseEntity<ChatRoomResponse> createChatRoom(
            @AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody CreateChatRoomRequest request) {
        ChatRoom chatRoom = chatRoomService.createChatRoom(me.id(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ChatRoomResponse.from(chatRoom));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChatRoomResponse> getChatRoom(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long id
    ){
        return ResponseEntity.ok(chatRoomService.getChatRoom(me.id(), id));
    }

    @GetMapping
    public ResponseEntity<List<ChatRoomResponse>> getMyChatRooms(@AuthenticationPrincipal AuthUser me) {
        return ResponseEntity.ok(chatRoomService.getMyChatRooms(me.id()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChatRoom(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long id) {
        chatRoomService.deleteChatRoom(me.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ChatRoomResponse> updateChatRoom(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long id,
            @Valid @RequestBody UpdateChatRoomRequest request
    ) {
        ChatRoomResponse response = chatRoomService.updateChatRoom(me.id(), id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/model")
    public ResponseEntity<ChatRoomResponse> updateModels(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long id,
            @Valid @RequestBody UpdateModelRequest request
    ) {
        ChatRoomResponse response = chatRoomService.updateModels(me.id(), id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/thinking")
    public ResponseEntity<ChatRoomResponse> updateThinking(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long id,
            @Valid @RequestBody UpdateThinkingRequest request
    ) {
        return ResponseEntity.ok(chatRoomService.updateThinking(me.id(), id, request));
    }

    @PatchMapping("/{id}/config")
    public ResponseEntity<ChatRoomResponse> updateConfig(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long id,
            @RequestParam boolean writingToggle,
            @RequestParam boolean foldSpoilerToggle
    ) {
        ChatRoomResponse response = chatRoomService.updateConfig(
                me.id(), id, writingToggle, foldSpoilerToggle);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<SummaryResponse> getSummary(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(chatRoomService.getSummary(me.id(), id));
    }

    @PatchMapping("/{id}/summary")
    public ResponseEntity<SummaryResponse> updateSummary(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSummaryRequest request
    ) {
        return ResponseEntity.ok(chatRoomService.updateSummary(me.id(), id, request));
    }
}

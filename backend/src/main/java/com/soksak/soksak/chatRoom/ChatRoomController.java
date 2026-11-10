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
            // 고정 해제는 본문의 null이 아니라 명시적인 플래그로 받는다.
            // 본문의 null은 "변경 없음"이라, {}가 고른 값을 조용히 풀어 버리면 안 된다.
            @RequestParam(defaultValue = "false") boolean followDefaultModel,
            @RequestParam(defaultValue = "false") boolean followDefaultThinking,
            // 해제만 할 때는 본문 없이 보낸다.
            @Valid @RequestBody(required = false) UpdateChatRoomRequest request
    ) {
        return ResponseEntity.ok(chatRoomService.updateChatRoom(
                me.id(), id, request, followDefaultModel, followDefaultThinking));
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

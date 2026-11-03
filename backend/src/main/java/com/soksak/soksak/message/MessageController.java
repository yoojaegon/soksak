package com.soksak.soksak.message;

import com.soksak.soksak.auth.AuthUser;
import com.soksak.soksak.message.dto.DeleteFromResponse;
import com.soksak.soksak.message.dto.MessageRequest;
import com.soksak.soksak.message.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chatrooms/{roomId}/messages")
public class MessageController {
    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<MessageResponse> send(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long roomId,
            @Valid @RequestBody MessageRequest request
            ) {

        MessageResponse response = messageService.sendMessage(me.id(), roomId, request.content());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MessageResponse>> list(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long roomId
    ) {
        return ResponseEntity.ok(messageService.getMessages(me.id(), roomId));
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<MessageResponse> update(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long roomId,
            @PathVariable Long messageId,
            @Valid @RequestBody MessageRequest request
    ) {
        MessageResponse response = messageService.updateMessage(me.id(), roomId, messageId, request.content());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/regenerate")
    public ResponseEntity<MessageResponse> regenerate(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long roomId
    ) {
        MessageResponse response = messageService.regenerate(me.id(), roomId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{messageId}/after")
    public ResponseEntity<DeleteFromResponse> deleteForm(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long roomId,
            @PathVariable Long messageId
    ) {

        return ResponseEntity.ok(messageService.deleteFrom(me.id(), roomId, messageId));
    }

    @PostMapping("/stream")
    public SseEmitter sendStream(@AuthenticationPrincipal AuthUser me, @PathVariable Long roomId, @Valid @RequestBody MessageRequest request) {
        return messageService.sendMessageStream(me.id(), roomId, request.content());
    }

    @PostMapping("/regenerate/stream")
    public SseEmitter regenerateStream(@AuthenticationPrincipal AuthUser me, @PathVariable Long roomId) {
        return messageService.regenerateStream(me.id(), roomId);
    }
}

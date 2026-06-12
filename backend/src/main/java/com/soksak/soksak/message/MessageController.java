package com.soksak.soksak.message;

import com.soksak.soksak.message.dto.MessageRequest;
import com.soksak.soksak.message.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;

    @PostMapping("/chatrooms/{roomId}/messages")
    public ResponseEntity<MessageResponse> send(
            Authentication authentication,
            @PathVariable Long roomId,
            @Valid @RequestBody MessageRequest request
            ) {

        MessageResponse response = messageService.sendMessage(authentication.getName(), roomId, request.content());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/chatrooms/{roomId}/messages")
    public ResponseEntity<List<MessageResponse>> list(
            Authentication authentication,
            @PathVariable Long roomId
    ) {
        return ResponseEntity.ok(messageService.getMessages(authentication.getName(), roomId));
    }
}

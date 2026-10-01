package com.soksak.soksak.reports;

import com.soksak.soksak.reports.dto.CharacterReportRequest;
import com.soksak.soksak.reports.dto.MessageReportRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// 경로는 신고 대상 리소스 아래에 둔다(메시지 API가 전부 /chatrooms/{roomId}/messages 아래라 거기에 맞춤).
// 둘 다 204 — 숨겨졌는지는 알려주지 않는다(몇 번이면 숨는지 떠볼 수 없게).
@RestController
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @PostMapping("/characters/{id}/report")
    public ResponseEntity<Void> reportCharacter(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody CharacterReportRequest request) {
        reportService.reportCharacter(authentication.getName(), id, request.target(), request.reason(), request.detail());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/chatrooms/{roomId}/messages/{messageId}/report")
    public ResponseEntity<Void> reportMessage(
            Authentication authentication,
            @PathVariable Long roomId,
            @PathVariable Long messageId,
            @Valid @RequestBody MessageReportRequest request) {
        reportService.reportMessage(authentication.getName(), roomId, messageId, request.reason(), request.detail());
        return ResponseEntity.noContent().build();
    }
}

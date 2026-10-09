package com.soksak.soksak.reports.dto;

import com.soksak.soksak.reports.ReportReason;
import com.soksak.soksak.reports.ReportTarget;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// target은 IMAGE·CONCEPT만 — CHAT은 메시지 신고 경로로만 들어온다(서비스에서 거절).
public record CharacterReportRequest(
        @NotNull ReportTarget target,
        @NotNull ReportReason reason,
        @Size(max = 200) String detail
) {
}

package com.soksak.soksak.reports.dto;

import com.soksak.soksak.reports.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MessageReportRequest(
        @NotNull ReportReason reason,
        @Size(max = 200) String detail
) {
}

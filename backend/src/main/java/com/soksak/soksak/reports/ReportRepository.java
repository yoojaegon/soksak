package com.soksak.soksak.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ReportRepository extends JpaRepository<Report, Long> {
    // 숨김 판정용 — 창 안에서 이 캐릭터(IMAGE·CONCEPT)를 신고한 서로 다른 사람 수.
    // distinct라 같은 사람이 몇 번을 신고해도 1명이다. CHAT 신고는 제작자 책임이 아니라 세지 않는다.
    @Query("select count(distinct r.reporter.id) from Report r " +
            "where r.character.id = :characterId and r.target <> com.soksak.soksak.reports.ReportTarget.CHAT " +
            "and r.createdAt >= :since")
    long countReportersSince(@Param("characterId") Long characterId, @Param("since") LocalDateTime since);
}

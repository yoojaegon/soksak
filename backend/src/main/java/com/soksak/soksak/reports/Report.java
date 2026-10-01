package com.soksak.soksak.reports;

import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// 신고 한 건(한 행 = 한 명이 한 대상을 한 번 신고). 이미지·캐릭터 설정·채팅 응답 신고를 한 테이블에 둔다.
// - IMAGE·CONCEPT: 캐릭터에 대한 신고. 서로 다른 신고자 수로 자동 숨김을 판정한다(사유 무관).
// - CHAT: 우리 파이프라인이 만든 응답에 대한 신고. 제작자 책임이 아니라 자동 숨김엔 세지 않는다.
// 유니크 제약은 두지 않는다 — message_id가 null인 행끼리는 Postgres가 서로 다른 값으로 봐서 어차피 못 막는다.
// 대신 숨김 판정을 count(distinct reporter)로 해서 중복 행이 판정에 영향을 주지 않게 한다.
@Entity
@Getter
@NoArgsConstructor
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_character", columnList = "character_id")
})
public class Report extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User reporter;

    // CHAT 신고도 채운다(그 방의 캐릭터) — 캐릭터별로 신고를 모아 보려고.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatCharacter character;

    // FK로 잇지 않는다 — 재생성·되돌리기로 메시지 행이 지워져도 신고는 남아야 한다. 내용은 contentSnapshot에.
    @Column(name = "message_id")
    private Long messageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, length = 20)
    private ReportTarget target;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private ReportReason reason;

    @Column(name = "detail", length = 200)
    private String detail;

    // 신고 시점의 증거. CHAT = 직전 사용자 메시지 + AI 응답, IMAGE = 그때의 image_url.
    // 제작자가 이미지를 바꾸거나 사용자가 메시지를 되돌려도 무엇을 신고했는지 알 수 있게 복사해 둔다.
    @Column(name = "content_snapshot", columnDefinition = "TEXT")
    private String contentSnapshot;

    @Builder
    public Report(User reporter, ChatCharacter character, Long messageId, ReportTarget target,
                   ReportReason reason, String detail, String contentSnapshot) {
        this.reporter = reporter;
        this.character = character;
        this.messageId = messageId;
        this.target = target;
        this.reason = reason;
        this.detail = detail;
        this.contentSnapshot = contentSnapshot;
    }
}

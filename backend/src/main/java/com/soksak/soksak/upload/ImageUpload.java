package com.soksak.soksak.upload;

import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// 검사까지 간 업로드 한 건(한 행 = 누가 언제 어떤 파일을 올렸고 점수가 몇이었나). 두 가지에 같이 쓴다.
// - 횟수 제한: 사용자별 최근 1시간·24시간 행 수를 센다. 차단된 것도 센다(같은 파일 연타로 떠보는 걸 막으려고).
// - 검사 캐시: 같은 sha256이 이미 있으면 OpenAI를 다시 부르지 않고 그 점수를 쓴다.
//   판정이 아니라 점수를 두므로, 기준점을 바꾸면 캐시된 것도 새 기준으로 다시 판정된다.
// 형식 오류·검사 실패(503)는 기록하지 않는다 — 우리 쪽 장애로 사용자 한도가 깎이지 않게.
@Entity
@Getter
@NoArgsConstructor
@Table(name = "image_uploads", indexes = {
        @Index(name = "idx_image_uploads_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_image_uploads_sha256", columnList = "sha256")
})
public class ImageUpload extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private double sexual;

    @Column(name = "violence_graphic", nullable = false)
    private double violenceGraphic;

    // 이 행을 만들 때의 판정. 캐시로 쓸 땐 보지 않는다(점수로 다시 판정) — 나중에 "N회 차단 시 잠금"을 셀 때 쓴다.
    @Column(nullable = false)
    private boolean blocked;

    @Builder
    public ImageUpload(User user, String sha256, double sexual, double violenceGraphic, boolean blocked) {
        this.user = user;
        this.sha256 = sha256;
        this.sexual = sexual;
        this.violenceGraphic = violenceGraphic;
        this.blocked = blocked;
    }
}

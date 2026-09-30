package com.soksak.soksak.upload;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ImageUploadRepository extends JpaRepository<ImageUpload, Long> {
    // 횟수 제한용 — 이 시각 이후 이 사용자가 검사까지 간 업로드 수.
    long countByUser_IdAndCreatedAtAfter(Long userId, LocalDateTime since);

    // 검사 캐시 — 같은 파일의 가장 최근 점수(누가 올렸든 점수는 이미지에 대한 것이라 공유한다).
    Optional<ImageUpload> findFirstBySha256OrderByIdDesc(String sha256);
}

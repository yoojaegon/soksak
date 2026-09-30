package com.soksak.soksak.upload;

import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.upload.moderation.ImageModerator;
import com.soksak.soksak.upload.moderation.ModerationPolicy;
import com.soksak.soksak.upload.moderation.ModerationScores;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

// 업로드 한 건의 순서를 잡는다: 형식 판별 → 횟수 제한 → 검사(같은 파일이면 캐시) → 기록 → 저장.
// @Transactional을 붙이지 않는다. 붙이면
//  ① 차단 시 던지는 예외에 기록 INSERT가 같이 롤백돼 차단 이력이 사라지고
//  ② OpenAI 응답을 기다리는 동안(최대 read-timeout) DB 커넥션을 붙잡는다.
// save는 각자 자기 트랜잭션으로 바로 커밋된다.
// 같은 사용자의 동시 요청은 count 확인을 같이 통과해 한두 장 넘칠 수 있다 — 이 규모에선 허용.
@Service
@Slf4j
public class ImageUploadService {
    private final ImageStorageService imageStorageService;
    private final ImageModerator imageModerator;
    private final ImageUploadRepository imageUploadRepository;
    private final UserRepository userRepository;
    private final int perHour;
    private final int perDay;

    public ImageUploadService(
            ImageStorageService imageStorageService,
            ImageModerator imageModerator,
            ImageUploadRepository imageUploadRepository,
            UserRepository userRepository,
            @Value("${uploads.limit.per-hour:10}") int perHour,
            @Value("${uploads.limit.per-day:30}") int perDay
    ) {
        this.imageStorageService = imageStorageService;
        this.imageModerator = imageModerator;
        this.imageUploadRepository = imageUploadRepository;
        this.userRepository = userRepository;
        this.perHour = perHour;
        this.perDay = perDay;
    }

    public String upload(String loginId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 형식 판별·해시·검사·저장이 같은 바이트를 보도록 한 번만 읽는다(최대 5MB라 메모리에 올려도 된다).
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
        // 싼 것부터 거른다 — 형식이 틀린 건 한도에도 안 센다.
        ImageType type = ImageType.detect(bytes);

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        checkLimit(user.getId());

        String sha256 = sha256(bytes);
        // 같은 파일이면 OpenAI를 다시 부르지 않는다. 완전히 같은 바이트만 맞는다(재인코딩하면 빗나감) —
        // 목적은 레이트 리밋 절약이고, 우회 방지는 위의 횟수 제한이 맡는다.
        boolean cached = true;
        ModerationScores scores = imageUploadRepository.findFirstBySha256OrderByIdDesc(sha256)
                .map(u -> new ModerationScores(u.getSexual(), u.getViolenceGraphic()))
                .orElse(null);
        if (scores == null) {
            cached = false;
            scores = imageModerator.score(bytes, type.mimeType);   // 실패면 503, 기록 없이 끝난다
        }

        boolean blocked = ModerationPolicy.blocks(scores);
        imageUploadRepository.save(ImageUpload.builder()
                .user(user)
                .sha256(sha256)
                .sexual(scores.sexual())
                .violenceGraphic(scores.violenceGraphic())
                .blocked(blocked)
                .build());

        if (blocked) {
            // 이미지는 남기지 않고 점수만. 누가 올렸는지는 MDC(traceId/user)가 붙여 준다.
            log.warn("이미지 차단 sexual={} violence/graphic={} cached={}",
                    scores.sexual(), scores.violenceGraphic(), cached);
            throw new BusinessException(ErrorCode.IMAGE_REJECTED);
        }

        // 캐시가 맞아도 파일은 새로 저장한다 — 두 캐릭터가 한 파일을 같이 쓰면
        // 한쪽을 지울 때(B4 고아 파일 청소) 다른 쪽 이미지가 깨진다.
        return imageStorageService.store(bytes, type);
    }

    private void checkLimit(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        if (imageUploadRepository.countByUser_IdAndCreatedAtAfter(userId, now.minusHours(1)) >= perHour
                || imageUploadRepository.countByUser_IdAndCreatedAtAfter(userId, now.minusDays(1)) >= perDay) {
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_LIMITED);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // 모든 JVM에 있어야 하는 알고리즘
        }
    }
}

package com.soksak.soksak.upload;

import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.upload.moderation.ImageModerator;
import com.soksak.soksak.upload.moderation.ModerationScores;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {

    // png 매직 넘버 + 채움 — 형식 판별만 통과하면 된다.
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    @Mock ImageStorageService imageStorageService;
    @Mock ImageModerator imageModerator;
    @Mock ImageUploadRepository imageUploadRepository;
    @Mock UserRepository userRepository;

    ImageUploadService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new ImageUploadService(imageStorageService, imageModerator, imageUploadRepository, userRepository, 10, 30, 3, 500);
        user = new User();
        ReflectionTestUtils.setField(user, "id", 1L);
        lenient().when(userRepository.findByLoginId("tester")).thenReturn(Optional.of(user));
        lenient().when(imageStorageService.store(any(), any())).thenReturn("/api/uploads/x.png");
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "a.png", "image/png", PNG);
    }

    private void usedThisHour(long hour, long day) {
        when(imageUploadRepository.countByUser_IdAndCreatedAtAfter(eq(1L), any()))
                .thenReturn(hour, day);
    }

    private ImageUpload cachedRow(double sexual) {
        return ImageUpload.builder().user(user).sha256("h").sexual(sexual).violenceGraphic(0).blocked(false).build();
    }

    @Test
    @DisplayName("1시간 한도에 차면 429 — 검사도 저장도 하지 않는다")
    void hourly_limit() {
        usedThisHour(10, 10);

        assertThatThrownBy(() -> service.upload("tester", png()))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_UPLOAD_LIMITED);
        verify(imageModerator, never()).score(any(), anyString());
        verify(imageStorageService, never()).store(any(), any());
    }

    @Test
    @DisplayName("24시간 한도에 차도 429")
    void daily_limit() {
        usedThisHour(3, 30);

        assertThatThrownBy(() -> service.upload("tester", png()))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_UPLOAD_LIMITED);
    }

    @Test
    @DisplayName("24시간 차단이 3회면 업로드 잠금 403 — 개인 한도보다 먼저, 검사도 하지 않는다")
    void block_lock() {
        when(imageUploadRepository.countByUser_IdAndBlockedTrueAndCreatedAtAfter(eq(1L), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.upload("tester", png()))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_UPLOAD_LOCKED);
        verify(imageModerator, never()).score(any(), anyString());
        verify(imageUploadRepository, never()).save(any());
    }

    @Test
    @DisplayName("차단 2회까지는 잠그지 않는다")
    void two_blocks_not_locked() {
        when(imageUploadRepository.countByUser_IdAndBlockedTrueAndCreatedAtAfter(eq(1L), any())).thenReturn(2L);
        usedThisHour(2, 2);
        when(imageUploadRepository.findFirstBySha256OrderByIdDesc(anyString())).thenReturn(Optional.of(cachedRow(0.1)));

        assertThat(service.upload("tester", png())).isEqualTo("/api/uploads/x.png");
    }

    @Test
    @DisplayName("전체 24시간 상한에 차면 503 — 검사도 저장도 하지 않는다")
    void global_limit() {
        usedThisHour(0, 0);
        when(imageUploadRepository.countByCreatedAtAfter(any())).thenReturn(500L);

        assertThatThrownBy(() -> service.upload("tester", png()))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_UPLOAD_BUSY);
        verify(imageModerator, never()).score(any(), anyString());
        verify(imageStorageService, never()).store(any(), any());
    }

    @Test
    @DisplayName("처음 보는 파일은 검사하고 점수를 기록한 뒤 저장한다")
    void cache_miss_calls_moderator() {
        usedThisHour(0, 0);
        when(imageUploadRepository.findFirstBySha256OrderByIdDesc(anyString())).thenReturn(Optional.empty());
        when(imageModerator.score(any(), eq("image/png"))).thenReturn(new ModerationScores(0.1, 0.2));

        assertThat(service.upload("tester", png())).isEqualTo("/api/uploads/x.png");

        ArgumentCaptor<ImageUpload> saved = ArgumentCaptor.forClass(ImageUpload.class);
        verify(imageUploadRepository).save(saved.capture());
        assertThat(saved.getValue().getSexual()).isEqualTo(0.1);
        assertThat(saved.getValue().isBlocked()).isFalse();
        assertThat(saved.getValue().getSha256()).hasSize(64);
    }

    @Test
    @DisplayName("같은 파일이면 OpenAI를 다시 부르지 않는다 — 기록은 남기고(한도에 셈) 파일은 새로 저장")
    void cache_hit_skips_moderator() {
        usedThisHour(0, 0);
        when(imageUploadRepository.findFirstBySha256OrderByIdDesc(anyString())).thenReturn(Optional.of(cachedRow(0.1)));

        service.upload("tester", png());

        verify(imageModerator, never()).score(any(), anyString());
        verify(imageUploadRepository).save(any());
        verify(imageStorageService).store(any(), eq(ImageType.PNG));
    }

    @Test
    @DisplayName("캐시된 점수는 지금 기준으로 다시 판정한다 — 차단이면 기록은 남기고 저장은 안 한다")
    void cached_block_is_rejudged_and_recorded() {
        usedThisHour(0, 0);
        // blocked=false로 기록됐던 행이라도 점수가 지금 기준을 넘으면 막힌다.
        when(imageUploadRepository.findFirstBySha256OrderByIdDesc(anyString())).thenReturn(Optional.of(cachedRow(0.9)));

        assertThatThrownBy(() -> service.upload("tester", png()))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_REJECTED);

        ArgumentCaptor<ImageUpload> saved = ArgumentCaptor.forClass(ImageUpload.class);
        verify(imageUploadRepository).save(saved.capture());
        assertThat(saved.getValue().isBlocked()).isTrue();
        verify(imageStorageService, never()).store(any(), any());
    }

    @Test
    @DisplayName("검사 실패(503)는 기록하지 않는다 — 우리 쪽 장애로 한도가 깎이지 않게")
    void moderation_failure_is_not_recorded() {
        usedThisHour(0, 0);
        when(imageUploadRepository.findFirstBySha256OrderByIdDesc(anyString())).thenReturn(Optional.empty());
        when(imageModerator.score(any(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.IMAGE_MODERATION_UNAVAILABLE));

        assertThatThrownBy(() -> service.upload("tester", png()))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_MODERATION_UNAVAILABLE);
        verify(imageUploadRepository, never()).save(any());
    }

    @Test
    @DisplayName("형식이 틀리면 한도 확인 전에 거절한다")
    void wrong_type_rejected_before_limit() {
        MockMultipartFile gif = new MockMultipartFile("file", "a.gif", "image/gif",
                "GIF89a______".getBytes());

        assertThatThrownBy(() -> service.upload("tester", gif))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        verify(imageUploadRepository, never()).countByUser_IdAndCreatedAtAfter(any(), any());
    }
}

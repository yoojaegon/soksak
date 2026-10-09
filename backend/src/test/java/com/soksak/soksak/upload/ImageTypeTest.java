package com.soksak.soksak.upload;

import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class ImageTypeTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    // 길이(4) + 타입(4) + 데이터 + CRC(4). CRC는 판별에 안 쓰므로 0으로 채운다.
    private static void chunk(ByteArrayOutputStream out, String type, int dataLength) {
        out.write(dataLength >>> 24);
        out.write(dataLength >>> 16);
        out.write(dataLength >>> 8);
        out.write(dataLength);
        out.writeBytes(type.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(new byte[dataLength + 4]);
    }

    private static byte[] png(String... chunkTypes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PNG_SIGNATURE);
        for (String type : chunkTypes) {
            chunk(out, type, type.equals("IHDR") ? 13 : 8);
        }
        return out.toByteArray();
    }

    // RIFF(4) + 크기(4) + WEBP(4) + VP8X(4) + 청크 크기(4) + 플래그(1) + 나머지
    private static byte[] webpVp8x(int flags) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(new byte[4]);
        out.writeBytes("WEBPVP8X".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(new byte[]{10, 0, 0, 0});
        out.write(flags);
        out.writeBytes(new byte[9]);
        return out.toByteArray();
    }

    private static ErrorCode rejection(byte[] bytes) {
        return ((BusinessException) catchThrowable(() -> ImageType.detect(bytes)))
                .getErrorCode();
    }

    @Test
    @DisplayName("정지 png는 받는다 — IDAT 뒤에 뭐가 와도 보지 않는다")
    void static_png() {
        assertThat(ImageType.detect(png("IHDR", "IDAT", "IEND"))).isEqualTo(ImageType.PNG);
    }

    @Test
    @DisplayName("apng(IDAT 앞 acTL)는 거절한다")
    void animated_png() {
        assertThat(rejection(png("IHDR", "acTL", "IDAT", "IEND")))
                .isEqualTo(ErrorCode.ANIMATED_IMAGE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("청크 길이가 파일 끝을 넘어도 터지지 않는다")
    void truncated_png() {
        byte[] bytes = png("IHDR");
        bytes[8] = (byte) 0x7F;   // IHDR 길이를 거대하게
        assertThat(ImageType.detect(bytes)).isEqualTo(ImageType.PNG);
    }

    @Test
    @DisplayName("VP8X webp는 animation 플래그(0x02)가 없으면 받는다")
    void static_vp8x_webp() {
        assertThat(ImageType.detect(webpVp8x(0x10))).isEqualTo(ImageType.WEBP);   // 0x10 = alpha
    }

    @Test
    @DisplayName("움직이는 webp는 거절한다")
    void animated_webp() {
        assertThat(rejection(webpVp8x(0x12))).isEqualTo(ErrorCode.ANIMATED_IMAGE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("gif는 형식째 거절한다")
    void gif() {
        assertThatThrownBy(() -> ImageType.detect("GIF89a______".getBytes(StandardCharsets.US_ASCII)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }
}

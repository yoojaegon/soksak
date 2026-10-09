package com.soksak.soksak.upload;

import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;

// 받는 이미지 형식. 확장자와 Content-Type은 클라이언트가 마음대로 붙일 수 있으니 앞부분 바이트로 판별한다.
// 움짤은 받지 않는다 — 모더레이션이 첫 프레임만 보므로, 뒤 프레임에 문제를 숨긴 움짤이 통과한다.
// gif는 형식째 빼고, webp·png는 같은 시그니처로 움직이는 변종(animated webp·apng)이 있어 청크를 보고 거른다.
// 프론트의 리사이즈는 1024px 이하면 원본을 그대로 보내므로 여기서 막아야 한다.
public enum ImageType {
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp");

    final String extension;
    final String mimeType;

    ImageType(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    // 앞 12바이트의 매직 넘버로 형식을 판별한다. 아는 이미지가 아니거나 움짤이면 거절.
    static ImageType detect(byte[] bytes) {
        if (bytes.length < 12) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) return JPG;
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47)) {
            if (isAnimatedPng(bytes)) throw new BusinessException(ErrorCode.ANIMATED_IMAGE_NOT_SUPPORTED);
            return PNG;
        }
        // WEBP = RIFF....WEBP (4~7바이트는 파일 크기라 건너뛴다)
        if (startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            if (isAnimatedWebp(bytes)) throw new BusinessException(ErrorCode.ANIMATED_IMAGE_NOT_SUPPORTED);
            return WEBP;
        }
        throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    // 움직이는 webp는 첫 청크가 VP8X이고 그 플래그 바이트(20번째)의 0x02(animation)가 켜져 있다.
    private static boolean isAnimatedWebp(byte[] bytes) {
        return bytes.length > 20
                && bytes[12] == 'V' && bytes[13] == 'P' && bytes[14] == '8' && bytes[15] == 'X'
                && (bytes[20] & 0x02) != 0;
    }

    // apng는 첫 IDAT보다 앞에 acTL 청크가 있다. 청크 = 길이(4) + 타입(4) + 데이터 + CRC(4).
    private static boolean isAnimatedPng(byte[] bytes) {
        long pos = 8;   // 시그니처 뒤
        while (pos + 8 <= bytes.length) {
            int p = (int) pos;
            long length = ((bytes[p] & 0xFFL) << 24) | ((bytes[p + 1] & 0xFFL) << 16)
                    | ((bytes[p + 2] & 0xFFL) << 8) | (bytes[p + 3] & 0xFFL);
            if (isType(bytes, p + 4, "acTL")) return true;
            if (isType(bytes, p + 4, "IDAT")) return false;
            pos += 12 + length;
        }
        return false;
    }

    private static boolean isType(byte[] bytes, int offset, String type) {
        for (int i = 0; i < 4; i++) {
            if (bytes[offset + i] != type.charAt(i)) return false;
        }
        return true;
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }
}

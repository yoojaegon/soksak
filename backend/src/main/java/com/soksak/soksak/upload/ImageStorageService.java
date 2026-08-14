package com.soksak.soksak.upload;

import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

// 업로드된 이미지를 서버 파일시스템에 저장하고, 화면에서 쓸 주소를 돌려준다.
// 저장 위치는 uploads.dir(기본 ./uploads), 주소는 uploads.public-path(기본 /uploads).
// 나중에 오브젝트 스토리지로 옮기더라도 "파일을 주면 주소를 준다"는 이 계약은 그대로 두면 되고,
// 컨트롤러·프론트·DB(characters.image_url)는 손대지 않아도 된다.
@Service
@Slf4j
public class ImageStorageService {
    private final Path root;
    private final String publicPath;

    public ImageStorageService(
            @Value("${uploads.dir:uploads}") String dir,
            @Value("${uploads.public-path:/uploads}") String publicPath
    ) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        this.publicPath = publicPath;
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 확장자와 Content-Type은 클라이언트가 마음대로 붙일 수 있으니 앞부분 바이트로 실제 형식을 본다.
        String extension = detectExtension(file);
        // 파일명은 항상 새로 짓는다 — 사용자가 준 이름을 경로에 쓰면 경로 탈출·인코딩 문제가 따라온다.
        String storedName = UUID.randomUUID() + "." + extension;

        try {
            Files.createDirectories(root);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, root.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("이미지 저장 실패 name={}", storedName, e);
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        return publicPath + "/" + storedName;
    }

    // 파일 앞 12바이트의 매직 넘버로 형식을 판별한다. 아는 이미지가 아니면 거절.
    private String detectExtension(MultipartFile file) {
        byte[] head = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(head, 0, head.length);
            if (read < head.length) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        if (startsWith(head, 0xFF, 0xD8, 0xFF)) return "jpg";
        if (startsWith(head, 0x89, 0x50, 0x4E, 0x47)) return "png";
        if (startsWith(head, 0x47, 0x49, 0x46, 0x38)) return "gif";
        // WEBP = RIFF....WEBP (4~7바이트는 파일 크기라 건너뛴다)
        if (startsWith(head, 0x52, 0x49, 0x46, 0x46)
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
        throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }
}

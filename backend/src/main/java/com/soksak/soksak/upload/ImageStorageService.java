package com.soksak.soksak.upload;

import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

// 검사를 통과한 이미지를 서버 파일시스템에 저장하고, 화면에서 쓸 주소를 돌려준다.
// 저장 위치는 uploads.dir(yml 기본 ./uploads), 주소는 (컨텍스트 경로 + uploads.public-path).
// 나중에 오브젝트 스토리지로 옮기더라도 "파일을 주면 주소를 준다"는 이 계약은 그대로 두면 되고,
// 컨트롤러·프론트·DB(characters.image_url)는 손대지 않아도 된다.
// 검사·횟수 제한은 ImageUploadService가 맡는다 — 스토리지를 바꿀 때 이 클래스만 갈아 끼우도록.
@Service
@Slf4j
public class ImageStorageService {
    private final Path root;
    private final String urlPrefix;

    public ImageStorageService(
            @Value("${uploads.dir}") String dir,
            @Value("${uploads.public-path}") String publicPath,
            // 정적 리소스 매핑(WebConfig)은 컨텍스트 경로가 벗겨진 뒤에 걸리지만,
            // 브라우저가 요청하는 주소에는 그게 붙어 있다. 여기서 만드는 건 DB에 저장돼
            // <img src>로 쓰일 '브라우저 기준' 주소라 앞에 컨텍스트 경로를 더해 준다.
            // (설정이 없으면 빈 문자열이라 로컬에서는 지금과 똑같이 /uploads/... 가 된다.)
            @Value("${server.servlet.context-path:}") String contextPath
    ) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        this.urlPrefix = contextPath + publicPath;
    }

    public String store(byte[] bytes, ImageType type) {
        // 파일명은 항상 새로 짓는다 — 사용자가 준 이름을 경로에 쓰면 경로 탈출·인코딩 문제가 따라온다.
        String storedName = UUID.randomUUID() + "." + type.extension;

        try {
            Files.createDirectories(root);
            Files.write(root.resolve(storedName), bytes);
        } catch (IOException e) {
            log.error("이미지 저장 실패 name={}", storedName, e);
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        return urlPrefix + "/" + storedName;
    }
}

package com.soksak.soksak.upload;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 이미지 업로드 — 파일을 받아 저장하고 주소만 돌려준다.
// 캐릭터와 묶지 않은 이유: 캐릭터를 만들기 전(생성 폼)에도 업로드가 필요하고,
// 나중에 페르소나·프로필 이미지에도 그대로 쓸 수 있다.
@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class ImageUploadController {
    private final ImageStorageService imageStorageService;

    @PostMapping("/images")
    public ResponseEntity<ImageUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(new ImageUploadResponse(imageStorageService.store(file)));
    }
}

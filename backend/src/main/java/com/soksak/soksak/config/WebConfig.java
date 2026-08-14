package com.soksak.soksak.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

// Page<> 직렬화 시 PageImpl 안정화 경고 제거 — DTO 형태로 직렬화
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class WebConfig implements WebMvcConfigurer {

    @Value("${uploads.dir:uploads}")
    private String uploadsDir;

    @Value("${uploads.public-path:/uploads}")
    private String uploadsPublicPath;

    // 업로드된 이미지를 정적 파일로 내보낸다(POST /uploads/images 는 컨트롤러가 먼저 잡는다).
    // 파일명이 UUID라 내용이 바뀌지 않으므로 길게 캐시해도 안전하다.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadsDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(uploadsPublicPath + "/**")
                .addResourceLocations(location)
                .setCachePeriod(60 * 60 * 24 * 30);
    }
}

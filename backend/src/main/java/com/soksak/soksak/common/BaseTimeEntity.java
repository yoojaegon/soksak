package com.soksak.soksak.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// 생성·수정 시각을 자동으로 채워 주는 공통 부모. 엔티티가 이 클래스를 상속하면 된다.
// AuditingEntityListener가 저장 직전에 값을 넣어 주는데, @EnableJpaAuditing(SoksakApplication)이 있어야 동작한다.
// 주의: @Modifying 벌크 쿼리는 엔티티를 거치지 않아서 updatedAt이 갱신되지 않는다.
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
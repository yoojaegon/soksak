package com.soksak.soksak.credit.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 충전 요청. <b>수량이 아니라 묶음 id를 받는다</b> — 수량을 받으면 프론트가 보낸 숫자를 그대로
 * 지급하게 되어 개발자도구에서 얼마든지 고칠 수 있다.
 */
public record TopUpRequest(@NotBlank String packId) {
}

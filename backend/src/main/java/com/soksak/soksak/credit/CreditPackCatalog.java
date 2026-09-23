package com.soksak.soksak.credit;

import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;

import java.util.List;

/**
 * 살 수 있는 마디 묶음.
 * <p>
 * ⚠️ <b>결제는 만들지 않지만 고를 수 있는 값은 서버가 소유한다.</b> 프론트가 보낸 숫자를 그대로
 * 지급하면 개발자도구에서 amount만 고쳐 무한정 받을 수 있다 — 그래서 요청은 {@code packId}로
 * 받고 수량은 여기서만 정해진다. {@code ModelCatalog}과 같은 규칙(카탈로그는 백엔드 소유,
 * 프론트는 폴백 없이 GET 결과만 신뢰)이다.
 * <p>
 * ⛔ <b>결제 연동은 안 한다</b>([[todos]] A9). {@code priceKrw}는 "원래는 결제로 넘어가는
 * 자리"를 화면에 보여주기 위한 표시값일 뿐, 어디에서도 검증하거나 청구하지 않는다.
 */
public final class CreditPackCatalog {

    /**
     * @param amount   지급할 마디
     * @param priceKrw 표시용 가격. 실제로 받지 않는다.
     */
    public record Pack(String id, int amount, int priceKrw, String label) {}

    private static final List<Pack> PACKS = List.of(
            new Pack("small", 10, 1000, "한 대화 더"),
            new Pack("medium", 100, 9000, "느긋하게"),
            new Pack("large", 500, 39000, "실컷")
    );

    private CreditPackCatalog() {}

    public static List<Pack> all() {
        return PACKS;
    }

    public static Pack byId(String id) {
        return PACKS.stream()
                .filter(pack -> pack.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN_CREDIT_PACK));
    }
}

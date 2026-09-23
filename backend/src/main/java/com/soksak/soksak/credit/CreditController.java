package com.soksak.soksak.credit;

import com.soksak.soksak.credit.dto.CreditResponse;
import com.soksak.soksak.credit.dto.TopUpRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    /** 내 잔액. 프론트는 입력칸 옆에 띄우고, 전송이 끝날 때마다 다시 부른다. */
    @GetMapping("/me")
    public ResponseEntity<CreditResponse> myCredits(Authentication authentication) {
        return ResponseEntity.ok(new CreditResponse(creditService.balanceOf(authentication.getName())));
    }

    /** 고를 수 있는 묶음. 프론트는 이 목록만 그린다(하드코딩한 폴백을 두지 않는다). */
    @GetMapping("/packs")
    public ResponseEntity<List<CreditPackCatalog.Pack>> packs() {
        return ResponseEntity.ok(CreditPackCatalog.all());
    }

    /** 충전 — 결제 없이 바로 지급하고 갱신된 잔액을 돌려준다. */
    @PostMapping("/topup")
    public ResponseEntity<CreditResponse> topUp(Authentication authentication,
                                                @Valid @RequestBody TopUpRequest request) {
        return ResponseEntity.ok(
                new CreditResponse(creditService.topUp(authentication.getName(), request.packId())));
    }
}

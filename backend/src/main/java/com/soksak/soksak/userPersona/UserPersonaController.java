package com.soksak.soksak.userPersona;

import com.soksak.soksak.auth.AuthUser;
import com.soksak.soksak.userPersona.dto.CreateUserPersonaRequest;
import com.soksak.soksak.userPersona.dto.UpdateUserPersonaRequest;
import com.soksak.soksak.userPersona.dto.UserPersonaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user-personas")
@RequiredArgsConstructor
public class UserPersonaController {
    private final UserPersonaService userPersonaService;

    @PostMapping
    public ResponseEntity<UserPersonaResponse> createPersona(
            @AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody CreateUserPersonaRequest request) {
        UserPersonaResponse response = userPersonaService.createPersona(me.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 로그인한 본인의 페르소나 목록 */
    @GetMapping
    public ResponseEntity<List<UserPersonaResponse>> getMyPersonas(@AuthenticationPrincipal AuthUser me) {
        return ResponseEntity.ok(userPersonaService.getMyPersonas(me.id()));
    }

    /** 본인 페르소나 수정 */
    @PutMapping("/{id}")
    public ResponseEntity<UserPersonaResponse> updatePersona(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserPersonaRequest request) {
        return ResponseEntity.ok(userPersonaService.updatePersona(me.id(), id, request));
    }

    /** 기본 페르소나로 지정 */
    @PatchMapping("/{id}/default")
    public ResponseEntity<UserPersonaResponse> setDefault(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long id) {
        return ResponseEntity.ok(userPersonaService.setDefault(me.id(), id));
    }

    /** 본인 페르소나 삭제 (마지막 한 개는 삭제 불가) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePersona(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long id) {
        userPersonaService.deletePersona(me.id(), id);
        return ResponseEntity.noContent().build();
    }
}

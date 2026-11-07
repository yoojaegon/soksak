package com.soksak.soksak.lore;

import com.soksak.soksak.auth.AuthUser;
import com.soksak.soksak.lore.dto.CreateLoreRequest;
import com.soksak.soksak.lore.dto.LoreResponse;
import com.soksak.soksak.lore.dto.UpdateLoreRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/characters/{characterId}/lores")
public class LoreController {
    private final LoreService loreService;

    @PostMapping
    public ResponseEntity<LoreResponse> createLore(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long characterId,
            @Valid @RequestBody CreateLoreRequest request) {
        LoreResponse response = loreService.createLore(me.id(), characterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<LoreResponse>> getLoreList(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long characterId) {
        return ResponseEntity.ok(loreService.getLoreList(me.id(), characterId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LoreResponse> updateLore(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long characterId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateLoreRequest request) {
        return ResponseEntity.ok(loreService.updateLore(me.id(), characterId, id, request));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<LoreResponse> updateEnabled(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long characterId,
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(loreService.updateEnabled(me.id(), characterId, id, enabled));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLore(
            @AuthenticationPrincipal AuthUser me,
            @PathVariable Long characterId,
            @PathVariable Long id) {
        loreService.deleteLore(me.id(), characterId, id);
        return ResponseEntity.noContent().build();
    }
}

package com.soksak.soksak.lore;

import com.soksak.soksak.lore.dto.CreateLoreRequest;
import com.soksak.soksak.lore.dto.LoreResponse;
import com.soksak.soksak.lore.dto.UpdateLoreRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/characters/{characterId}/lores")
public class LoreController {
    private final LoreService loreService;

    @PostMapping
    public ResponseEntity<LoreResponse> createLore(
            Authentication authentication,
            @PathVariable Long characterId,
            @Valid @RequestBody CreateLoreRequest request){
        LoreResponse response = loreService.createLore(authentication.getName(), characterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<LoreResponse>> getLoreList(
            Authentication authentication,
            @PathVariable Long characterId) {
        return ResponseEntity.ok(loreService.getLoreList(authentication.getName(), characterId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoreResponse> getLore(
            Authentication authentication,
            @PathVariable Long characterId,
            @PathVariable Long id) {
        return ResponseEntity.ok(loreService.getLore(authentication.getName(), characterId, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LoreResponse> updateLore(
            Authentication authentication,
            @PathVariable Long characterId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateLoreRequest request) {
        LoreResponse response = loreService.updateLore(authentication.getName(), characterId, id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<LoreResponse> updateEnabled(
            Authentication authentication,
            @PathVariable Long characterId,
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        LoreResponse response = loreService.updateEnabled(authentication.getName(), characterId, id, enabled);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLore(
            Authentication authentication,
            @PathVariable Long characterId,
            @PathVariable Long id) {
        loreService.deleteLore(authentication.getName(), characterId, id);
        return ResponseEntity.noContent().build();
    }
}

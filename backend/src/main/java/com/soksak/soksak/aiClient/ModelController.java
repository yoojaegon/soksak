package com.soksak.soksak.aiClient;

import com.soksak.soksak.aiClient.dto.ModelResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModelController {
    @GetMapping("/models")
    public ResponseEntity<ModelResponse> getModels() {
        return ResponseEntity.ok(
                new ModelResponse(ModelCatalog.entries(), ModelCatalog.defaultId()));
    }
}

package com.soksak.soksak.userPersona;

import com.soksak.soksak.userPersona.dto.CreateUserPersonaRequest;
import com.soksak.soksak.userPersona.dto.UserPersonaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user-personas")
@RequiredArgsConstructor
public class UserPersonaController {
    private final UserPersonaService userPersonaService;

    @PostMapping
    public ResponseEntity<UserPersonaResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateUserPersonaRequest request) {
        UserPersonaResponse response = userPersonaService.create(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

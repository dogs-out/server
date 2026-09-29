package com.dogsout.server.sos;

import com.dogsout.server.sos.SosDtos.AlertResponse;
import com.dogsout.server.sos.SosDtos.CloseAlertRequest;
import com.dogsout.server.sos.SosDtos.ContactOwnerResponse;
import com.dogsout.server.sos.SosDtos.RaiseAlertRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sos")
@RequiredArgsConstructor
public class SosController {

    private final SosService sosService;

    @PostMapping
    public ResponseEntity<AlertResponse> raise(Authentication auth, @Valid @RequestBody RaiseAlertRequest request) {
        return ResponseEntity.ok(sosService.raise(auth.getName(), request));
    }

    @GetMapping
    public ResponseEntity<List<AlertResponse>> nearby(Authentication auth) {
        return ResponseEntity.ok(sosService.nearby(auth.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> get(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(sosService.get(auth.getName(), id));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<AlertResponse> close(Authentication auth, @PathVariable Long id,
                                               @RequestBody CloseAlertRequest request) {
        return ResponseEntity.ok(sosService.close(auth.getName(), id, request.found()));
    }

    @PostMapping("/{id}/contact")
    public ResponseEntity<ContactOwnerResponse> contact(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(sosService.contactOwner(auth.getName(), id));
    }
}

package com.dogsout.server.school;

import com.dogsout.server.school.SchoolDtos.EventRequest;
import com.dogsout.server.school.SchoolDtos.EventResponse;
import com.dogsout.server.school.SchoolDtos.ParticipantResponse;
import com.dogsout.server.school.SchoolDtos.SchoolRequest;
import com.dogsout.server.school.SchoolDtos.SchoolResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schools;

    @GetMapping("/schools")
    public List<SchoolResponse> nearby(Authentication auth) {
        return schools.nearby(auth.getName());
    }

    /** 204 when this account has no school. */
    @GetMapping("/schools/mine")
    public ResponseEntity<SchoolResponse> mine(Authentication auth) {
        return schools.mine(auth.getName()).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/schools/{id}")
    public SchoolResponse get(Authentication auth, @PathVariable Long id) {
        return schools.get(auth.getName(), id);
    }

    @PostMapping("/schools")
    public ResponseEntity<SchoolResponse> register(Authentication auth, @Valid @RequestBody SchoolRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schools.register(auth.getName(), request));
    }

    @PutMapping("/schools/mine")
    public SchoolResponse update(Authentication auth, @Valid @RequestBody SchoolRequest request) {
        return schools.update(auth.getName(), request);
    }

    @DeleteMapping("/schools/mine")
    public ResponseEntity<Void> deleteMine(Authentication auth) {
        schools.deleteMine(auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/schools/{id}/report")
    public ResponseEntity<Void> report(Authentication auth, @PathVariable Long id,
                                       @Valid @RequestBody com.dogsout.server.moderation.ReportRequest request) {
        schools.report(auth.getName(), id, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/schools/mine/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SchoolResponse logo(Authentication auth, @RequestParam("file") MultipartFile file) {
        return schools.setLogo(auth.getName(), file);
    }

    @GetMapping("/school-events")
    public List<EventResponse> events(Authentication auth) {
        return schools.upcomingNearby(auth.getName());
    }

    @GetMapping("/school-events/{id}")
    public EventResponse event(Authentication auth, @PathVariable Long id) {
        return schools.getEvent(auth.getName(), id);
    }

    @PostMapping("/school-events")
    public ResponseEntity<EventResponse> create(Authentication auth, @Valid @RequestBody EventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schools.createEvent(auth.getName(), request));
    }

    @PutMapping("/school-events/{id}")
    public EventResponse update(Authentication auth, @PathVariable Long id, @Valid @RequestBody EventRequest request) {
        return schools.updateEvent(auth.getName(), id, request);
    }

    @DeleteMapping("/school-events/{id}")
    public ResponseEntity<Void> cancel(Authentication auth, @PathVariable Long id) {
        schools.cancelEvent(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/school-events/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EventResponse addPhoto(Authentication auth, @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return schools.addEventPhoto(auth.getName(), id, file);
    }

    @DeleteMapping("/school-events/{id}/photos/{photoId}")
    public EventResponse deletePhoto(Authentication auth, @PathVariable Long id, @PathVariable Long photoId) {
        return schools.deleteEventPhoto(auth.getName(), id, photoId);
    }

    /** Body {"date": "2026-11-04"} for one class of a per-date event; empty otherwise. */
    @PostMapping("/school-events/{id}/join")
    public EventResponse join(Authentication auth, @PathVariable Long id,
                              @RequestBody(required = false) SchoolDtos.JoinRequest request) {
        return schools.join(auth.getName(), id, request == null ? null : request.date());
    }

    @DeleteMapping("/school-events/{id}/join")
    public EventResponse leave(Authentication auth, @PathVariable Long id,
                               @RequestParam(required = false) java.time.LocalDate date) {
        return schools.leave(auth.getName(), id, date);
    }

    @PostMapping("/school-events/{id}/cancel-date")
    public EventResponse cancelDate(Authentication auth, @PathVariable Long id,
                                    @RequestBody SchoolDtos.JoinRequest request) {
        return schools.cancelDate(auth.getName(), id, request.date());
    }

    @GetMapping("/school-events/{id}/participants")
    public List<ParticipantResponse> participants(Authentication auth, @PathVariable Long id) {
        return schools.participantsOf(auth.getName(), id);
    }
}

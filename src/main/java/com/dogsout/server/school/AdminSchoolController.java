package com.dogsout.server.school;

import com.dogsout.server.school.SchoolDtos.AdminSchoolResponse;
import com.dogsout.server.school.SchoolDtos.ReviewRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Approving dog schools from the admin page. /admin/** is ADMIN-only (SecurityConfig). */
@RestController
@RequestMapping("/admin/api/schools")
@RequiredArgsConstructor
public class AdminSchoolController {

    private final SchoolService schools;

    @GetMapping
    public List<AdminSchoolResponse> list(@RequestParam(defaultValue = "PENDING") String status) {
        return schools.forReview(status);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        schools.review(id, request.status(), request.note());
        return ResponseEntity.noContent().build();
    }
}

package com.dogsout.server.admin;

import com.dogsout.server.moderation.Report;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Data for the admin page at /admin. SecurityConfig restricts /admin/api/** to
 * ROLE_ADMIN; the page itself is static and asks for a login.
 */
@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboard;

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return dashboard.overview();
    }

    @GetMapping("/timeseries")
    public Map<String, Object> timeseries(@RequestParam(defaultValue = "30") int days) {
        return dashboard.timeseries(days);
    }

    @GetMapping("/funnel")
    public List<Map<String, Object>> funnel() {
        return dashboard.funnel();
    }

    @GetMapping("/online")
    public Map<String, Object> online() {
        return dashboard.online();
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(@RequestParam(required = false) String q) {
        return dashboard.searchUsers(q);
    }

    @GetMapping("/users/{id}")
    public Map<String, Object> user(@PathVariable Long id) {
        return dashboard.userDetail(id);
    }

    @GetMapping("/reports")
    public List<Report> reports(@RequestParam(required = false) String status) {
        return dashboard.reports(status);
    }

    public record ReportUpdate(String status, String note) {}

    @PatchMapping("/reports/{id}")
    public ResponseEntity<Report> updateReport(@PathVariable Long id, @RequestBody ReportUpdate body, Authentication auth) {
        return ResponseEntity.ok(dashboard.updateReport(id, body.status(), body.note(), auth.getName()));
    }

    @GetMapping("/server")
    public Map<String, Object> server(@RequestParam(defaultValue = "24") int hours) {
        return dashboard.server(hours);
    }
}

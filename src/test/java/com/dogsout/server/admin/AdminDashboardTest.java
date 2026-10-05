package com.dogsout.server.admin;

import com.dogsout.server.auth.EmailService;
import com.dogsout.server.auth.JwtUtil;
import com.dogsout.server.moderation.ModerationService;
import com.dogsout.server.moderation.Report;
import com.dogsout.server.moderation.ReportRequest;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.user.Role;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every admin query runs against a real (H2) database, and only admins get in. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminDashboardTest {

    @Autowired AdminDashboardService dashboard;
    @Autowired ModerationService moderation;
    @Autowired UserRepository users;
    @Autowired JwtUtil jwt;
    @Autowired MockMvc mvc;
    @MockitoBean PushNotificationService push;
    @MockitoBean EmailService email;

    User user(String name, Role role) {
        User u = new User();
        u.setEmail(name.toLowerCase() + "@test.ch");
        u.setName(name);
        u.setPassword("x");
        u.setRole(role);
        u.setEmailVerified(true);
        u.setDateOfBirth(LocalDate.of(1990, 1, 1));
        return users.save(u);
    }

    @Test
    void everyQueryRunsAndAReportIsKept() {
        User anna = user("Anna", Role.USER);
        User ben = user("Ben", Role.USER);
        moderation.reportProfile(anna.getEmail(), ben.getId(), new ReportRequest("Fake profile", "stock photo"));

        Map<String, Object> overview = dashboard.overview();
        assertThat(((Map<?, ?>) overview.get("users")).get("total")).isEqualTo(2L);
        assertThat(((Map<?, ?>) overview.get("content")).get("openReports")).isEqualTo(1L);
        assertThat((java.util.List<?>) dashboard.timeseries(30).get("signups")).hasSize(30);
        assertThat(dashboard.funnel()).isNotEmpty();
        assertThat(dashboard.online()).containsKeys("online", "recent");
        assertThat(dashboard.searchUsers("ben")).hasSize(1);
        assertThat(dashboard.userDetail(ben.getId())).containsKeys("dogs", "counts", "reports");
        assertThat(dashboard.server(24)).containsKeys("jvm", "railway");

        Report report = dashboard.reports("OPEN").get(0);
        assertThat(report.getReportedName()).isEqualTo("Ben");
        assertThat(report.getKind()).isEqualTo("PROFILE");
        Report handled = dashboard.updateReport(report.getId(), "RESOLVED", "Removed photo", "admin@test.ch");
        assertThat(handled.getHandledAt()).isNotNull();
        assertThat(dashboard.reports("OPEN")).isEmpty();
    }

    @Test
    void onlyAdminsGetTheData() throws Exception {
        User admin = user("Admin", Role.ADMIN);
        User plain = user("Plain", Role.USER);

        mvc.perform(get("/admin/api/overview")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/api/overview").header("Authorization", "Bearer " + jwt.generateToken(plain.getEmail())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/api/overview").header("Authorization", "Bearer " + jwt.generateToken(admin.getEmail())))
                .andExpect(status().isOk());
        // The page itself is public; it asks for the login.
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    }
}

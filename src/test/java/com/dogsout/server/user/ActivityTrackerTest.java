package com.dogsout.server.user;

import com.dogsout.server.notification.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ActivityTrackerTest {

    @Autowired ActivityTracker tracker;
    @Autowired UserRepository users;
    @Autowired UserActivityDayRepository days;
    @MockitoBean PushNotificationService push;

    @Test
    void recognisesPlatformFromHeaderOrHttpClient() {
        assertThat(ActivityTracker.platform("ios", null)).isEqualTo("IOS");
        assertThat(ActivityTracker.platform("Android", null)).isEqualTo("ANDROID");
        assertThat(ActivityTracker.platform(null, "DogsOut/25 CFNetwork/3826.500 Darwin/24.4.0")).isEqualTo("IOS");
        assertThat(ActivityTracker.platform(null, "okhttp/4.12.0")).isEqualTo("ANDROID");
        assertThat(ActivityTracker.platform(null, "curl/8.0")).isNull();
        assertThat(ActivityTracker.version(null, "DogsOut/25 CFNetwork/3826.500 Darwin/24.4.0")).isEqualTo("build 25");
        assertThat(ActivityTracker.version("1.3.0 (26)", "okhttp/4.12.0")).isEqualTo("1.3.0 (26)");
    }

    @Test
    void recordsLastActiveAndOneRowPerDay() {
        User u = new User();
        u.setEmail("active@test");
        u.setName("Active");
        u.setPassword("x");
        u.setDateOfBirth(LocalDate.of(1990, 1, 1));
        u = users.save(u);

        tracker.touch(u.getEmail(), null, null, "okhttp/4.12.0");
        tracker.touch(u.getEmail(), null, null, "okhttp/4.12.0");

        User saved = users.findById(u.getId()).orElseThrow();
        assertThat(saved.getLastActiveAt()).isNotNull();
        assertThat(saved.getLastPlatform()).isEqualTo("ANDROID");
        assertThat(days.findAll().stream().filter(d -> d.getUserId().equals(saved.getId()))).hasSize(1);
    }
}

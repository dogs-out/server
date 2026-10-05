package com.dogsout.server.admin;

import com.dogsout.server.moderation.Report;
import com.dogsout.server.moderation.ReportRepository;
import com.dogsout.server.photo.PhotoRendition;
import com.dogsout.server.photo.PhotoService;
import com.dogsout.server.websocket.ChatSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.management.ManagementFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Numbers for the admin page.
 *
 * <p>Plain SQL over the existing tables: the questions here ("how many signed up
 * each day") are aggregates no entity was designed to answer, and the data is
 * small enough that a few queries per page load cost nothing.
 *
 * <p>Seed and demo accounts (@dogsout.dev, @dogsout.app) are left out of every
 * user number, so the page shows real people only. Days are Swiss calendar days.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    /** SQL condition for "a real user", on a users table aliased u. */
    static final String REAL = "u.email NOT LIKE '%@dogsout.dev' AND u.email NOT LIKE '%@dogsout.app'";

    private final JdbcTemplate jdbc;
    private final ChatSocketHandler sockets;
    private final ReportRepository reportRepository;
    private final PhotoService photoService;
    private final RailwayMetrics railwayMetrics;

    // ─── Overview ─────────────────────────────────────────────────────────────

    public Map<String, Object> overview() {
        Instant now = Instant.now();
        Map<String, Object> out = new LinkedHashMap<>();

        Map<String, Object> users = new LinkedHashMap<>();
        users.put("total", count("SELECT COUNT(*) FROM users u WHERE " + REAL));
        users.put("verified", count("SELECT COUNT(*) FROM users u WHERE u.email_verified = TRUE AND " + REAL));
        users.put("newToday", count("SELECT COUNT(*) FROM users u WHERE u.created_at >= ? AND " + REAL, startOfDayLocal(0)));
        users.put("newYesterday", count("SELECT COUNT(*) FROM users u WHERE u.created_at >= ? AND u.created_at < ? AND " + REAL,
                startOfDayLocal(1), startOfDayLocal(0)));
        users.put("new7d", count("SELECT COUNT(*) FROM users u WHERE u.created_at >= ? AND " + REAL, startOfDayLocal(6)));
        users.put("withDog", count("SELECT COUNT(DISTINCT u.id) FROM users u JOIN dogs d ON d.owner_id = u.id WHERE " + REAL));
        users.put("sitters", count("SELECT COUNT(*) FROM users u WHERE u.is_sitter = TRUE AND " + REAL));
        users.put("lookingForSitter", count("SELECT COUNT(*) FROM users u WHERE u.looking_for_sitter = TRUE AND " + REAL));
        users.put("noLocation", count("SELECT COUNT(*) FROM users u WHERE u.latitude IS NULL AND " + REAL));
        out.put("users", users);

        Set<Long> onlineIds = realIds(sockets.onlineUserIds());
        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("onlineNow", onlineIds.size());
        activity.put("active15m", activeSince(now.minusSeconds(15 * 60)));
        activity.put("active24h", activeSince(now.minusSeconds(24 * 3600)));
        activity.put("active7d", activeSince(now.minusSeconds(7 * 24 * 3600)));
        activity.put("active30d", activeSince(now.minusSeconds(30L * 24 * 3600)));
        out.put("activity", activity);

        out.put("platforms", rows("""
                SELECT COALESCE(u.last_platform, 'UNKNOWN') AS platform, COUNT(*) AS users
                FROM users u WHERE %s GROUP BY COALESCE(u.last_platform, 'UNKNOWN') ORDER BY 2 DESC""".formatted(REAL)));
        out.put("versions", rows("""
                SELECT u.last_platform AS platform, u.last_app_version AS version, COUNT(*) AS users
                FROM users u WHERE u.last_app_version IS NOT NULL AND %s
                GROUP BY u.last_platform, u.last_app_version ORDER BY 3 DESC""".formatted(REAL)));

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("dogs", count("SELECT COUNT(*) FROM dogs d JOIN users u ON u.id = d.owner_id WHERE " + REAL));
        content.put("dogsWithoutPhoto", count("""
                SELECT COUNT(*) FROM dogs d JOIN users u ON u.id = d.owner_id
                WHERE NOT EXISTS (SELECT 1 FROM dog_photos p WHERE p.dog_id = d.id) AND """ + " " + REAL));
        content.put("swipes", count("SELECT COUNT(*) FROM matches m JOIN users u ON u.id = m.user1_id WHERE " + REAL));
        content.put("matches", count("SELECT COUNT(*) FROM matches m JOIN users u ON u.id = m.user1_id WHERE m.status = 'MATCHED' AND " + REAL));
        content.put("messages", count("SELECT COUNT(*) FROM messages x JOIN users u ON u.id = x.sender_id WHERE " + REAL));
        content.put("upcomingPlaydates", count("SELECT COUNT(*) FROM playdates p WHERE p.status = 'ACTIVE' AND p.starts_at > ?", Timestamp.from(now)));
        content.put("openSittingJobs", count("SELECT COUNT(*) FROM sitting_requests r WHERE r.status = 'OPEN' AND r.ends_at > ?", Timestamp.from(now)));
        content.put("openSosAlerts", count("SELECT COUNT(*) FROM lost_dog_alerts a WHERE a.closed_at IS NULL"));
        content.put("openReports", reportRepository.countByStatus(Report.OPEN));
        out.put("content", content);
        return out;
    }

    // ─── Time series ──────────────────────────────────────────────────────────

    /** Per Swiss calendar day for the last {@code days} days, oldest first. */
    public Map<String, Object> timeseries(int days) {
        days = Math.max(7, Math.min(days, 180));
        LocalDate today = LocalDate.now(ZURICH);
        LocalDate first = today.minusDays(days - 1L);
        Instant since = first.atStartOfDay(ZURICH).toInstant();

        Map<String, Object> out = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < days; i++) labels.add(first.plusDays(i).toString());
        out.put("days", labels);

        out.put("signups", bucket(labels, timestamps(
                "SELECT u.created_at FROM users u WHERE u.created_at >= ? AND " + REAL, startOfDayLocal(days - 1))));
        out.put("swipes", bucket(labels, timestamps(
                "SELECT m.created_at FROM matches m JOIN users u ON u.id = m.user1_id WHERE m.created_at >= ? AND " + REAL,
                Timestamp.from(since))));
        out.put("matches", bucket(labels, timestamps(
                "SELECT m.created_at FROM matches m JOIN users u ON u.id = m.user1_id WHERE m.status = 'MATCHED' AND m.created_at >= ? AND " + REAL,
                Timestamp.from(since))));
        out.put("messages", bucket(labels, timestamps(
                "SELECT x.sent_at FROM messages x JOIN users u ON u.id = x.sender_id WHERE x.sent_at >= ? AND " + REAL,
                Timestamp.from(since))));

        // Daily actives come from the activity table, which is already per Swiss day.
        Map<String, long[]> active = new TreeMap<>();
        for (String d : labels) active.put(d, new long[3]);
        jdbc.query("""
                SELECT a.activity_day, a.platform FROM user_activity_days a JOIN users u ON u.id = a.user_id
                WHERE a.activity_day >= ? AND %s""".formatted(REAL), rs -> {
            long[] slot = active.get(rs.getDate(1).toLocalDate().toString());
            if (slot == null) return;
            slot[0]++;
            String p = rs.getString(2);
            if ("IOS".equals(p)) slot[1]++; else if ("ANDROID".equals(p)) slot[2]++;
        }, java.sql.Date.valueOf(first));
        out.put("activeUsers", active.values().stream().map(v -> v[0]).toList());
        out.put("activeIos", active.values().stream().map(v -> v[1]).toList());
        out.put("activeAndroid", active.values().stream().map(v -> v[2]).toList());
        return out;
    }

    // ─── Funnel ───────────────────────────────────────────────────────────────

    /** How far real users get, each step a subset of the one before in practice. */
    public List<Map<String, Object>> funnel() {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("registered", "SELECT COUNT(*) FROM users u WHERE " + REAL));
        steps.add(step("emailConfirmed", "SELECT COUNT(*) FROM users u WHERE u.email_verified = TRUE AND " + REAL));
        steps.add(step("locationSet", "SELECT COUNT(*) FROM users u WHERE u.latitude IS NOT NULL AND " + REAL));
        steps.add(step("profilePhoto", "SELECT COUNT(*) FROM users u WHERE u.profile_picture_key IS NOT NULL AND " + REAL));
        steps.add(step("dogAdded", "SELECT COUNT(DISTINCT u.id) FROM users u JOIN dogs d ON d.owner_id = u.id WHERE " + REAL));
        steps.add(step("swiped", "SELECT COUNT(DISTINCT u.id) FROM users u JOIN matches m ON m.user1_id = u.id WHERE " + REAL));
        steps.add(step("matched", """
                SELECT COUNT(DISTINCT u.id) FROM users u JOIN matches m ON (m.user1_id = u.id OR m.user2_id = u.id)
                WHERE m.status = 'MATCHED' AND """ + " " + REAL));
        steps.add(step("messaged", "SELECT COUNT(DISTINCT u.id) FROM users u JOIN messages x ON x.sender_id = u.id WHERE " + REAL));
        return steps;
    }

    // ─── People ───────────────────────────────────────────────────────────────

    /** Who has the app open now, then who used it in the last 15 minutes. */
    public Map<String, Object> online() {
        Set<Long> ids = sockets.onlineUserIds();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("online", ids.isEmpty() ? List.of() : rows("""
                SELECT u.id, u.name, u.last_platform AS platform, u.last_app_version AS version, u.last_active_at AS last_active
                FROM users u WHERE u.id IN (%s) AND %s ORDER BY u.name""".formatted(idList(ids), REAL)));
        out.put("recent", rows("""
                SELECT u.id, u.name, u.last_platform AS platform, u.last_app_version AS version, u.last_active_at AS last_active
                FROM users u WHERE u.last_active_at >= ? AND %s ORDER BY u.last_active_at DESC""".formatted(REAL),
                Timestamp.from(Instant.now().minusSeconds(15 * 60))));
        return out;
    }

    /** Search by id, name or email; newest first. Includes seed accounts, marked. */
    public List<Map<String, Object>> searchUsers(String q) {
        String sql = """
                SELECT u.id, u.name, u.email, u.created_at, u.last_active_at AS last_active, u.last_platform AS platform,
                       u.role, u.email_verified AS verified,
                       (SELECT COUNT(*) FROM reports r WHERE r.reported_id = u.id) AS reports,
                       CASE WHEN %s THEN FALSE ELSE TRUE END AS seed
                FROM users u %s ORDER BY u.created_at DESC LIMIT 100""";
        if (q == null || q.isBlank()) return rows(sql.formatted(REAL, ""));
        String like = "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        Long id = parseId(q.trim());
        return rows(sql.formatted(REAL, "WHERE LOWER(u.name) LIKE ? OR LOWER(u.email) LIKE ? OR u.id = ?"),
                like, like, id == null ? -1L : id);
    }

    public Map<String, Object> userDetail(Long id) {
        List<Map<String, Object>> found = rows("""
                SELECT u.id, u.name, u.email, u.bio, u.date_of_birth, u.created_at, u.last_active_at AS last_active,
                       u.last_platform AS platform, u.last_app_version AS version, u.role, u.auth_provider,
                       u.email_verified AS verified, u.has_dog, u.is_sitter, u.looking_for_sitter,
                       u.latitude IS NOT NULL AS has_location, u.profile_picture_key
                FROM users u WHERE u.id = ?""", id);
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        Map<String, Object> user = new LinkedHashMap<>(found.get(0));
        user.put("photos", jdbc.queryForList("SELECT p.storage_key FROM user_photos p WHERE p.user_id = ? ORDER BY p.sort_order", String.class, id)
                .stream().map(k -> photoService.url(k, PhotoRendition.FEED)).toList());
        user.remove("profile_picture_key");

        List<Map<String, Object>> dogs = new ArrayList<>();
        for (Map<String, Object> d : rows("SELECT d.id, d.name, d.breed, d.bio FROM dogs d WHERE d.owner_id = ? ORDER BY d.id", id)) {
            Map<String, Object> dog = new LinkedHashMap<>(d);
            dog.put("photos", jdbc.queryForList("SELECT p.storage_key FROM dog_photos p WHERE p.dog_id = ? ORDER BY p.sort_order",
                    String.class, d.get("id")).stream().map(k -> photoService.url(k, PhotoRendition.THUMB)).toList());
            dogs.add(dog);
        }
        user.put("dogs", dogs);

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("swipes", count("SELECT COUNT(*) FROM matches m WHERE m.user1_id = ?", id));
        counts.put("matches", count("SELECT COUNT(*) FROM matches m WHERE m.status = 'MATCHED' AND (m.user1_id = ? OR m.user2_id = ?)", id, id));
        counts.put("messagesSent", count("SELECT COUNT(*) FROM messages x WHERE x.sender_id = ?", id));
        counts.put("blockedBy", count("SELECT COUNT(*) FROM blocks b WHERE b.blocked_id = ?", id));
        counts.put("reportsAgainst", reportRepository.countByReportedId(id));
        user.put("counts", counts);
        user.put("reports", reportRepository.findByReportedIdOrderByCreatedAtDesc(id));
        return user;
    }

    // ─── Reports ──────────────────────────────────────────────────────────────

    public List<Report> reports(String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            return reportRepository.findTop200ByOrderByCreatedAtDesc();
        }
        return reportRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase(java.util.Locale.ROOT));
    }

    @Transactional
    public Report updateReport(Long id, String status, String note, String adminEmail) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        String next = status == null ? report.getStatus() : status.toUpperCase(java.util.Locale.ROOT);
        if (!Set.of(Report.OPEN, Report.RESOLVED, Report.DISMISSED).contains(next)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status");
        }
        report.setStatus(next);
        if (note != null) report.setAdminNote(note.isBlank() ? null : note.trim());
        if (Report.OPEN.equals(next)) {
            report.setHandledAt(null);
            report.setHandledBy(null);
        } else {
            report.setHandledAt(Instant.now());
            report.setHandledBy(adminEmail);
        }
        return reportRepository.save(report);
    }

    // ─── Server ───────────────────────────────────────────────────────────────

    public Map<String, Object> server(int hours) {
        Map<String, Object> out = new LinkedHashMap<>();
        Runtime rt = Runtime.getRuntime();
        var os = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMb", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        jvm.put("heapMaxMb", rt.maxMemory() / (1024 * 1024));
        jvm.put("cpus", rt.availableProcessors());
        jvm.put("loadAverage", os.getSystemLoadAverage());
        jvm.put("threads", ManagementFactory.getThreadMXBean().getThreadCount());
        jvm.put("uptimeMinutes", ManagementFactory.getRuntimeMXBean().getUptime() / 60000);
        jvm.put("socketConnections", sockets.onlineUserIds().size());
        out.put("jvm", jvm);
        try {
            out.put("databaseSize", jdbc.queryForObject("SELECT pg_size_pretty(pg_database_size(current_database()))", String.class));
        } catch (Exception notPostgres) {
            out.put("databaseSize", null);
        }
        out.put("railway", railwayMetrics.fetch(Math.max(1, Math.min(hours, 168))));
        return out;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> step(String key, String sql) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("step", key);
        m.put("users", count(sql));
        return m;
    }

    private long activeSince(Instant since) {
        return count("SELECT COUNT(*) FROM users u WHERE u.last_active_at >= ? AND " + REAL, Timestamp.from(since));
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    /**
     * users.created_at is a local date-time written in the server's zone, so the
     * Swiss midnight it is compared with is converted into that zone first.
     */
    private static Timestamp startOfDayLocal(int daysAgo) {
        Instant midnight = LocalDate.now(ZURICH).minusDays(daysAgo).atStartOfDay(ZURICH).toInstant();
        return Timestamp.valueOf(LocalDateTime.ofInstant(midnight, ZoneId.systemDefault()));
    }

    private List<Instant> timestamps(String sql, Object arg) {
        return jdbc.query(sql, (rs, i) -> rs.getTimestamp(1).toInstant(), arg);
    }

    /**
     * Counts per Swiss day. Plain date-time columns come back from the driver read
     * in the JVM zone, which is the zone they were written in, so every column type
     * arrives here as the right instant.
     */
    private static List<Long> bucket(List<String> days, List<Instant> times) {
        Map<String, Long> counts = new TreeMap<>();
        for (String d : days) counts.put(d, 0L);
        for (Instant t : times) {
            String day = t.atZone(ZURICH).toLocalDate().toString();
            counts.computeIfPresent(day, (k, v) -> v + 1);
        }
        return new ArrayList<>(counts.values());
    }

    private Set<Long> realIds(Set<Long> ids) {
        if (ids.isEmpty()) return Set.of();
        return Set.copyOf(jdbc.queryForList("SELECT u.id FROM users u WHERE u.id IN (%s) AND %s".formatted(idList(ids), REAL), Long.class));
    }

    private static String idList(Set<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }

    private static Long parseId(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }
}

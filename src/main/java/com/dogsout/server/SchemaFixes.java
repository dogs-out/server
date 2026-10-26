package com.dogsout.server;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Column changes that ddl-auto=update cannot make: it adds columns but never
 * widens one. Each fix checks first, so it runs on every start and does nothing
 * once applied. PostgreSQL only — the test database is created fresh from the
 * entities and is already right.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaFixes implements ApplicationRunner {

    /**
     * Chat text was varchar(255) while the API accepted 2000 characters, so any
     * longer message failed with a database error shown to the user.
     */
    static final List<String[]> TO_TEXT = List.of(
            new String[]{"messages", "content"},
            new String[]{"playdate_messages", "content"});

    private final JdbcTemplate jdbc;

    /**
     * A unique constraint that no longer fits. Event sign-ups were one per person and
     * event; with sign-up per date, one person has a row for each date they picked.
     */
    private void dropUnique(String table, String columns) {
        List<String> names = jdbc.queryForList("""
                select tc.constraint_name
                  from information_schema.table_constraints tc
                  join information_schema.key_column_usage k on k.constraint_name = tc.constraint_name
                 where tc.table_schema = 'public' and tc.table_name = ? and tc.constraint_type = 'UNIQUE'
                 group by tc.constraint_name
                having string_agg(k.column_name, ',' order by k.ordinal_position) = ?""", String.class, table, columns);
        for (String name : names) {
            jdbc.execute("alter table " + table + " drop constraint " + name);
            log.info("Dropped unique {}({}) on {}", name, columns, table);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String product = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
            if (!"PostgreSQL".equalsIgnoreCase(product)) return;
            for (String[] tc : TO_TEXT) {
                List<Integer> limit = jdbc.queryForList("""
                        select character_maximum_length from information_schema.columns
                         where table_schema = 'public' and table_name = ? and column_name = ?""",
                        Integer.class, tc[0], tc[1]);
                if (!limit.isEmpty() && limit.get(0) != null) {
                    jdbc.execute("alter table " + tc[0] + " alter column " + tc[1] + " type text");
                    log.info("Widened {}.{} from varchar({}) to text", tc[0], tc[1], limit.get(0));
                }
            }
            dropUnique("school_event_participants", "event_id,user_id");
        } catch (RuntimeException e) {
            // A failed fix must not keep the server from starting; the old limit stays.
            log.warn("Schema fix skipped: {}", e.getMessage());
        }
    }
}

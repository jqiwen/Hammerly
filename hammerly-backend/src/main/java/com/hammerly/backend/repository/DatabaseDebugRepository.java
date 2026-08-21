package com.hammerly.backend.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseDebugRepository {
    private static final Set<String> APPLICATION_TABLES =
        Set.of("users", "auctions", "bids", "watchlist", "payment_methods");
    private final JdbcTemplate jdbc;

    public DatabaseDebugRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> describeDatabase() {
        List<String> tables = jdbc.query("""
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'hammerly' AND table_type = 'BASE TABLE'
            ORDER BY table_name
            """, (rs, row) -> rs.getString("table_name")).stream()
            .filter(APPLICATION_TABLES::contains)
            .toList();
        List<Map<String, Object>> tableInfo = new ArrayList<>();
        for (String table : tables) {
            List<Map<String, Object>> columns = jdbc.query("""
                SELECT c.column_name, c.data_type, c.is_nullable,
                       EXISTS (
                           SELECT 1
                           FROM information_schema.table_constraints tc
                           JOIN information_schema.key_column_usage kcu
                             ON kcu.constraint_schema = tc.constraint_schema
                            AND kcu.constraint_name = tc.constraint_name
                           WHERE tc.table_schema = c.table_schema
                             AND tc.table_name = c.table_name
                             AND tc.constraint_type = 'PRIMARY KEY'
                             AND kcu.column_name = c.column_name
                       ) AS is_primary_key
                FROM information_schema.columns c
                WHERE c.table_schema = 'hammerly' AND c.table_name = ?
                ORDER BY c.ordinal_position
                """, (rs, row) -> {
                    Map<String, Object> mapped = new LinkedHashMap<>();
                    mapped.put("name", rs.getString("column_name"));
                    mapped.put("type", rs.getString("data_type"));
                    mapped.put("notnull", "NO".equals(rs.getString("is_nullable")) ? 1 : 0);
                    mapped.put("primaryKey", rs.getBoolean("is_primary_key") ? 1 : 0);
                    return mapped;
                }, table);
            List<Map<String, Object>> rows = jdbc.query("SELECT * FROM hammerly." + table +
                    " ORDER BY id DESC LIMIT 10",
                new ColumnMapRowMapper());
            Collections.reverse(rows);
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM hammerly." + table, Long.class);

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", table);
            info.put("rowCount", count == null ? 0 : count);
            info.put("columns", columns);
            info.put("lastRows", rows);
            tableInfo.add(info);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("tables", tableInfo);
        result.put("totalTables", tables.size());
        return result;
    }
}

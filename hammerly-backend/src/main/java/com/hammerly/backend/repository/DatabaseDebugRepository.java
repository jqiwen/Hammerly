package com.hammerly.backend.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseDebugRepository {
    private final JdbcTemplate jdbc;

    public DatabaseDebugRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> describeDatabase() {
        List<String> tables = jdbc.query("SELECT name FROM sqlite_master WHERE type='table' " +
            "AND name NOT LIKE 'sqlite_%'", (rs, row) -> rs.getString("name"));
        List<Map<String, Object>> tableInfo = new ArrayList<>();
        for (String table : tables) {
            List<Map<String, Object>> schema = jdbc.query("PRAGMA table_info(" + table + ")", new ColumnMapRowMapper());
            List<Map<String, Object>> rows = jdbc.query("SELECT * FROM " + table + " ORDER BY id DESC LIMIT 10",
                new ColumnMapRowMapper());
            Collections.reverse(rows);
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            List<Map<String, Object>> columns = schema.stream().map(column -> {
                Map<String, Object> mapped = new LinkedHashMap<>();
                mapped.put("name", column.get("name"));
                mapped.put("type", column.get("type"));
                mapped.put("notnull", column.get("notnull"));
                mapped.put("primaryKey", column.get("pk"));
                return mapped;
            }).toList();

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

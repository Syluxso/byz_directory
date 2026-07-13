package com.nyberg.directory.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/db")
public class DbViewerController {

    private static final Logger log = LoggerFactory.getLogger(DbViewerController.class);
    private static final String SCHEMA = "directory";

    private final JdbcTemplate jdbc;

    public DbViewerController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            String currentSchema = jdbc.queryForObject("SELECT current_schema()", String.class);
            Long tableCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = ? AND table_type = 'BASE TABLE'",
                    Long.class, SCHEMA);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("select1", one);
            out.put("currentSchema", currentSchema);
            out.put("schema", SCHEMA);
            out.put("tableCount", tableCount);
            return out;
        } catch (Exception ex) {
            log.error("admin db ping failed: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "db ping failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    @GetMapping("/tables")
    public List<String> tables() {
        try {
            // Cast to text — information_schema.sql_identifier can break String mapping on some JDBC drivers.
            return jdbc.query(
                    "SELECT table_name::text FROM information_schema.tables " +
                    "WHERE table_schema = ? AND table_type = 'BASE TABLE' " +
                    "ORDER BY 1",
                    (rs, rowNum) -> rs.getString(1),
                    SCHEMA);
        } catch (Exception ex) {
            log.error("admin db tables failed: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "tables failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    @GetMapping("/tables/{table}")
    public Map<String, Object> tableData(
            @PathVariable String table,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        try {
            List<String> valid = tables();
            if (!valid.contains(table)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown table");
            }

            // Only allow simple identifiers (already validated against information_schema).
            if (!table.matches("[a-zA-Z0-9_]+")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid table name");
            }

            String quoted = "\"" + SCHEMA + "\".\"" + table + "\"";
            int offset = Math.max(0, page) * Math.max(1, size);
            int limit = Math.min(Math.max(1, size), 500);

            List<String> columns = jdbc.query(
                    "SELECT column_name::text FROM information_schema.columns " +
                    "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position",
                    (rs, rowNum) -> rs.getString(1),
                    SCHEMA, table);

            List<Map<String, Object>> rawRows = jdbc.queryForList(
                    "SELECT * FROM " + quoted + " ORDER BY 1 LIMIT ? OFFSET ?", limit, offset);

            List<List<String>> rows = new ArrayList<>();
            for (Map<String, Object> row : rawRows) {
                List<String> cells = new ArrayList<>();
                for (String col : columns) {
                    Object val = row.get(col);
                    if (val == null) {
                        // PG may return lowercase keys; try as-is then lower.
                        val = row.get(col.toLowerCase());
                    }
                    cells.add(val != null ? val.toString() : null);
                }
                rows.add(cells);
            }

            Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + quoted, Long.class);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("columns", columns);
            result.put("rows", rows);
            result.put("total", total != null ? total : 0L);
            result.put("page", page);
            result.put("size", size);
            return result;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("admin db tableData({}) failed: {}", table, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "tableData failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
}

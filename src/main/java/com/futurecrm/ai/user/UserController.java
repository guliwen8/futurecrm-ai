package com.futurecrm.ai.user;

import com.futurecrm.ai.common.ApiResponse;
import com.futurecrm.ai.common.PasswordUtil;
import com.futurecrm.ai.common.SqlMaps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final JdbcTemplate jdbcTemplate;

    public UserController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(jdbcTemplate.queryForList("""
                SELECT id, username, real_name, email, phone, role, status, created_at, updated_at
                FROM users ORDER BY id ASC
                """));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(jdbcTemplate.queryForMap("""
                SELECT id, username, real_name, email, phone, role, status, created_at, updated_at
                FROM users WHERE id = ?
                """, id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> data = SqlMaps.mutable(body);
        String username = required(data, "username");
        String realName = required(data, "real_name");
        String password = defaultText(data, "password", "123456");

        // Check duplicate username
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO users(username, password_hash, real_name, email, phone, role, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, PasswordUtil.sha256(password));
            ps.setString(3, realName);
            ps.setString(4, SqlMaps.text(data, "email"));
            ps.setString(5, SqlMaps.text(data, "phone"));
            ps.setString(6, defaultText(data, "role", "SALES"));
            ps.setString(7, defaultText(data, "status", "ACTIVE"));
            return ps;
        }, keyHolder);
        return detail(keyHolder.getKey().longValue());
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> data = SqlMaps.mutable(body);
        jdbcTemplate.update("""
                UPDATE users
                SET real_name = ?, email = ?, phone = ?, role = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                required(data, "real_name"),
                SqlMaps.text(data, "email"),
                SqlMaps.text(data, "phone"),
                defaultText(data, "role", "SALES"),
                id);
        return detail(id);
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String newPassword = defaultText(SqlMaps.mutable(body), "password", "123456");
        jdbcTemplate.update("UPDATE users SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                PasswordUtil.sha256(newPassword), id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> toggleStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String newStatus = required(SqlMaps.mutable(body), "status");
        jdbcTemplate.update("UPDATE users SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                newStatus, id);
        return ApiResponse.ok(null);
    }

    private String required(Map<String, Object> data, String key) {
        String value = SqlMaps.text(data, key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + "不能为空");
        return value;
    }

    private String defaultText(Map<String, Object> data, String key, String fallback) {
        String value = SqlMaps.text(data, key);
        return value == null || value.isBlank() ? fallback : value;
    }
}

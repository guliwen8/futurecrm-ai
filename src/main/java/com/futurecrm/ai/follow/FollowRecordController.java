package com.futurecrm.ai.follow;

import com.futurecrm.ai.common.ApiResponse;
import com.futurecrm.ai.common.CurrentUser;
import com.futurecrm.ai.common.SqlMaps;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FollowRecordController {
    private final JdbcTemplate jdbcTemplate;

    public FollowRecordController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/customers/{customerId}/follow-records")
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable Long customerId) {
        return ApiResponse.ok(jdbcTemplate.queryForList("""
                SELECT f.*, u.real_name AS user_name, c.name AS contact_name
                FROM follow_records f
                LEFT JOIN users u ON u.id = f.user_id
                LEFT JOIN contacts c ON c.id = f.contact_id
                WHERE f.customer_id = ?
                ORDER BY f.follow_time DESC, f.id DESC
                """, customerId));
    }

    @PostMapping("/customers/{customerId}/follow-records")
    public ApiResponse<Map<String, Object>> create(@PathVariable Long customerId,
                                                   @RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        CurrentUser currentUser = (CurrentUser) request.getAttribute("currentUser");
        Map<String, Object> data = SqlMaps.mutable(body);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO follow_records(customer_id, contact_id, user_id, follow_type, follow_time, content,
                    customer_feedback, next_action, next_follow_time, ai_summary)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, customerId);
            ps.setObject(2, SqlMaps.longValue(data, "contact_id"));
            ps.setLong(3, currentUser.id());
            ps.setString(4, defaultText(data, "follow_type", "PHONE"));
            ps.setString(5, defaultText(data, "follow_time", LocalDateTime.now().toString()));
            ps.setString(6, required(data, "content"));
            ps.setString(7, SqlMaps.text(data, "customer_feedback"));
            ps.setString(8, SqlMaps.text(data, "next_action"));
            ps.setString(9, SqlMaps.text(data, "next_follow_time"));
            ps.setString(10, SqlMaps.text(data, "ai_summary"));
            return ps;
        }, keyHolder);
        Long nextId = keyHolder.getKey().longValue();
        String nextFollowTime = SqlMaps.text(data, "next_follow_time");
        if (nextFollowTime != null && !nextFollowTime.isBlank()) {
            jdbcTemplate.update("UPDATE customers SET next_follow_time = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", nextFollowTime, customerId);
        }
        return detail(nextId);
    }

    @PutMapping("/follow-records/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> data = SqlMaps.mutable(body);
        jdbcTemplate.update("""
                UPDATE follow_records
                SET contact_id = ?, follow_type = ?, follow_time = ?, content = ?, customer_feedback = ?,
                    next_action = ?, next_follow_time = ?, ai_summary = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                SqlMaps.longValue(data, "contact_id"),
                defaultText(data, "follow_type", "PHONE"),
                defaultText(data, "follow_time", LocalDateTime.now().toString()),
                required(data, "content"),
                SqlMaps.text(data, "customer_feedback"),
                SqlMaps.text(data, "next_action"),
                SqlMaps.text(data, "next_follow_time"),
                SqlMaps.text(data, "ai_summary"),
                id);
        return detail(id);
    }

    @DeleteMapping("/follow-records/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jdbcTemplate.update("DELETE FROM follow_records WHERE id = ?", id);
        return ApiResponse.ok(null);
    }

    private ApiResponse<Map<String, Object>> detail(Long id) {
        return ApiResponse.ok(jdbcTemplate.queryForMap("""
                SELECT f.*, u.real_name AS user_name, c.name AS contact_name
                FROM follow_records f
                LEFT JOIN users u ON u.id = f.user_id
                LEFT JOIN contacts c ON c.id = f.contact_id
                WHERE f.id = ?
                """, id));
    }

    private String required(Map<String, Object> data, String key) {
        String value = SqlMaps.text(data, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + "不能为空");
        }
        return value;
    }

    private String defaultText(Map<String, Object> data, String key, String fallback) {
        String value = SqlMaps.text(data, key);
        return value == null || value.isBlank() ? fallback : value;
    }
}

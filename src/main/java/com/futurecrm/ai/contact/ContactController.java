package com.futurecrm.ai.contact;

import com.futurecrm.ai.common.ApiResponse;
import com.futurecrm.ai.common.SqlMaps;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ContactController {
    private final JdbcTemplate jdbcTemplate;

    public ContactController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/customers/{customerId}/contacts")
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable Long customerId) {
        return ApiResponse.ok(jdbcTemplate.queryForList(
                "SELECT * FROM contacts WHERE customer_id = ? ORDER BY is_decision_maker DESC, id DESC", customerId));
    }

    @PostMapping("/customers/{customerId}/contacts")
    public ApiResponse<Map<String, Object>> create(@PathVariable Long customerId, @RequestBody Map<String, Object> body) {
        Map<String, Object> data = SqlMaps.mutable(body);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO contacts(customer_id, name, gender, position, mobile, phone, email, wechat,
                    is_decision_maker, hobby, taboo, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            bind(ps, customerId, data);
            return ps;
        }, keyHolder);
        return detail(keyHolder.getKey().longValue());
    }

    @PutMapping("/contacts/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> data = SqlMaps.mutable(body);
        jdbcTemplate.update("""
                UPDATE contacts
                SET name = ?, gender = ?, position = ?, mobile = ?, phone = ?, email = ?, wechat = ?,
                    is_decision_maker = ?, hobby = ?, taboo = ?, remark = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                required(data, "name"),
                SqlMaps.text(data, "gender"),
                SqlMaps.text(data, "position"),
                SqlMaps.text(data, "mobile"),
                SqlMaps.text(data, "phone"),
                SqlMaps.text(data, "email"),
                SqlMaps.text(data, "wechat"),
                SqlMaps.intValue(data, "is_decision_maker") == null ? 0 : SqlMaps.intValue(data, "is_decision_maker"),
                SqlMaps.text(data, "hobby"),
                SqlMaps.text(data, "taboo"),
                SqlMaps.text(data, "remark"),
                id);
        return detail(id);
    }

    @DeleteMapping("/contacts/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jdbcTemplate.update("DELETE FROM contacts WHERE id = ?", id);
        return ApiResponse.ok(null);
    }

    private ApiResponse<Map<String, Object>> detail(Long id) {
        return ApiResponse.ok(jdbcTemplate.queryForMap("SELECT * FROM contacts WHERE id = ?", id));
    }

    private void bind(PreparedStatement ps, Long customerId, Map<String, Object> data) throws java.sql.SQLException {
        ps.setLong(1, customerId);
        ps.setString(2, required(data, "name"));
        ps.setString(3, SqlMaps.text(data, "gender"));
        ps.setString(4, SqlMaps.text(data, "position"));
        ps.setString(5, SqlMaps.text(data, "mobile"));
        ps.setString(6, SqlMaps.text(data, "phone"));
        ps.setString(7, SqlMaps.text(data, "email"));
        ps.setString(8, SqlMaps.text(data, "wechat"));
        Integer decisionMaker = SqlMaps.intValue(data, "is_decision_maker");
        ps.setInt(9, decisionMaker == null ? 0 : decisionMaker);
        ps.setString(10, SqlMaps.text(data, "hobby"));
        ps.setString(11, SqlMaps.text(data, "taboo"));
        ps.setString(12, SqlMaps.text(data, "remark"));
    }

    private String required(Map<String, Object> data, String key) {
        String value = SqlMaps.text(data, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + "不能为空");
        }
        return value;
    }
}

package com.futurecrm.ai.customer;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final JdbcTemplate jdbcTemplate;

    public CustomerController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.*, u.real_name AS owner_name
                FROM customers c
                LEFT JOIN users u ON u.id = c.owner_user_id
                WHERE 1 = 1
                """);
        new Object() {
            final java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        };
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (c.name LIKE ? OR c.phone LIKE ? OR c.industry LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND c.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY c.updated_at DESC, c.id DESC LIMIT 200");
        return ApiResponse.ok(jdbcTemplate.queryForList(sql.toString(), args.toArray()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        String sql = """
                SELECT c.*, u.real_name AS owner_name
                FROM customers c
                LEFT JOIN users u ON u.id = c.owner_user_id
                WHERE c.id = ?
                """;
        return ApiResponse.ok(jdbcTemplate.queryForMap(sql, id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        CurrentUser currentUser = (CurrentUser) request.getAttribute("currentUser");
        Map<String, Object> data = SqlMaps.mutable(body);
        Long ownerId = SqlMaps.longValue(data, "owner_user_id");
        if (ownerId == null) {
            ownerId = currentUser.id();
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        Long finalOwnerId = ownerId;
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO customers(code, name, grade, source, industry, scale, province, city, address, phone,
                    email, website, status, stage, owner_user_id, next_follow_time, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            bindCustomer(ps, data, finalOwnerId);
            return ps;
        }, keyHolder);
        return detail(keyHolder.getKey().longValue());
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> data = SqlMaps.mutable(body);
        jdbcTemplate.update("""
                UPDATE customers
                SET code = ?, name = ?, grade = ?, source = ?, industry = ?, scale = ?, province = ?, city = ?,
                    address = ?, phone = ?, email = ?, website = ?, status = ?, stage = ?, owner_user_id = ?,
                    next_follow_time = ?, remark = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                SqlMaps.text(data, "code"),
                SqlMaps.text(data, "name"),
                SqlMaps.text(data, "grade"),
                SqlMaps.text(data, "source"),
                SqlMaps.text(data, "industry"),
                SqlMaps.text(data, "scale"),
                SqlMaps.text(data, "province"),
                SqlMaps.text(data, "city"),
                SqlMaps.text(data, "address"),
                SqlMaps.text(data, "phone"),
                SqlMaps.text(data, "email"),
                SqlMaps.text(data, "website"),
                defaultText(data, "status", "POTENTIAL"),
                defaultText(data, "stage", "INITIAL_CONTACT"),
                SqlMaps.longValue(data, "owner_user_id"),
                SqlMaps.text(data, "next_follow_time"),
                SqlMaps.text(data, "remark"),
                id);
        return detail(id);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jdbcTemplate.update("DELETE FROM customers WHERE id = ?", id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/timeline")
    public ApiResponse<Map<String, Object>> timeline(@PathVariable Long id) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("contacts", jdbcTemplate.queryForList("SELECT * FROM contacts WHERE customer_id = ? ORDER BY id DESC", id));
        result.put("followRecords", jdbcTemplate.queryForList("""
                SELECT f.*, u.real_name AS user_name, ct.name AS contact_name
                FROM follow_records f
                LEFT JOIN users u ON u.id = f.user_id
                LEFT JOIN contacts ct ON ct.id = f.contact_id
                WHERE f.customer_id = ?
                ORDER BY f.follow_time DESC, f.id DESC
                """, id));
        result.put("orders", jdbcTemplate.queryForList("SELECT * FROM sales_orders WHERE customer_id = ? ORDER BY order_date DESC, id DESC", id));
        return ApiResponse.ok(result);
    }

    private void bindCustomer(PreparedStatement ps, Map<String, Object> data, Long ownerId) throws java.sql.SQLException {
        ps.setString(1, SqlMaps.text(data, "code"));
        ps.setString(2, required(data, "name"));
        ps.setString(3, SqlMaps.text(data, "grade"));
        ps.setString(4, SqlMaps.text(data, "source"));
        ps.setString(5, SqlMaps.text(data, "industry"));
        ps.setString(6, SqlMaps.text(data, "scale"));
        ps.setString(7, SqlMaps.text(data, "province"));
        ps.setString(8, SqlMaps.text(data, "city"));
        ps.setString(9, SqlMaps.text(data, "address"));
        ps.setString(10, SqlMaps.text(data, "phone"));
        ps.setString(11, SqlMaps.text(data, "email"));
        ps.setString(12, SqlMaps.text(data, "website"));
        ps.setString(13, defaultText(data, "status", "POTENTIAL"));
        ps.setString(14, defaultText(data, "stage", "INITIAL_CONTACT"));
        ps.setObject(15, ownerId);
        ps.setString(16, SqlMaps.text(data, "next_follow_time"));
        ps.setString(17, SqlMaps.text(data, "remark"));
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

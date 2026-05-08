package com.futurecrm.ai.order;

import com.futurecrm.ai.common.ApiResponse;
import com.futurecrm.ai.common.CurrentUser;
import com.futurecrm.ai.common.SqlMaps;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final JdbcTemplate jdbcTemplate;

    public OrderController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.*, c.name AS customer_name, u.real_name AS owner_name
                FROM sales_orders o
                LEFT JOIN customers c ON c.id = o.customer_id
                LEFT JOIN users u ON u.id = o.owner_user_id
                WHERE 1 = 1
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (o.order_no LIKE ? OR c.name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND o.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY o.order_date DESC, o.id DESC LIMIT 200");
        return ApiResponse.ok(jdbcTemplate.queryForList(sql.toString(), args.toArray()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> order = jdbcTemplate.queryForMap("""
                SELECT o.*, c.name AS customer_name, ct.name AS contact_name, u.real_name AS owner_name
                FROM sales_orders o
                LEFT JOIN customers c ON c.id = o.customer_id
                LEFT JOIN contacts ct ON ct.id = o.contact_id
                LEFT JOIN users u ON u.id = o.owner_user_id
                WHERE o.id = ?
                """, id);
        order.put("items", jdbcTemplate.queryForList("SELECT * FROM sales_order_items WHERE order_id = ? ORDER BY id", id));
        return ApiResponse.ok(order);
    }

    @PostMapping
    @Transactional
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
                    INSERT INTO sales_orders(order_no, customer_id, contact_id, owner_user_id, order_date, amount,
                    paid_amount, status, delivery_address, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, defaultText(data, "order_no", "SO" + System.currentTimeMillis()));
            ps.setLong(2, requiredLong(data, "customer_id"));
            ps.setObject(3, SqlMaps.longValue(data, "contact_id"));
            ps.setLong(4, finalOwnerId);
            ps.setString(5, defaultText(data, "order_date", LocalDate.now().toString()));
            ps.setDouble(6, defaultDouble(data, "amount", 0));
            ps.setDouble(7, defaultDouble(data, "paid_amount", 0));
            ps.setString(8, defaultText(data, "status", "DRAFT"));
            ps.setString(9, SqlMaps.text(data, "delivery_address"));
            ps.setString(10, SqlMaps.text(data, "remark"));
            return ps;
        }, keyHolder);
        Long orderId = keyHolder.getKey().longValue();
        saveItems(orderId, data);
        return detail(orderId);
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> data = SqlMaps.mutable(body);
        jdbcTemplate.update("""
                UPDATE sales_orders
                SET order_no = ?, customer_id = ?, contact_id = ?, owner_user_id = ?, order_date = ?, amount = ?,
                    paid_amount = ?, status = ?, delivery_address = ?, remark = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                defaultText(data, "order_no", "SO" + System.currentTimeMillis()),
                requiredLong(data, "customer_id"),
                SqlMaps.longValue(data, "contact_id"),
                requiredLong(data, "owner_user_id"),
                defaultText(data, "order_date", LocalDate.now().toString()),
                defaultDouble(data, "amount", 0),
                defaultDouble(data, "paid_amount", 0),
                defaultText(data, "status", "DRAFT"),
                SqlMaps.text(data, "delivery_address"),
                SqlMaps.text(data, "remark"),
                id);
        jdbcTemplate.update("DELETE FROM sales_order_items WHERE order_id = ?", id);
        saveItems(id, data);
        return detail(id);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jdbcTemplate.update("DELETE FROM sales_orders WHERE id = ?", id);
        return ApiResponse.ok(null);
    }

    @SuppressWarnings("unchecked")
    private void saveItems(Long orderId, Map<String, Object> data) {
        Object rawItems = data.get("items");
        if (!(rawItems instanceof List<?> items)) {
            return;
        }
        for (Object rawItem : items) {
            if (!(rawItem instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> item = (Map<String, Object>) map;
            jdbcTemplate.update("""
                    INSERT INTO sales_order_items(order_id, item_name, quantity, unit_price, amount, remark)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    orderId,
                    defaultText(item, "item_name", "服务项目"),
                    defaultDouble(item, "quantity", 1),
                    defaultDouble(item, "unit_price", 0),
                    defaultDouble(item, "amount", defaultDouble(item, "quantity", 1) * defaultDouble(item, "unit_price", 0)),
                    SqlMaps.text(item, "remark"));
        }
    }

    private String defaultText(Map<String, Object> data, String key, String fallback) {
        String value = SqlMaps.text(data, key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private double defaultDouble(Map<String, Object> data, String key, double fallback) {
        Double value = SqlMaps.doubleValue(data, key);
        return value == null ? fallback : value;
    }

    private long requiredLong(Map<String, Object> data, String key) {
        Long value = SqlMaps.longValue(data, key);
        if (value == null) {
            throw new IllegalArgumentException(key + "不能为空");
        }
        return value;
    }
}

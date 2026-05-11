package com.futurecrm.ai.receipt;

import com.futurecrm.ai.common.ApiResponse;
import com.futurecrm.ai.common.CurrentUser;
import com.futurecrm.ai.common.SqlMaps;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {
    private final JdbcTemplate jdbcTemplate;

    public ReceiptController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.*, c.name AS customer_name, s.order_no AS order_no
                FROM receipts r
                LEFT JOIN customers c ON c.id = r.customer_id
                LEFT JOIN sales_orders s ON s.id = r.order_id
                WHERE 1 = 1
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (customerId != null) {
            sql.append(" AND r.customer_id = ?");
            args.add(customerId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND r.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY r.expected_date DESC, r.id DESC LIMIT 200");
        return ApiResponse.ok(jdbcTemplate.queryForList(sql.toString(), args.toArray()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> receipt = jdbcTemplate.queryForMap("""
                SELECT r.*, c.name AS customer_name, s.order_no AS order_no
                FROM receipts r
                LEFT JOIN customers c ON c.id = r.customer_id
                LEFT JOIN sales_orders s ON s.id = r.order_id
                WHERE r.id = ?
                """, id);
        return ApiResponse.ok(receipt);
    }

    @GetMapping("/overdue")
    public ApiResponse<List<Map<String, Object>>> overdue() {
        String sql = """
                SELECT r.*, c.name AS customer_name, s.order_no AS order_no
                FROM receipts r
                LEFT JOIN customers c ON c.id = r.customer_id
                LEFT JOIN sales_orders s ON s.id = r.order_id
                WHERE r.status IN ('UNPAID', 'PARTIAL')
                  AND r.expected_date IS NOT NULL
                  AND date(r.expected_date) < date('now')
                ORDER BY r.expected_date ASC
                LIMIT 50
                """;
        return ApiResponse.ok(jdbcTemplate.queryForList(sql));
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        CurrentUser currentUser = (CurrentUser) request.getAttribute("currentUser");
        Map<String, Object> data = SqlMaps.mutable(body);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO receipts(customer_id, order_id, receivable_amount, received_amount,
                    status, expected_date, received_date, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, requiredLong(data, "customer_id"));
            ps.setObject(2, SqlMaps.longValue(data, "order_id"));
            ps.setDouble(3, defaultDouble(data, "receivable_amount", 0));
            ps.setDouble(4, defaultDouble(data, "received_amount", 0));
            ps.setString(5, computeStatus(data));
            ps.setString(6, SqlMaps.text(data, "expected_date"));
            ps.setString(7, SqlMaps.text(data, "received_date"));
            ps.setString(8, SqlMaps.text(data, "remark"));
            return ps;
        }, keyHolder);
        Long receiptId = keyHolder.getKey().longValue();
        syncOrderPaidAmount(data);
        return detail(receiptId);
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> data = SqlMaps.mutable(body);
        jdbcTemplate.update("""
                UPDATE receipts
                SET customer_id = ?, order_id = ?, receivable_amount = ?, received_amount = ?,
                    status = ?, expected_date = ?, received_date = ?, remark = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                requiredLong(data, "customer_id"),
                SqlMaps.longValue(data, "order_id"),
                defaultDouble(data, "receivable_amount", 0),
                defaultDouble(data, "received_amount", 0),
                computeStatus(data),
                SqlMaps.text(data, "expected_date"),
                SqlMaps.text(data, "received_date"),
                SqlMaps.text(data, "remark"),
                id);
        syncOrderPaidAmount(data);
        return detail(id);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jdbcTemplate.update("DELETE FROM receipts WHERE id = ?", id);
        return ApiResponse.ok(null);
    }

    private void syncOrderPaidAmount(Map<String, Object> data) {
        Long orderId = SqlMaps.longValue(data, "order_id");
        if (orderId == null) return;
        Double totalReceived = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(received_amount), 0) FROM receipts WHERE order_id = ?", Double.class, orderId);
        if (totalReceived != null) {
            jdbcTemplate.update("UPDATE sales_orders SET paid_amount = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    totalReceived, orderId);
        }
    }

    private String computeStatus(Map<String, Object> data) {
        double receivable = defaultDouble(data, "receivable_amount", 0);
        double received = defaultDouble(data, "received_amount", 0);
        String expectedDate = SqlMaps.text(data, "expected_date");

        if (received >= receivable && receivable > 0) return "PAID";
        if (received > 0) return "PARTIAL";
        // Check overdue
        if (expectedDate != null && !expectedDate.isBlank()) {
            try {
                if (LocalDate.parse(expectedDate).isBefore(LocalDate.now())) return "OVERDUE";
            } catch (Exception ignored) {}
        }
        return "UNPAID";
    }

    private double defaultDouble(Map<String, Object> data, String key, double fallback) {
        Double value = SqlMaps.doubleValue(data, key);
        return value == null ? fallback : value;
    }

    private long requiredLong(Map<String, Object> data, String key) {
        Long value = SqlMaps.longValue(data, key);
        if (value == null) throw new IllegalArgumentException(key + "不能为空");
        return value;
    }
}

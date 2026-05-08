package com.futurecrm.ai.dashboard;

import com.futurecrm.ai.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final JdbcTemplate jdbcTemplate;

    public DashboardController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customerCount", count("SELECT COUNT(*) FROM customers"));
        data.put("newCustomersThisWeek", count("SELECT COUNT(*) FROM customers WHERE created_at >= datetime('now','-7 days')"));
        data.put("todayFollowCount", count("SELECT COUNT(*) FROM customers WHERE date(next_follow_time) <= date('now')"));
        data.put("openOrderCount", count("SELECT COUNT(*) FROM sales_orders WHERE status NOT IN ('COMPLETED','CANCELLED')"));
        data.put("monthOrderAmount", jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM sales_orders
                WHERE strftime('%Y-%m', order_date) = strftime('%Y-%m', 'now')
                """, Double.class));
        data.put("recentFollows", jdbcTemplate.queryForList("""
                SELECT f.*, c.name AS customer_name, u.real_name AS user_name
                FROM follow_records f
                LEFT JOIN customers c ON c.id = f.customer_id
                LEFT JOIN users u ON u.id = f.user_id
                ORDER BY f.follow_time DESC, f.id DESC
                LIMIT 8
                """));
        data.put("aiTodayAdvice", java.util.List.of(
                "优先联系下次跟进时间已到期的客户。",
                "对已进入方案报价阶段的客户生成针对性跟进话术。",
                "检查本月大额订单的收款状态和交付风险。"
        ));
        return ApiResponse.ok(data);
    }

    private Long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }
}

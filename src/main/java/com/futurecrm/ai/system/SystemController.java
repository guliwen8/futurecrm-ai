package com.futurecrm.ai.system;

import com.futurecrm.ai.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final JdbcTemplate jdbcTemplate;
    private final String aiApiKey;
    private final String aiBaseUrl;
    private final String aiModel;
    private final String aiApiStyle;

    public SystemController(JdbcTemplate jdbcTemplate,
                            @Value("${futurecrm.ai.api-key:}") String aiApiKey,
                            @Value("${futurecrm.ai.base-url:https://api.xiaomimimo.com/v1}") String aiBaseUrl,
                            @Value("${futurecrm.ai.model:mimo-v2-flash}") String aiModel,
                            @Value("${futurecrm.ai.api-style:chat-completions}") String aiApiStyle) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiApiKey = aiApiKey;
        this.aiBaseUrl = aiBaseUrl;
        this.aiModel = aiModel;
        this.aiApiStyle = aiApiStyle;
    }

    @GetMapping("/ai-config")
    public ApiResponse<Map<String, Object>> aiConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("apiKeyConfigured", aiApiKey != null && !aiApiKey.isBlank());
        config.put("apiKeyHint", aiApiKey != null && !aiApiKey.isBlank()
                ? aiApiKey.substring(0, 3) + "****" + aiApiKey.substring(Math.max(3, aiApiKey.length() - 4))
                : "未配置");
        config.put("baseUrl", aiBaseUrl);
        config.put("model", aiModel);
        config.put("apiStyle", aiApiStyle);
        return ApiResponse.ok(config);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("database", "SQLite");
        data.put("customerCount", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers", Long.class));
        data.put("orderCount", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sales_orders", Long.class));
        data.put("aiConfigured", aiApiKey != null && !aiApiKey.isBlank());
        return ApiResponse.ok(data);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalCustomers", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers", Long.class));
        data.put("totalOrders", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sales_orders", Long.class));
        data.put("totalFollows", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM follow_records", Long.class));
        data.put("totalReceipts", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM receipts", Long.class));
        data.put("totalUsers", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class));
        data.put("overdueReceipts", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM receipts WHERE status = 'OVERDUE'", Long.class));
        data.put("monthOrderAmount", jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM sales_orders
                WHERE strftime('%Y-%m', order_date) = strftime('%Y-%m', 'now')
                """, Double.class));
        return ApiResponse.ok(data);
    }
}

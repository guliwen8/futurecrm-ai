package com.futurecrm.ai.ai;

import com.futurecrm.ai.common.ApiResponse;
import com.futurecrm.ai.common.SqlMaps;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/customer-profile")
    public ApiResponse<Map<String, Object>> customerProfile(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiService.customerProfile(requiredLong(body, "customer_id")));
    }

    @PostMapping("/follow-suggestion")
    public ApiResponse<Map<String, Object>> followSuggestion(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiService.followSuggestion(requiredLong(body, "customer_id")));
    }

    @PostMapping("/follow-summary")
    public ApiResponse<Map<String, Object>> followSummary(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiService.followSummary(requiredText(body, "content")));
    }

    @PostMapping("/sales-script")
    public ApiResponse<Map<String, Object>> salesScript(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiService.salesScript(requiredLong(body, "customer_id"), SqlMaps.text(body, "channel")));
    }

    @PostMapping("/order-risk")
    public ApiResponse<Map<String, Object>> orderRisk(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiService.orderRisk(requiredLong(body, "order_id")));
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiService.chat(requiredText(body, "question")));
    }

    private Long requiredLong(Map<String, Object> body, String key) {
        Long value = SqlMaps.longValue(body, key);
        if (value == null) {
            throw new IllegalArgumentException(key + "不能为空");
        }
        return value;
    }

    private String requiredText(Map<String, Object> body, String key) {
        String value = SqlMaps.text(body, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + "不能为空");
        }
        return value;
    }
}

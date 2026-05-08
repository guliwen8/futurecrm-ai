package com.futurecrm.ai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String apiStyle;

    public AiService(JdbcTemplate jdbcTemplate,
                     ObjectMapper objectMapper,
                     @Value("${futurecrm.ai.api-key:}") String apiKey,
                     @Value("${futurecrm.ai.base-url:https://api.xiaomimimo.com/v1}") String baseUrl,
                     @Value("${futurecrm.ai.model:mimo-v2-flash}") String model,
                     @Value("${futurecrm.ai.api-style:chat-completions}") String apiStyle) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiStyle = apiStyle;
    }

    public Map<String, Object> customerProfile(Long customerId) {
        Map<String, Object> context = customerContext(customerId);
        String prompt = """
                你是一个 CRM 销售顾问。请基于以下客户资料生成客户画像。
                输出结构：
                1. 客户概况
                2. 关键需求推断
                3. 成交机会
                4. 风险点
                5. 下一步建议

                客户资料：
                %s
                """.formatted(toJson(context));
        String content = complete(prompt, fallbackCustomerProfile(context));
        saveInsight("CUSTOMER", customerId, "PROFILE", content, toJson(context));
        return result("客户画像", content, context);
    }

    public Map<String, Object> followSuggestion(Long customerId) {
        Map<String, Object> context = customerContext(customerId);
        String prompt = """
                你是一个资深销售教练。请基于客户资料和跟进历史，给出下一次跟进建议。
                输出：跟进目标、沟通重点、建议话术、需要确认的问题、风险提醒。

                客户资料：
                %s
                """.formatted(toJson(context));
        String content = complete(prompt, fallbackFollowSuggestion(context));
        saveInsight("CUSTOMER", customerId, "FOLLOW_SUGGESTION", content, toJson(context));
        return result("跟进建议", content, context);
    }

    public Map<String, Object> followSummary(String content) {
        String prompt = """
                请把下面的销售跟进原始记录整理成规范 CRM 记录。
                输出：摘要、客户反馈、需求、异议、下一步动作、建议下次跟进时间。

                原始记录：
                %s
                """.formatted(content);
        String fallback = """
                摘要：已记录本次客户沟通内容。
                客户反馈：请补充客户明确表达的需求、异议和预算。
                需求：从原始记录中提取客户想解决的问题。
                异议：关注价格、交付周期、决策流程等阻碍。
                下一步动作：建议明确一个可执行动作，并设置下次跟进时间。
                """;
        return result("跟进总结", complete(prompt, fallback), Map.of("content", content));
    }

    public Map<String, Object> salesScript(Long customerId, String channel) {
        Map<String, Object> context = customerContext(customerId);
        String prompt = """
                请基于客户资料，生成一段适合 %s 渠道的销售跟进话术。
                要求：自然、简洁、有下一步邀约，不要过度承诺。

                客户资料：
                %s
                """.formatted(channel == null ? "微信" : channel, toJson(context));
        String fallback = "您好，想跟您同步一下上次沟通后我们整理的方案重点，也想确认下您目前最关注的是预算、效果还是上线时间。我这边可以根据您的优先级再做一版更贴近业务的建议。";
        return result("销售话术", complete(prompt, fallback), context);
    }

    public Map<String, Object> orderRisk(Long orderId) {
        Map<String, Object> order = jdbcTemplate.queryForMap("""
                SELECT o.*, c.name AS customer_name, c.status AS customer_status, c.stage AS customer_stage
                FROM sales_orders o
                LEFT JOIN customers c ON c.id = o.customer_id
                WHERE o.id = ?
                """, orderId);
        order.put("items", jdbcTemplate.queryForList("SELECT * FROM sales_order_items WHERE order_id = ?", orderId));
        String prompt = """
                请检查以下 CRM 销售订单风险。
                输出：订单摘要、收款风险、交付风险、客户风险、建议动作。

                订单资料：
                %s
                """.formatted(toJson(order));
        String content = complete(prompt, fallbackOrderRisk(order));
        saveInsight("ORDER", orderId, "RISK", content, toJson(order));
        return result("订单风险", content, order);
    }

    public Map<String, Object> chat(String question) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("customerCount", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers", Long.class));
        context.put("openOrders", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sales_orders WHERE status NOT IN ('COMPLETED','CANCELLED')", Long.class));
        context.put("dueCustomers", jdbcTemplate.queryForList("""
                SELECT id, name, status, stage, next_follow_time
                FROM customers
                WHERE next_follow_time IS NOT NULL
                ORDER BY next_follow_time ASC
                LIMIT 10
                """));
        String prompt = """
                你是 FutureCRM AI 助手。请基于系统摘要回答用户问题。

                用户问题：%s
                系统摘要：%s
                """.formatted(question, toJson(context));
        String fallback = "当前系统已有 " + context.get("customerCount") + " 个客户，未完成订单 " + context.get("openOrders") + " 个。建议优先查看下次跟进时间最近的客户，并补充跟进记录。";
        return result("AI 助手", complete(prompt, fallback), context);
    }

    private Map<String, Object> customerContext(Long customerId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("customer", jdbcTemplate.queryForMap("""
                SELECT c.*, u.real_name AS owner_name
                FROM customers c
                LEFT JOIN users u ON u.id = c.owner_user_id
                WHERE c.id = ?
                """, customerId));
        context.put("contacts", jdbcTemplate.queryForList("SELECT * FROM contacts WHERE customer_id = ? ORDER BY is_decision_maker DESC, id DESC", customerId));
        context.put("followRecords", jdbcTemplate.queryForList("""
                SELECT follow_type, follow_time, content, customer_feedback, next_action, next_follow_time, ai_summary
                FROM follow_records
                WHERE customer_id = ?
                ORDER BY follow_time DESC, id DESC
                LIMIT 10
                """, customerId));
        context.put("orders", jdbcTemplate.queryForList("""
                SELECT order_no, order_date, amount, paid_amount, status, remark
                FROM sales_orders
                WHERE customer_id = ?
                ORDER BY order_date DESC, id DESC
                LIMIT 10
                """, customerId));
        return context;
    }

    private String complete(String prompt, String fallback) {
        if (apiKey == null || apiKey.isBlank()) {
            return fallback + "\n\n提示：当前未配置 AI_API_KEY / XIAOMI_API_KEY，以上为系统规则生成的本地建议。";
        }
        try {
            Map<String, Object> payload = requestPayload(prompt);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint()))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return fallback + "\n\n提示：AI 服务调用失败，状态码 " + response.statusCode() + "。返回信息：" + abbreviate(response.body());
            }
            return extractText(response.body()).trim();
        } catch (Exception e) {
            return fallback + "\n\n提示：AI 服务暂不可用，已返回本地建议。错误信息：" + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "无";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() > 500 ? compact.substring(0, 500) + "..." : compact;
    }

    private String endpoint() {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if ("responses".equalsIgnoreCase(apiStyle)) {
            return normalizedBaseUrl + "/responses";
        }
        return normalizedBaseUrl + "/chat/completions";
    }

    private Map<String, Object> requestPayload(String prompt) {
        if ("responses".equalsIgnoreCase(apiStyle)) {
            return Map.of("model", model, "input", prompt);
        }
        return Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.3
        );
    }

    private String extractText(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode messageContent = choices.get(0).path("message").path("content");
            if (messageContent.isTextual()) {
                return messageContent.asText();
            }
        }
        JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isTextual()) {
            return outputText.asText();
        }
        StringBuilder text = new StringBuilder();
        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content != null && content.isArray()) {
                    for (JsonNode part : content) {
                        JsonNode partText = part.get("text");
                        if (partText != null && partText.isTextual()) {
                            text.append(partText.asText()).append("\n");
                        }
                    }
                }
            }
        }
        return text.isEmpty() ? "AI 已完成分析，但返回内容为空。" : text.toString();
    }

    private void saveInsight(String bizType, Long bizId, String insightType, String content, String evidence) {
        jdbcTemplate.update("""
                INSERT INTO ai_insights(biz_type, biz_id, insight_type, content, evidence, model)
                VALUES (?, ?, ?, ?, ?, ?)
                """, bizType, bizId, insightType, content, evidence, model);
    }

    private Map<String, Object> result(String title, String content, Object evidence) {
        return Map.of("title", title, "content", content, "evidence", evidence);
    }

    private String fallbackCustomerProfile(Map<String, Object> context) {
        Map<?, ?> customer = (Map<?, ?>) context.get("customer");
        return """
                客户概况：%s 当前处于 %s 阶段，客户状态为 %s。
                关键需求推断：建议从行业、规模、最近跟进反馈中确认真实痛点。
                成交机会：如果已有明确下一步动作，应尽快推进到方案或报价。
                风险点：若长期无跟进记录或下次跟进时间为空，存在流失风险。
                下一步建议：补齐联系人和最近跟进记录，明确决策人、预算、时间表。
                """.formatted(customer.get("name"), customer.get("stage"), customer.get("status"));
    }

    private String fallbackFollowSuggestion(Map<String, Object> context) {
        Map<?, ?> customer = (Map<?, ?>) context.get("customer");
        List<?> follows = (List<?>) context.get("followRecords");
        String history = follows.isEmpty() ? "暂无跟进记录" : "已有 " + follows.size() + " 条近期跟进记录";
        return """
                跟进目标：推进 %s 从当前阶段进入下一步明确动作。
                沟通重点：确认客户最关心的问题、预算、决策流程和时间表。
                建议话术：上次沟通后我整理了几个更贴近您业务目标的建议，想和您确认优先级后再推进方案。
                需要确认的问题：谁参与决策、什么时候评估、当前最大顾虑是什么。
                风险提醒：%s，请及时补充有效跟进。
                """.formatted(customer.get("name"), history);
    }

    private String fallbackOrderRisk(Map<String, Object> order) {
        double amount = ((Number) order.getOrDefault("amount", 0)).doubleValue();
        double paid = ((Number) order.getOrDefault("paid_amount", 0)).doubleValue();
        return """
                订单摘要：订单 %s 金额 %.2f，已收 %.2f。
                收款风险：如已收金额低于订单金额，请设置预计收款日期并持续跟进。
                交付风险：建议补充交付范围、验收标准和责任人。
                客户风险：结合客户阶段和历史跟进判断是否存在决策延迟。
                建议动作：确认收款计划、交付节点和客户验收人。
                """.formatted(order.get("order_no"), amount, paid);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}

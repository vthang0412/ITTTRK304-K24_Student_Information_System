package com.example.student_management.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class AIProviderService {

    private static final int MAX_OUTPUT_LENGTH = 8000;

    @Value("${app.ai.provider:gemini}")
    private String provider;

    @Value("${app.ai.model:gemini-2.0-flash}")
    private String model;

    @Value("${app.ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.ai.openai.api-key:}")
    private String openaiApiKey;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AIProviderResult {
        private String content;
        private String providerUsed;
        private String modelUsed;
        private boolean success;
        private long latencyMs;
        private List<String> sources;
    }

    // Backward-compatible overload for existing callers (insights/advisor/planner/older chat)
    @SuppressWarnings("unchecked")
    public AIProviderResult generateChatResponse(
            String systemContext,
            List<Map<String, String>> conversationHistory,
            String userQuestion,
            Map<String, Object> academicSummary
    ) {
        String fallback = generateLegacySisFallback(userQuestion, academicSummary == null ? Map.of() : academicSummary);
        List<String> sources = detectSources(userQuestion, academicSummary == null ? Map.of() : academicSummary);
        return generateChatResponse(systemContext, conversationHistory, userQuestion, fallback, sources);
    }

    public AIProviderResult generateChatResponse(
            String systemContext,
            List<Map<String, String>> conversationHistory,
            String userQuestion,
            String fallbackContent,
            List<String> sources
    ) {
        long start = System.currentTimeMillis();
        List<String> safeSources = (sources == null || sources.isEmpty()) ? List.of("Student SIS Profile") : sources;

        if ("gemini".equalsIgnoreCase(provider) && geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                String answer = callGeminiApi(systemContext, conversationHistory, userQuestion);
                if (answer != null && !answer.isBlank()) {
                    return AIProviderResult.builder()
                            .content(limitOutput(answer))
                            .providerUsed("GEMINI")
                            .modelUsed(model)
                            .success(true)
                            .latencyMs(System.currentTimeMillis() - start)
                            .sources(safeSources)
                            .build();
                }
            } catch (Exception ex) {
                log.warn("AI provider call failed: type={}", ex.getClass().getSimpleName());
            }
        }

        if ("openai".equalsIgnoreCase(provider) && openaiApiKey != null && !openaiApiKey.isBlank()) {
            try {
                String answer = callOpenAiApi(systemContext, conversationHistory, userQuestion);
                if (answer != null && !answer.isBlank()) {
                    return AIProviderResult.builder()
                            .content(limitOutput(answer))
                            .providerUsed("OPENAI")
                            .modelUsed(model)
                            .success(true)
                            .latencyMs(System.currentTimeMillis() - start)
                            .sources(safeSources)
                            .build();
                }
            } catch (Exception ex) {
                log.warn("AI provider call failed: type={}", ex.getClass().getSimpleName());
            }
        }

        String content = (fallbackContent == null || fallbackContent.isBlank())
                ? "Hiện tại trợ lý AI chưa thể phản hồi. Vui lòng thử lại sau."
                : fallbackContent;
        return AIProviderResult.builder()
                .content(content)
                .providerUsed("SIS-ASSISTANT-ENGINE")
                .modelUsed("sis-local-v1")
                .success(false)
                .latencyMs(System.currentTimeMillis() - start)
                .sources(safeSources)
                .build();
    }

    public AIProviderResult generateExternalAnalysis(String systemPrompt, String userPrompt) {
        long start = System.currentTimeMillis();
        if ("gemini".equalsIgnoreCase(provider) && geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                String answer = callGeminiApi(systemPrompt, List.of(), userPrompt);
                if (answer != null && !answer.isBlank()) {
                    return AIProviderResult.builder()
                            .content(limitOutput(answer))
                            .providerUsed("GEMINI")
                            .modelUsed(model)
                            .success(true)
                            .latencyMs(System.currentTimeMillis() - start)
                            .build();
                }
            } catch (Exception ex) {
                log.warn("AI provider call failed: type={}", ex.getClass().getSimpleName());
            }
        }
        if ("openai".equalsIgnoreCase(provider) && openaiApiKey != null && !openaiApiKey.isBlank()) {
            try {
                String answer = callOpenAiApi(systemPrompt, List.of(), userPrompt);
                if (answer != null && !answer.isBlank()) {
                    return AIProviderResult.builder()
                            .content(limitOutput(answer))
                            .providerUsed("OPENAI")
                            .modelUsed(model)
                            .success(true)
                            .latencyMs(System.currentTimeMillis() - start)
                            .build();
                }
            } catch (Exception ex) {
                log.warn("AI provider call failed: type={}", ex.getClass().getSimpleName());
            }
        }
        return AIProviderResult.builder()
                .content(null)
                .providerUsed("SIS-ASSISTANT-ENGINE")
                .modelUsed("sis-local-v1")
                .success(false)
                .latencyMs(System.currentTimeMillis() - start)
                .build();
    }

    private String callGeminiApi(String systemContext, List<Map<String, String>> history, String userQuestion) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemContext == null ? "" : systemContext))));

        List<Map<String, Object>> contents = new ArrayList<>();
        if (history != null) {
            int startIdx = Math.max(0, history.size() - 12);
            for (int i = startIdx; i < history.size(); i++) {
                Map<String, String> msg = history.get(i);
                String role = msg.getOrDefault("role", "user");
                String geminiRole = "assistant".equalsIgnoreCase(role) || "ASSISTANT".equalsIgnoreCase(role) ? "model" : "user";
                String content = msg.getOrDefault("content", "");
                if (content == null || content.isBlank()) continue;
                contents.add(Map.of("role", geminiRole, "parts", List.of(Map.of("text", content))));
            }
        }
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", userQuestion == null ? "" : userQuestion))));
        body.put("contents", contents);
        body.put("generationConfig", Map.of("temperature", 0.3, "maxOutputTokens", 1024));

        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", geminiApiKey == null ? "" : geminiApiKey.trim())
                .timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText();
                }
            }
        }
        throw new RuntimeException("Gemini API returned status " + response.statusCode());
    }

    private String callOpenAiApi(String systemContext, List<Map<String, String>> history, String userQuestion) throws Exception {
        String url = "https://api.openai.com/v1/chat/completions";
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemContext == null ? "" : systemContext));
        if (history != null) {
            int startIdx = Math.max(0, history.size() - 12);
            for (int i = startIdx; i < history.size(); i++) {
                Map<String, String> msg = history.get(i);
                String role = msg.getOrDefault("role", "user");
                String oaiRole = "assistant".equalsIgnoreCase(role) || "ASSISTANT".equalsIgnoreCase(role) ? "assistant" : "user";
                String content = msg.getOrDefault("content", "");
                if (content == null || content.isBlank()) continue;
                messages.add(Map.of("role", oaiRole, "content", content));
            }
        }
        messages.add(Map.of("role", "user", "content", userQuestion == null ? "" : userQuestion));

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.3,
                "max_tokens", 1024
        );
        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openaiApiKey.trim())
                .timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText();
            }
        }
        throw new RuntimeException("OpenAI API returned status " + response.statusCode());
    }

    private String limitOutput(String answer) {
        if (answer == null) return null;
        String trimmed = answer.trim();
        return trimmed.length() > MAX_OUTPUT_LENGTH ? trimmed.substring(0, MAX_OUTPUT_LENGTH) : trimmed;
    }

    @SuppressWarnings("unchecked")
    private String generateLegacySisFallback(String question, Map<String, Object> data) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String fullName = String.valueOf(data.getOrDefault("fullName", "bạn"));
        int accumulatedCredits = toInt(data.get("accumulatedCredits"));
        Object gpa10 = data.getOrDefault("gpa10", "0.00");
        Object gpa4 = data.getOrDefault("gpa4", "0.00");
        List<Map<String, Object>> passed = (List<Map<String, Object>>) data.getOrDefault("passedCourses", List.of());
        List<Map<String, Object>> failed = (List<Map<String, Object>>) data.getOrDefault("failedCourses", List.of());
        List<Map<String, Object>> active = (List<Map<String, Object>>) data.getOrDefault("activeEnrollments", List.of());

        if (q.contains("tín chỉ") || q.contains("tin chi") || q.contains("tích lũy") || q.contains("tich luy") || q.contains("credit")) {
            int activeCredits = active.stream().mapToInt(a -> toInt(a.get("credits"))).sum();
            return "Chào **" + fullName + "**,\n\n- **Tín chỉ tích lũy:** **" + accumulatedCredits + " TC**\n- **Tín chỉ đang học:** **" + activeCredits + " TC** (" + active.size() + " lớp)\n- **Môn chưa đạt:** " + failed.size();
        }
        if (q.contains("điểm") || q.contains("diem") || q.contains("gpa") || q.contains("kết quả") || q.contains("ket qua")) {
            StringBuilder sb = new StringBuilder();
            sb.append("Tổng hợp kết quả học tập của **").append(fullName).append("**:\n- GPA hệ 10: **").append(gpa10).append("**\n- GPA hệ 4: **").append(gpa4).append("**\n");
            for (Map<String, Object> p : passed) sb.append("- ").append(p.get("courseCode")).append(" - ").append(p.get("courseName")).append(": ").append(p.get("finalScore")).append("\n");
            for (Map<String, Object> f : failed) sb.append("- [Chưa đạt] ").append(f.get("courseCode")).append(" - ").append(f.get("courseName")).append(": ").append(f.get("finalScore")).append("\n");
            if (passed.isEmpty() && failed.isEmpty()) sb.append("- Hiện hệ thống chưa có dữ liệu điểm công bố.\n");
            return sb.toString();
        }
        if (q.contains("chưa hoàn thành") || q.contains("chua hoan thanh") || q.contains("nợ") || q.contains("rớt") || q.contains("rot") || q.contains("học lại")) {
            StringBuilder sb = new StringBuilder();
            if (failed.isEmpty()) sb.append("Hiện hệ thống chưa ghi nhận môn chưa đạt theo dữ liệu điểm công bố.");
            else {
                sb.append("**Môn chưa đạt/cần cải thiện:**\n");
                for (Map<String, Object> f : failed) sb.append("- ").append(f.get("courseCode")).append(" - ").append(f.get("courseName")).append(" (").append(f.get("credits")).append(" TC): ").append(f.get("finalScore")).append("\n");
            }
            return sb.toString();
        }
        if (q.contains("đăng ký") || q.contains("dang ky") || q.contains("lớp") || q.contains("lop") || q.contains("lịch") || q.contains("lich") || q.contains("thời khóa biểu")) {
            StringBuilder sb = new StringBuilder();
            sb.append("**Lớp học phần đang học:** ").append(active.size()).append("\n");
            for (Map<String, Object> a : active) sb.append("- ").append(a.get("sectionCode")).append(" - ").append(a.get("courseName")).append(" (").append(a.get("credits")).append(" TC), lịch: ").append(a.get("schedule")).append("\n");
            if (active.isEmpty()) sb.append("- Hiện bạn chưa có lớp học phần ENROLLED trong hệ thống.\n");
            return sb.toString();
        }
        return "Chào **" + fullName + "**. GPA hiện tại **" + gpa10 + "/10** (" + gpa4 + "/4.0), tín chỉ tích lũy **" + accumulatedCredits + " TC**, đang học **" + active.size() + " lớp**. Bạn có thể hỏi về GPA, tín chỉ, điểm môn, môn chưa đạt, hoặc lớp đang đăng ký.";
    }

    private List<String> detectSources(String question, Map<String, Object> data) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        List<String> sources = new ArrayList<>();
        if (q.contains("điểm") || q.contains("diem") || q.contains("gpa") || q.contains("tín chỉ") || q.contains("tin chi") || q.contains("hoàn thành") || data.containsKey("gpa10")) {
            sources.add("Published Grades & Academic Records");
        }
        if (q.contains("đăng ký") || q.contains("dang ky") || q.contains("lớp") || q.contains("lop") || q.contains("lịch") || q.contains("lich") || data.containsKey("activeEnrollments")) {
            sources.add("Enrollment Records");
        }
        if (sources.isEmpty()) sources.add("Student SIS Profile");
        return sources;
    }

    private int toInt(Object o) {
        if (o instanceof Integer i) return i;
        if (o instanceof Long l) return l.intValue();
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}

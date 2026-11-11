package com.example.student_management.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class AIProviderService {

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AIProviderResult {
        private String content;
        private String providerUsed;
        private String modelUsed;
        private boolean success;
        private long latencyMs;
        private List<String> sources;
    }

    public AIProviderResult generateChatResponse(
            String systemContext,
            List<Map<String, String>> conversationHistory,
            String userQuestion,
            Map<String, Object> academicSummary
    ) {
        long start = System.currentTimeMillis();

        // 1. Ưu tiên gọi Gemini API nếu có GEMINI_API_KEY
        if ("gemini".equalsIgnoreCase(provider) && geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                String answer = callGeminiApi(systemContext, conversationHistory, userQuestion);
                if (answer != null && !answer.isBlank()) {
                    return AIProviderResult.builder()
                            .content(answer)
                            .providerUsed("GEMINI")
                            .modelUsed(model)
                            .success(true)
                            .latencyMs(System.currentTimeMillis() - start)
                            .sources(List.of("Academic Records", "Published Grades", "Enrollments"))
                            .build();
                }
            } catch (Exception e) {
                // Nếu Gemini lỗi/timeout -> rơi xuống fallback xử lý dữ liệu thật cục bộ để không làm sập trải nghiệm
            }
        }

        // 2. Hoặc gọi OpenAI API nếu được cấu hình
        if ("openai".equalsIgnoreCase(provider) && openaiApiKey != null && !openaiApiKey.isBlank()) {
            try {
                String answer = callOpenAiApi(systemContext, conversationHistory, userQuestion);
                if (answer != null && !answer.isBlank()) {
                    return AIProviderResult.builder()
                            .content(answer)
                            .providerUsed("OPENAI")
                            .modelUsed(model)
                            .success(true)
                            .latencyMs(System.currentTimeMillis() - start)
                            .sources(List.of("Academic Records", "Published Grades", "Enrollments"))
                            .build();
                }
            } catch (Exception e) {
                // Fallback
            }
        }

        // 3. Fallback Engine thông minh truy vấn trực tiếp từ dữ liệu thật SIS
        String fallbackAnswer = generateSmartSisFallback(userQuestion, academicSummary);
        return AIProviderResult.builder()
                .content(fallbackAnswer)
                .providerUsed("SIS-ASSISTANT-ENGINE")
                .modelUsed("sis-local-v1")
                .success(true)
                .latencyMs(System.currentTimeMillis() - start)
                .sources(detectSources(userQuestion))
                .build();
    }

    private String callGeminiApi(String systemContext, List<Map<String, String>> history, String userQuestion) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + geminiApiKey.trim();

        Map<String, Object> body = new LinkedHashMap<>();

        // System instruction
        Map<String, Object> sysPart = Map.of("text", systemContext);
        body.put("systemInstruction", Map.of("parts", List.of(sysPart)));

        // Contents (history + current question)
        List<Map<String, Object>> contents = new ArrayList<>();
        if (history != null) {
            int startIdx = Math.max(0, history.size() - 6); // Giới hạn 6 tin nhắn gần nhất để tiết kiệm token
            for (int i = startIdx; i < history.size(); i++) {
                Map<String, String> msg = history.get(i);
                String geminiRole = "ASSISTANT".equalsIgnoreCase(msg.get("role")) ? "model" : "user";
                contents.add(Map.of(
                        "role", geminiRole,
                        "parts", List.of(Map.of("text", msg.get("content")))
                ));
            }
        }
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userQuestion))
        ));
        body.put("contents", contents);

        body.put("generationConfig", Map.of(
                "temperature", 0.3,
                "maxOutputTokens", 1024
        ));

        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
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
        messages.add(Map.of("role", "system", "content", systemContext));

        if (history != null) {
            int startIdx = Math.max(0, history.size() - 6);
            for (int i = startIdx; i < history.size(); i++) {
                Map<String, String> msg = history.get(i);
                String role = "ASSISTANT".equalsIgnoreCase(msg.get("role")) ? "assistant" : "user";
                messages.add(Map.of("role", role, "content", msg.get("content")));
            }
        }
        messages.add(Map.of("role", "user", "content", userQuestion));

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

    @SuppressWarnings("unchecked")
    private String generateSmartSisFallback(String question, Map<String, Object> data) {
        String q = question.toLowerCase();
        String fullName = String.valueOf(data.getOrDefault("fullName", "bạn"));
        int accumulatedCredits = (int) data.getOrDefault("accumulatedCredits", 0);
        Object gpa10 = data.getOrDefault("gpa10", "0.00");
        Object gpa4 = data.getOrDefault("gpa4", "0.00");
        List<Map<String, Object>> passed = (List<Map<String, Object>>) data.getOrDefault("passedCourses", List.of());
        List<Map<String, Object>> failed = (List<Map<String, Object>>) data.getOrDefault("failedCourses", List.of());
        List<Map<String, Object>> active = (List<Map<String, Object>>) data.getOrDefault("activeEnrollments", List.of());

        // Câu hỏi về tín chỉ tích lũy
        if (q.contains("tín chỉ") || q.contains("tin chi") || q.contains("tích lũy") || q.contains("tich luy") || q.contains("credit")) {
            int activeCredits = active.stream().mapToInt(a -> (int) a.getOrDefault("credits", 0)).sum();
            StringBuilder sb = new StringBuilder();
            sb.append("Chào **").append(fullName).append("**, theo dữ liệu thực tế từ hệ thống SIS:\n\n");
            sb.append("- **Tổng số tín chỉ đã tích lũy (đã đạt):** **").append(accumulatedCredits).append(" tín chỉ** (qua ").append(passed.size()).append(" môn học).\n");
            sb.append("- **Số tín chỉ đang theo học trong kỳ:** **").append(activeCredits).append(" tín chỉ** (").append(active.size()).append(" lớp học phần).\n");
            if (!failed.isEmpty()) {
                sb.append("- **Số môn chưa đạt:** ").append(failed.size()).append(" môn.\n");
            }
            return sb.toString();
        }

        // Câu hỏi về điểm số / GPA
        if (q.contains("điểm") || q.contains("diem") || q.contains("gpa") || q.contains("kết quả") || q.contains("ket qua") || q.contains("score")) {
            StringBuilder sb = new StringBuilder();
            sb.append("Tổng hợp kết quả học tập của **").append(fullName).append("**:\n\n");
            sb.append("- **Điểm trung bình tích lũy (GPA hệ 10):** **").append(gpa10).append(" / 10**\n");
            sb.append("- **Điểm trung bình tích lũy (GPA hệ 4):** **").append(gpa4).append(" / 4.0**\n\n");
            if (passed.isEmpty() && failed.isEmpty()) {
                sb.append("Hiện tại hệ thống **chưa có bảng điểm chính thức** nào được công bố cho bạn.");
            } else {
                sb.append("**Chi tiết điểm các môn đã công bố:**\n");
                for (Map<String, Object> p : passed) {
                    sb.append("- **").append(p.get("courseCode")).append(" - ").append(p.get("courseName"))
                            .append("**: Điểm **").append(p.get("finalScore")).append("** (").append(p.get("classification")).append(")\n");
                }
                for (Map<String, Object> f : failed) {
                    sb.append("- **[Chưa đạt]** ").append(f.get("courseCode")).append(" - ").append(f.get("courseName"))
                            .append(": Điểm **").append(f.get("finalScore")).append("** (").append(f.get("classification")).append(")\n");
                }
            }
            return sb.toString();
        }

        // Câu hỏi về môn học chưa hoàn thành / môn rớt / nợ môn
        if (q.contains("chưa hoàn thành") || q.contains("chua hoan thanh") || q.contains("nợ") || q.contains("rớt") || q.contains("rot") || q.contains("học lại")) {
            StringBuilder sb = new StringBuilder();
            if (failed.isEmpty() && active.isEmpty()) {
                sb.append("Hiện tại hệ thống chưa ghi nhận môn học nào bị rớt hoặc chưa hoàn thành đối với bạn.");
            } else {
                if (!failed.isEmpty()) {
                    sb.append("**Các môn chưa đạt (cần học lại / cải thiện):**\n");
                    for (Map<String, Object> f : failed) {
                        sb.append("- **").append(f.get("courseCode")).append(" - ").append(f.get("courseName"))
                                .append("** (").append(f.get("credits")).append(" TC) - Điểm: ").append(f.get("finalScore")).append("\n");
                    }
                    sb.append("\n");
                } else {
                    sb.append("Tuyệt vời! Bạn **không có môn học nào bị trượt/nợ điểm**.\n\n");
                }
                if (!active.isEmpty()) {
                    sb.append("**Các lớp học phần bạn đang theo học (chưa kết thúc):**\n");
                    for (Map<String, Object> a : active) {
                        sb.append("- ").append(a.get("sectionCode")).append(" - **").append(a.get("courseName")).append("**\n");
                    }
                }
            }
            return sb.toString();
        }

        // Câu hỏi về đăng ký học phần / thời khóa biểu / đang học môn gì
        if (q.contains("đăng ký") || q.contains("dang ky") || q.contains("môn nào") || q.contains("lớp") || q.contains("lịch") || q.contains("thời khóa biểu")) {
            StringBuilder sb = new StringBuilder();
            if (active.isEmpty()) {
                sb.append("Hiện tại bạn **chưa đăng ký lớp học phần nào** trong hệ thống.");
            } else {
                sb.append("**Danh sách các lớp học phần bạn đã đăng ký (").append(active.size()).append(" lớp):**\n\n");
                for (Map<String, Object> a : active) {
                    sb.append("- **").append(a.get("sectionCode")).append("** — ").append(a.get("courseName"))
                            .append(" (").append(a.get("credits")).append(" TC)\n")
                            .append("  + Giảng viên: ").append(a.get("lecturer")).append("\n")
                            .append("  + Lịch học: ").append(a.get("schedule")).append("\n");
                }
            }
            return sb.toString();
        }

        // Câu hỏi tổng quát hoặc lời chào
        return "Chào **" + fullName + "**! Tôi là **AI Study Assistant** của hệ thống SIS.\n\n"
                + "Tóm tắt tình hình học tập thực tế hiện tại của bạn:\n"
                + "- **Mã SV:** " + data.get("studentCode") + " | **Khoa:** " + data.get("department") + "\n"
                + "- **Tín chỉ tích lũy:** **" + accumulatedCredits + " tín chỉ**\n"
                + "- **GPA tích lũy:** **" + gpa10 + "/10** (" + gpa4 + "/4.0)\n"
                + "- **Số lớp đang theo học:** **" + active.size() + " lớp**\n\n"
                + "Bạn có thể hỏi cụ thể hơn như:\n"
                + "- *\"Tôi đã tích lũy bao nhiêu tín chỉ?\"*\n"
                + "- *\"Tôi đã đăng ký những môn nào?\"*\n"
                + "- *\"Điểm môn học gần đây của tôi thế nào?\"*\n"
                + "- *\"Tôi còn những môn nào chưa hoàn thành?\"*";
    }

    private List<String> detectSources(String question) {
        String q = question.toLowerCase();
        List<String> sources = new ArrayList<>();
        if (q.contains("điểm") || q.contains("gpa") || q.contains("tín chỉ") || q.contains("hoàn thành")) {
            sources.add("Academic Records & Grades");
        }
        if (q.contains("đăng ký") || q.contains("lớp") || q.contains("lịch") || q.contains("thời khóa biểu")) {
            sources.add("Course Enrollments");
        }
        if (sources.isEmpty()) {
            sources.add("Student SIS Profile");
        }
        return sources;
    }
}


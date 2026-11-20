
package com.example.student_management.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class AIProviderService {

    private static final int MAX_HISTORY_MESSAGES = 12;

    private static final int DEFAULT_MAX_TOKENS = 2048;
    private static final int MIN_MAX_TOKENS = 256;
    private static final int HARD_MAX_TOKENS = 4096;

    private static final int DEFAULT_MAX_LENGTH = 12000;
    private static final int MIN_MAX_LENGTH = 1000;
    private static final int HARD_MAX_LENGTH = 16000;

    private static final int DEFAULT_TIMEOUT_SECONDS = 25;
    private static final int MIN_TIMEOUT_SECONDS = 5;
    private static final int HARD_MAX_TIMEOUT_SECONDS = 120;

    @Value("${app.ai.provider:both}")
    private String provider;

    // Legacy OpenAI model
    @Value("${app.ai.model:gemini-2.0-flash}")
    private String model;

    // Direct Gemini
    @Value("${app.ai.gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${app.ai.gemini.api-key:}")
    private String geminiApiKey;

    // OpenAI
    @Value("${app.ai.openai.api-key:}")
    private String openaiApiKey;

    // OpenRouter
    @Value("${app.ai.openrouter.api-key:}")
    private String openRouterApiKey;

    @Value("${app.ai.openrouter.model:google/gemini-2.5-flash}")
    private String openRouterModel;

    @Value("${app.ai.openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openRouterBaseUrl;

    @Value("${app.ai.output.max-tokens:2048}")
    private int maxOutputTokens;

    @Value("${app.ai.output.max-length:12000}")
    private int maxOutputLength;

    @Value("${app.ai.request.timeout-seconds:25}")
    private int requestTimeoutSeconds;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final class ProviderAnswer {
        private final String content;
        private final String finishReason;

        private ProviderAnswer(String content, String finishReason) {
            this.content = content;
            this.finishReason = finishReason;
        }
    }

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
        private String finishReason;
        private Boolean truncated;
        private Boolean canContinue;
    }

    // Backward-compatible overload for existing callers.
    @SuppressWarnings("unchecked")
    public AIProviderResult generateChatResponse(
            String systemContext,
            List<Map<String, String>> conversationHistory,
            String userQuestion,
            Map<String, Object> academicSummary
    ) {
        Map<String, Object> safeSummary =
                academicSummary == null ? Map.of() : academicSummary;

        String fallback = generateLegacySisFallback(userQuestion, safeSummary);
        List<String> sources = detectSources(userQuestion, safeSummary);

        return generateChatResponse(
                systemContext,
                conversationHistory,
                userQuestion,
                fallback,
                sources
        );
    }

    public AIProviderResult generateChatResponse(
            String systemContext,
            List<Map<String, String>> conversationHistory,
            String userQuestion,
            String fallbackContent,
            List<String> sources
    ) {
        long start = System.currentTimeMillis();

        List<String> safeSources =
                sources == null || sources.isEmpty()
                        ? List.of("Student SIS Profile")
                        : sources;

        AIProviderResult result = generateFromConfiguredProviders(
                systemContext,
                conversationHistory,
                userQuestion,
                safeSources,
                start
        );

        if (result != null) {
            return result;
        }

        String content = fallbackContent == null || fallbackContent.isBlank()
                ? "Hiện tại trợ lý AI chưa thể phản hồi. Vui lòng thử lại sau."
                : fallbackContent;

        return AIProviderResult.builder()
                .content(content)
                .providerUsed("SIS-ASSISTANT-ENGINE")
                .modelUsed("sis-local-v1")
                .success(false)
                .latencyMs(System.currentTimeMillis() - start)
                .sources(safeSources)
                .finishReason("SIS_FALLBACK")
                .truncated(false)
                .canContinue(false)
                .build();
    }

    public AIProviderResult generateExternalAnalysis(
            String systemPrompt,
            String userPrompt
    ) {
        long start = System.currentTimeMillis();

        AIProviderResult result = generateFromConfiguredProviders(
                systemPrompt,
                List.of(),
                userPrompt,
                null,
                start
        );

        if (result != null) {
            return result;
        }

        return AIProviderResult.builder()
                .content(null)
                .providerUsed("SIS-ASSISTANT-ENGINE")
                .modelUsed("sis-local-v1")
                .success(false)
                .latencyMs(System.currentTimeMillis() - start)
                .finishReason("SIS_FALLBACK")
                .truncated(false)
                .canContinue(false)
                .build();
    }

    /**
     * Provider selection:
     *
     * both / auto:
     * OpenRouter -> Direct Gemini -> SIS fallback
     *
     * openrouter:
     * OpenRouter only
     *
     * gemini:
     * Direct Gemini only
     *
     * openai:
     * OpenAI only
     */
    private AIProviderResult generateFromConfiguredProviders(
            String systemContext,
            List<Map<String, String>> conversationHistory,
            String userQuestion,
            List<String> sources,
            long start
    ) {
        String mode = provider == null
                ? "both"
                : provider.trim().toLowerCase(Locale.ROOT);

        boolean useBoth = "both".equals(mode) || "auto".equals(mode);

        // 1. OpenRouter
        if (useBoth || "openrouter".equals(mode)) {
            if (hasKey(openRouterApiKey)) {
                try {
                    ProviderAnswer answer = callOpenRouterApi(
                            systemContext,
                            conversationHistory,
                            userQuestion
                    );

                    if (answer != null && answer.content != null && !answer.content.isBlank()) {
                        return buildSuccessResult(
                                answer,
                                "OPENROUTER",
                                openRouterModel,
                                sources,
                                start
                        );
                    }

                    log.warn("OpenRouter returned an empty response");

                } catch (Exception ex) {
                    log.warn(
                            "OpenRouter request failed: type={}",
                            ex.getClass().getSimpleName()
                    );
                }
            } else {
                log.warn("OpenRouter API key is not configured");
            }
        }

        // 2. Direct Gemini fallback
        if (useBoth || "gemini".equals(mode)) {
            if (hasKey(geminiApiKey)) {
                try {
                    ProviderAnswer answer = callGeminiApi(
                            systemContext,
                            conversationHistory,
                            userQuestion
                    );

                    if (answer != null && answer.content != null && !answer.content.isBlank()) {
                        return buildSuccessResult(
                                answer,
                                "GEMINI",
                                geminiModel,
                                sources,
                                start
                        );
                    }

                    log.warn("Gemini returned an empty response");

                } catch (Exception ex) {
                    log.warn(
                            "Gemini request failed: type={}",
                            ex.getClass().getSimpleName()
                    );
                }
            } else if (useBoth) {
                log.warn(
                        "Direct Gemini API key is not configured; fallback unavailable"
                );
            }
        }

        // 3. Legacy OpenAI mode
        if ("openai".equals(mode)) {
            if (hasKey(openaiApiKey)) {
                try {
                    ProviderAnswer answer = callOpenAiApi(
                            systemContext,
                            conversationHistory,
                            userQuestion
                    );

                    if (answer != null && answer.content != null && !answer.content.isBlank()) {
                        return buildSuccessResult(
                                answer,
                                "OPENAI",
                                model,
                                sources,
                                start
                        );
                    }

                    log.warn("OpenAI returned an empty response");

                } catch (Exception ex) {
                    log.warn(
                            "OpenAI request failed: type={}",
                            ex.getClass().getSimpleName()
                    );
                }
            } else {
                log.warn("OpenAI API key is not configured");
            }
        }

        if (!List.of(
                "both",
                "auto",
                "openrouter",
                "gemini",
                "openai"
        ).contains(mode)) {
            log.warn("Unsupported AI provider mode: {}", mode);
        }

        return null;
    }

    private boolean hasKey(String key) {
        return key != null && !key.isBlank();
    }

    private AIProviderResult buildSuccessResult(
            ProviderAnswer answer,
            String providerUsed,
            String modelUsed,
            List<String> sources,
            long start
    ) {
        String original = answer.content == null ? "" : answer.content.trim();
        String limited = limitOutput(answer.content);
        boolean cutByBackend = limited.length() < original.length();
        String finishReason = normalizeFinishReason(answer.finishReason);
        if (cutByBackend) {
            finishReason = "BACKEND_LIMIT";
        }
        boolean truncated = "LENGTH".equals(finishReason) || cutByBackend;

        AIProviderResult.AIProviderResultBuilder builder =
                AIProviderResult.builder()
                        .content(limited)
                        .providerUsed(providerUsed)
                        .modelUsed(modelUsed)
                        .success(true)
                        .latencyMs(System.currentTimeMillis() - start)
                        .finishReason(finishReason)
                        .truncated(truncated)
                        .canContinue(truncated);

        if (sources != null) {
            builder.sources(sources);
        }

        return builder.build();
    }

    static String extractFinishReason(JsonNode root, String provider) {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        if ("GEMINI".equalsIgnoreCase(provider)) {
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode candidate = candidates.get(0);
                JsonNode reason = candidate.path("finishReason");
                if (!reason.isMissingNode() && reason.isTextual()) {
                    return reason.asText();
                }
                JsonNode stopReason = candidate.path("stopReason");
                if (!stopReason.isMissingNode() && stopReason.isTextual()) {
                    return stopReason.asText();
                }
            }
            return null;
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode reason = choices.get(0).path("finish_reason");
            if (!reason.isMissingNode() && reason.isTextual()) {
                return reason.asText();
            }
        }
        return null;
    }

    static String normalizeFinishReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.contains("LENGTH") || value.contains("MAX_TOKENS")
                || value.contains("TOKEN_LIMIT") || value.contains("MODEL_LENGTH")) {
            return "LENGTH";
        }
        if (value.contains("SAFETY") || value.contains("RECITATION")
                || value.contains("CONTENT_FILTER")) {
            return "SAFETY";
        }
        if (value.contains("STOP") || value.contains("END_TURN")
                || value.contains("EOS")) {
            return "STOP";
        }
        if (value.contains("TOOL") || value.contains("FUNCTION")) {
            return "TOOL";
        }
        return "UNKNOWN";
    }

    static int boundedInt(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    private int currentMaxTokens() {
        return boundedInt(maxOutputTokens, MIN_MAX_TOKENS, HARD_MAX_TOKENS, DEFAULT_MAX_TOKENS);
    }

    private int currentMaxLength() {
        return boundedInt(maxOutputLength, MIN_MAX_LENGTH, HARD_MAX_LENGTH, DEFAULT_MAX_LENGTH);
    }

    private Duration currentTimeout() {
        int seconds = boundedInt(requestTimeoutSeconds, MIN_TIMEOUT_SECONDS,
                HARD_MAX_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
        return Duration.ofSeconds(seconds);
    }

    private List<Map<String, String>> buildOpenAiMessages(
            String systemContext,
            List<Map<String, String>> history,
            String userQuestion
    ) {
        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", systemContext == null ? "" : systemContext
        ));

        if (history != null) {
            int startIdx = Math.max(
                    0,
                    history.size() - MAX_HISTORY_MESSAGES
            );

            for (int i = startIdx; i < history.size(); i++) {
                Map<String, String> msg = history.get(i);

                if (msg == null) {
                    continue;
                }

                String role = msg.getOrDefault("role", "user");
                String content = msg.getOrDefault("content", "");

                if (content == null || content.isBlank()) {
                    continue;
                }

                String normalizedRole =
                        "assistant".equalsIgnoreCase(role)
                                ? "assistant"
                                : "user";

                messages.add(Map.of(
                        "role", normalizedRole,
                        "content", content
                ));
            }
        }

        messages.add(Map.of(
                "role", "user",
                "content", userQuestion == null ? "" : userQuestion
        ));

        return messages;
    }

    /**
     * OpenRouter uses an OpenAI-compatible Chat Completions API.
     */
    private ProviderAnswer callOpenRouterApi(
            String systemContext,
            List<Map<String, String>> history,
            String userQuestion
    ) throws Exception {

        String baseUrl = openRouterBaseUrl == null
                ? "https://openrouter.ai/api/v1"
                : openRouterBaseUrl.trim();

        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String url = baseUrl + "/chat/completions";

        List<Map<String, String>> messages = buildOpenAiMessages(
                systemContext,
                history,
                userQuestion
        );

        Map<String, Object> body = Map.of(
                "model", openRouterModel,
                "messages", messages,
                "temperature", 0.3,
                "max_tokens", currentMaxTokens()
        );

        String jsonBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header(
                        "Authorization",
                        "Bearer " + openRouterApiKey.trim()
                )
                .timeout(currentTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() >= 200
                && response.statusCode() < 300) {

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");

            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode content = choices.get(0)
                        .path("message")
                        .path("content");

                if (content.isTextual()) {
                    return new ProviderAnswer(
                            content.asText(),
                            extractFinishReason(root, "OPENROUTER")
                    );
                }
            }
        }

        throw new RuntimeException(
                "OpenRouter API returned HTTP status "
                        + response.statusCode()
        );
    }

    private ProviderAnswer callGeminiApi(
            String systemContext,
            List<Map<String, String>> history,
            String userQuestion
    ) throws Exception {

        String url =
                "https://generativelanguage.googleapis.com/v1beta/models/"
                        + geminiModel
                        + ":generateContent";

        Map<String, Object> body = new LinkedHashMap<>();

        body.put(
                "systemInstruction",
                Map.of(
                        "parts",
                        List.of(
                                Map.of(
                                        "text",
                                        systemContext == null ? "" : systemContext
                                )
                        )
                )
        );

        List<Map<String, Object>> contents = new ArrayList<>();

        if (history != null) {
            int startIdx = Math.max(
                    0,
                    history.size() - MAX_HISTORY_MESSAGES
            );

            for (int i = startIdx; i < history.size(); i++) {
                Map<String, String> msg = history.get(i);

                if (msg == null) {
                    continue;
                }

                String role = msg.getOrDefault("role", "user");

                String geminiRole =
                        "assistant".equalsIgnoreCase(role)
                                ? "model"
                                : "user";

                String content = msg.getOrDefault("content", "");

                if (content == null || content.isBlank()) {
                    continue;
                }

                contents.add(Map.of(
                        "role", geminiRole,
                        "parts", List.of(Map.of("text", content))
                ));
            }
        }

        contents.add(Map.of(
                "role", "user",
                "parts",
                List.of(
                        Map.of(
                                "text",
                                userQuestion == null ? "" : userQuestion
                        )
                )
        ));

        body.put("contents", contents);

        body.put(
                "generationConfig",
                Map.of(
                        "temperature", 0.3,
                        "maxOutputTokens", currentMaxTokens()
                )
        );

        String jsonBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header(
                        "x-goog-api-key",
                        geminiApiKey.trim()
                )
                .timeout(currentTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() >= 200
                && response.statusCode() < 300) {

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");

            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0)
                        .path("content")
                        .path("parts");

                if (parts.isArray() && !parts.isEmpty()) {
                    String text = extractGeminiTextParts(parts);
                    if (!text.isBlank()) {
                        return new ProviderAnswer(
                                text,
                                extractFinishReason(root, "GEMINI")
                        );
                    }
                }
            }
        }

        throw new RuntimeException(
                "Gemini API returned HTTP status "
                        + response.statusCode()
        );
    }

    static String extractGeminiTextParts(JsonNode parts) {
        if (parts == null || !parts.isArray()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode textPart = part.path("text");
            if (textPart.isTextual()) {
                text.append(textPart.asText());
            }
        }
        return text.toString();
    }

    private ProviderAnswer callOpenAiApi(
            String systemContext,
            List<Map<String, String>> history,
            String userQuestion
    ) throws Exception {

        String url = "https://api.openai.com/v1/chat/completions";

        List<Map<String, String>> messages = buildOpenAiMessages(
                systemContext,
                history,
                userQuestion
        );

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.3,
                "max_tokens", currentMaxTokens()
        );

        String jsonBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header(
                        "Authorization",
                        "Bearer " + openaiApiKey.trim()
                )
                .timeout(currentTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() >= 200
                && response.statusCode() < 300) {

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");

            if (choices.isArray() && !choices.isEmpty()) {
                return new ProviderAnswer(
                        choices.get(0)
                                .path("message")
                                .path("content")
                                .asText(),
                        extractFinishReason(root, "OPENAI")
                );
            }
        }

        throw new RuntimeException(
                "OpenAI API returned HTTP status "
                        + response.statusCode()
        );
    }

    private String limitOutput(String answer) {
        if (answer == null) {
            return null;
        }

        String trimmed = answer.trim();
        int limit = currentMaxLength();

        return trimmed.length() > limit
                ? trimmed.substring(0, limit)
                : trimmed;
    }

    @SuppressWarnings("unchecked")
    private String generateLegacySisFallback(
            String question,
            Map<String, Object> data
    ) {
        String q = question == null
                ? ""
                : question.toLowerCase(Locale.ROOT);

        String fullName = String.valueOf(
                data.getOrDefault("fullName", "bạn")
        );

        int accumulatedCredits = toInt(data.get("accumulatedCredits"));

        Object gpa10 = data.getOrDefault("gpa10", "0.00");
        Object gpa4 = data.getOrDefault("gpa4", "0.00");

        List<Map<String, Object>> passed =
                (List<Map<String, Object>>) data.getOrDefault(
                        "passedCourses", List.of()
                );

        List<Map<String, Object>> failed =
                (List<Map<String, Object>>) data.getOrDefault(
                        "failedCourses", List.of()
                );

        List<Map<String, Object>> active =
                (List<Map<String, Object>>) data.getOrDefault(
                        "activeEnrollments", List.of()
                );

        if (q.contains("tín chỉ")
                || q.contains("tin chi")
                || q.contains("tích lũy")
                || q.contains("tich luy")
                || q.contains("credit")) {

            int activeCredits = active.stream()
                    .mapToInt(a -> toInt(a.get("credits")))
                    .sum();

            return "Chào **" + fullName + "**,\n\n"
                    + "- **Tín chỉ tích lũy:** **"
                    + accumulatedCredits + " TC**\n"
                    + "- **Tín chỉ đang học:** **"
                    + activeCredits + " TC** ("
                    + active.size() + " lớp)\n"
                    + "- **Môn chưa đạt:** " + failed.size();
        }

        if (q.contains("điểm")
                || q.contains("diem")
                || q.contains("gpa")
                || q.contains("kết quả")
                || q.contains("ket qua")) {

            StringBuilder sb = new StringBuilder();

            sb.append("Tổng hợp kết quả học tập của **")
                    .append(fullName)
                    .append("**:\n- GPA hệ 10: **")
                    .append(gpa10)
                    .append("**\n- GPA hệ 4: **")
                    .append(gpa4)
                    .append("**\n");

            for (Map<String, Object> p : passed) {
                sb.append("- ")
                        .append(p.get("courseCode"))
                        .append(" - ")
                        .append(p.get("courseName"))
                        .append(": ")
                        .append(p.get("finalScore"))
                        .append("\n");
            }

            for (Map<String, Object> f : failed) {
                sb.append("- [Chưa đạt] ")
                        .append(f.get("courseCode"))
                        .append(" - ")
                        .append(f.get("courseName"))
                        .append(": ")
                        .append(f.get("finalScore"))
                        .append("\n");
            }

            if (passed.isEmpty() && failed.isEmpty()) {
                sb.append("- Hiện hệ thống chưa có dữ liệu điểm công bố.\n");
            }

            return sb.toString();
        }

        if (q.contains("chưa hoàn thành")
                || q.contains("chua hoan thanh")
                || q.contains("nợ")
                || q.contains("rớt")
                || q.contains("rot")
                || q.contains("học lại")) {

            StringBuilder sb = new StringBuilder();

            if (failed.isEmpty()) {
                sb.append(
                        "Hiện hệ thống chưa ghi nhận môn chưa đạt "
                                + "theo dữ liệu điểm công bố."
                );
            } else {
                sb.append("**Môn chưa đạt/cần cải thiện:**\n");

                for (Map<String, Object> f : failed) {
                    sb.append("- ")
                            .append(f.get("courseCode"))
                            .append(" - ")
                            .append(f.get("courseName"))
                            .append(" (")
                            .append(f.get("credits"))
                            .append(" TC): ")
                            .append(f.get("finalScore"))
                            .append("\n");
                }
            }

            return sb.toString();
        }

        if (q.contains("đăng ký")
                || q.contains("dang ky")
                || q.contains("lớp")
                || q.contains("lop")
                || q.contains("lịch")
                || q.contains("lich")
                || q.contains("thời khóa biểu")) {

            StringBuilder sb = new StringBuilder();

            sb.append("**Lớp học phần đang học:** ")
                    .append(active.size())
                    .append("\n");

            for (Map<String, Object> a : active) {
                sb.append("- ")
                        .append(a.get("sectionCode"))
                        .append(" - ")
                        .append(a.get("courseName"))
                        .append(" (")
                        .append(a.get("credits"))
                        .append(" TC), lịch: ")
                        .append(a.get("schedule"))
                        .append("\n");
            }

            if (active.isEmpty()) {
                sb.append(
                        "- Hiện bạn chưa có lớp học phần ENROLLED "
                                + "trong hệ thống.\n"
                );
            }

            return sb.toString();
        }

        return "Chào **" + fullName
                + "**. GPA hiện tại **" + gpa10
                + "/10** (" + gpa4
                + "/4.0), tín chỉ tích lũy **"
                + accumulatedCredits
                + " TC**, đang học **" + active.size()
                + " lớp**. Bạn có thể hỏi về GPA, tín chỉ, điểm môn, "
                + "môn chưa đạt, hoặc lớp đang đăng ký.";
    }

    private List<String> detectSources(
            String question,
            Map<String, Object> data
    ) {
        String q = question == null
                ? ""
                : question.toLowerCase(Locale.ROOT);

        List<String> sources = new ArrayList<>();

        if (q.contains("điểm")
                || q.contains("diem")
                || q.contains("gpa")
                || q.contains("tín chỉ")
                || q.contains("tin chi")
                || q.contains("hoàn thành")
                || data.containsKey("gpa10")) {

            sources.add("Published Grades & Academic Records");
        }

        if (q.contains("đăng ký")
                || q.contains("dang ky")
                || q.contains("lớp")
                || q.contains("lop")
                || q.contains("lịch")
                || q.contains("lich")
                || data.containsKey("activeEnrollments")) {

            sources.add("Enrollment Records");
        }

        if (sources.isEmpty()) {
            sources.add("Student SIS Profile");
        }

        return sources;
    }

    private int toInt(Object o) {
        if (o instanceof Integer i) {
            return i;
        }

        if (o instanceof Long l) {
            return l.intValue();
        }

        if (o instanceof Number n) {
            return n.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
}

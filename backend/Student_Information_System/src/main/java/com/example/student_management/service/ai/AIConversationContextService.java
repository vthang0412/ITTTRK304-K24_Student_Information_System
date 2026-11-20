package com.example.student_management.service.ai;

import com.example.student_management.entity.ai.AIMessage;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tái sử dụng lịch sử hội thoại hiện có (AIMessage) để xác định intent/chủ đề
 * của lượt trước, follow-up và các slot cần thiết. Không đọc dữ liệu SIS và
 * không truy cập hội thoại của người khác; caller đã kiểm tra ownership.
 */
public class AIConversationContextService {

    private static final Pattern GPA_TARGET = Pattern.compile(
            "(?:gpa|dtb|diem\\s*trung\\s*binh).{0,30}?([0-4](?:[.,]\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREDIT_COUNT = Pattern.compile(
            "(\\d{1,2})\\s*(?:tc|tin\\s*chi|tich\\s*luy)", Pattern.CASE_INSENSITIVE);
    private static final int HISTORY_WINDOW = 8;

    public static class Context {
        private final AIIntentService.PriorContext prior;
        private final boolean followUp;
        private final boolean topicSwitch;
        private final String topic;
        private final String lastUserQuestion;

        public Context(AIIntentService.PriorContext prior, boolean followUp, boolean topicSwitch,
                       String topic, String lastUserQuestion) {
            this.prior = prior;
            this.followUp = followUp;
            this.topicSwitch = topicSwitch;
            this.topic = topic;
            this.lastUserQuestion = lastUserQuestion;
        }

        public AIIntentService.PriorContext getPrior() { return prior; }
        public boolean isFollowUp() { return followUp; }
        public boolean isTopicSwitch() { return topicSwitch; }
        public String getTopic() { return topic; }
        public String getLastUserQuestion() { return lastUserQuestion; }
    }

    private final AIIntentService intentService = new AIIntentService();

    public Context resolve(List<AIMessage> historyExcludingCurrent, String currentQuestion) {
        List<AIMessage> safeHistory = historyExcludingCurrent == null ? List.of() : historyExcludingCurrent;
        int start = Math.max(0, safeHistory.size() - HISTORY_WINDOW);
        List<AIMessage> recent = safeHistory.subList(start, safeHistory.size());

        String lastUserQuestion = "";
        String lastAssistantAnswer = "";
        for (AIMessage m : recent) {
            if ("USER".equalsIgnoreCase(m.getRole())) lastUserQuestion = AIInputSafety.sanitizeData(m.getContent());
            if ("ASSISTANT".equalsIgnoreCase(m.getRole())) lastAssistantAnswer = AIInputSafety.sanitizeData(m.getContent());
        }

        AIIntentService.Intent lastIntent = lastUserQuestion.isBlank()
                ? null : intentService.detectIntent(lastUserQuestion, false);
        String topic = deriveTopic(lastIntent, lastUserQuestion, lastAssistantAnswer);
        boolean followUp = intentService.isFollowUpPhrase(currentQuestion);
        boolean topicSwitch = intentService.isTopicSwitch(currentQuestion);
        Map<String, String> slots = collectSlots(recent, lastUserQuestion);

        AIIntentService.PriorContext prior = lastIntent == null
                ? null : new AIIntentService.PriorContext(lastIntent, topic, slots);
        return new Context(prior, followUp, topicSwitch, topic, lastUserQuestion);
    }

    private String deriveTopic(AIIntentService.Intent intent, String lastUserQuestion, String lastAssistantAnswer) {
        if (lastUserQuestion.isBlank()) return "";
        String topic = lastUserQuestion;
        if (intent == AIIntentService.Intent.TUTOR_EXPLANATION
                || intent == AIIntentService.Intent.COMBINED_SIS_TUTOR
                || intent == AIIntentService.Intent.GENERAL_KNOWLEDGE) {
            topic = lastUserQuestion.replaceFirst("(?i)^(giai thich|cho toi|hay giai thich|minh hoa|huong dan)\\s+", "");
        }
        topic = topic.replaceAll("[?！!]+$", "").trim();
        if (topic.isBlank()) topic = lastAssistantAnswer;
        if (topic.length() > 90) topic = topic.substring(0, 87) + "...";
        return AIInputSafety.sanitizeData(topic);
    }

    private Map<String, String> collectSlots(List<AIMessage> recent, String lastUserQuestion) {
        Map<String, String> slots = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();
        for (AIMessage m : recent) {
            if ("USER".equalsIgnoreCase(m.getRole())) text.append(AIInputSafety.sanitizeData(m.getContent())).append(' ');
        }
        String all = text.toString();
        Matcher gpaMatcher = GPA_TARGET.matcher(all);
        Map<String, String> found = new HashMap<>();
        while (gpaMatcher.find()) found.put("targetGpa", gpaMatcher.group(1).replace(',', '.'));
        Matcher creditMatcher = CREDIT_COUNT.matcher(all);
        while (creditMatcher.find()) found.put("newCredits", creditMatcher.group(1));
        if (GPA_TARGET.matcher(lastUserQuestion).find()) {
            Matcher m = GPA_TARGET.matcher(lastUserQuestion);
            if (m.find()) found.put("targetGpa", m.group(1).replace(',', '.'));
        }
        slots.putAll(found);
        return slots;
    }
}

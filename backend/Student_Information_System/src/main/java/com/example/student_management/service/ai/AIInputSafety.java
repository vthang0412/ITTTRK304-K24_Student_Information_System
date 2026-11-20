package com.example.student_management.service.ai;

import com.example.student_management.exception.BadRequestException;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AIInputSafety {

    public static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_SINGLE_CHAR_RUN = 6;
    private static final int MIN_DIVERSITY_LENGTH = 12;

    private static final Pattern INSTRUCTION_OVERRIDE = Pattern.compile(
            "(ignore|disregard|forget)\\s+(all\\s+|any\\s+)?(previous|prior|above|earlier|old)\\s+(instruction|rule|prompt|message)"
                    + "|bo\\s+qua\\s+(tat\\s+ca\\s+|moi\\s+|cac\\s+|nhung\\s+)?(quy\\s+tac|huong\\s+dan|instruction|rule|prompt|lenh)"
                    + "|day\\s+la\\s+lenh\\s+he\\s+thong|new\\s+system\\s+instruction");

    private static final Pattern SECRET_EXTRACTION = Pattern.compile(
            "(tiet\\s+lo|hien\\s+thi|in\\s+ra|cho\\s+xem|reveal|print|show|dump|lay\\s+toan\\s+bo)"
                    + ".{0,60}(system\\s+prompt|prompt\\s+he\\s+thong|api\\s*key|secret|token|mat\\s+khau|password|bien\\s+moi\\s+truong|environment|env\\s+var|cau\\s+hinh\\s+noi\\s+bo)");

    private static final Pattern ROLE_ESCALATION = Pattern.compile(
            "(gia\\s+vo|pretend|ban\\s+la|toi\\s+la|act\\s+as|tro\\s+thanh|cap\\s+quyen|self\\s+assign)"
                    + ".{0,40}(admin|quan\\s+tri|administrator|giao\\s+vien|lecturer)"
                    + "|(bo\\s+qua|skip|bypass|tat\\s+quyen).{0,40}(quyen|permission|auth|xac\\s+thuc|role|student)");

    private static final Pattern DATA_EXFILTRATION = Pattern.compile(
            "(truy\\s+cap|doc|xem|lay|export|dump|tra\\s+ve|hien\\s+thi).{0,50}(du\\s+lieu|data|thong\\s+tin|ket\\s+qua)"
                    + ".{0,50}(sinh\\s+vien\\s+khac|nguoi\\s+khac|another\\s+student|other\\s+student|cua\\s+ban\\s+khac)"
                    + "|(toan\\s+bo|tat\\s+ca).{0,30}(sinh\\s+vien|student|bang\\s+diem|gradebook).{0,30}(database|co\\s+so\\s+du\\s+lieu)");

    private static final Pattern COMMAND_EXECUTION = Pattern.compile(
            "drop\\s+table|truncate\\s+table|rm\\s+-rf|chmod\\s|shutdown\\s+now"
                    + "|(thuc\\s+hien|execute|run|thuc\\s+thi|goi\\s+lenh).{0,40}(delete|drop|truncate|update|insert|shell|command|sql)");

    private static final Pattern TOOL_MISUSE = Pattern.compile(
            "(goi|call|su\\s+dung|use|chay).{0,30}(tat\\s+ca|all|moi).{0,20}(tool|cong\\s+cu|function|api|endpoint)");

    private static final Pattern SECRET_IN_OUTPUT = Pattern.compile(
            "(?i)(AIza[0-9A-Za-z_-]{20,}|sk-[A-Za-z0-9]{20,}|ghp_[A-Za-z0-9]{20,}"
                    + "|(api[_-]?key|secret|password|token|jwt[_-]?secret|db[_-]?password|x-goog-api-key)\\s*[:=]\\s*['\"]?[A-Za-z0-9_\\-]{12,})");

    private static final List<String> SYSTEM_PROMPT_MARKERS = List.of(
            "=== VAI TRÒ ===", "=== QUY TẮC BẮT BUỘC ===", "=== DỮ LIỆU SIS",
            "=== HƯỚNG DẪN AI TUTOR ===", "=== NGỮ CẢNH HỘI THOẠI", "systemInstruction");

    private AIInputSafety() {
    }

    public static String validateQuestion(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Nội dung câu hỏi không được để trống");
        }
        String question = raw.trim().replaceAll("[ \\t\\u00A0]{2,}", " ");
        if (question.isBlank()) {
            throw new BadRequestException("Nội dung câu hỏi không được để trống");
        }
        if (question.length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException("Câu hỏi tối đa " + MAX_MESSAGE_LENGTH + " ký tự. Vui lòng rút gọn và gửi lại.");
        }
        if (!hasMeaningfulContent(question)) {
            throw new BadRequestException("Câu hỏi chưa có nội dung hợp lệ. Vui lòng nhập câu hỏi bằng chữ hoặc số.");
        }
        if (isAbusiveInput(question)) {
            throw new BadRequestException("Nội dung có dấu hiệu spam. Vui lòng diễn đạt câu hỏi tự nhiên hơn.");
        }
        if (isInjectionAttempt(question)) {
            throw new BadRequestException("Yêu cầu nằm ngoài phạm vi trợ lý học tập. Mình chỉ hỗ trợ câu hỏi học tập và dữ liệu học vụ của bạn.");
        }
        return question;
    }

    public static boolean isInjectionAttempt(String text) {
        String normalized = normalize(text);
        return INSTRUCTION_OVERRIDE.matcher(normalized).find()
                || SECRET_EXTRACTION.matcher(normalized).find()
                || ROLE_ESCALATION.matcher(normalized).find()
                || DATA_EXFILTRATION.matcher(normalized).find()
                || COMMAND_EXECUTION.matcher(normalized).find()
                || TOOL_MISUSE.matcher(normalized).find();
    }

    public static boolean isAbusiveInput(String text) {
        if (text == null || text.isBlank()) return true;
        String compact = text.replaceAll("\\s", "");
        if (compact.isEmpty()) return true;
        if (!hasMeaningfulContent(compact)) return true;

        int bestRun = 1;
        int currentRun = 1;
        int previous = -1;
        for (int i = 0; i < compact.length(); i++) {
            int cp = compact.codePointAt(i);
            if (Character.isSupplementaryCodePoint(cp)) i++;
            currentRun = cp == previous ? currentRun + 1 : 1;
            previous = cp;
            bestRun = Math.max(bestRun, currentRun);
        }
        if (bestRun >= MAX_SINGLE_CHAR_RUN) return true;

        if (compact.length() >= MIN_DIVERSITY_LENGTH) {
            String lettersDigits = normalize(compact).replaceAll("[^a-z0-9]", "");
            long distinct = lettersDigits.chars().distinct().count();
            if (lettersDigits.length() >= MIN_DIVERSITY_LENGTH && distinct <= 2) return true;
        }
        return false;
    }

    public static boolean isSensitiveOutput(String text) {
        if (text == null || text.isBlank()) return false;
        if (SECRET_IN_OUTPUT.matcher(text).find()) return true;
        for (String marker : SYSTEM_PROMPT_MARKERS) {
            if (text.contains(marker)) return true;
        }
        return false;
    }

    public static String safeAnswer(String answer, String fallback) {
        if (isSensitiveOutput(answer)) return fallback;
        return answer;
    }

    public static String sanitizeData(String text) {
        if (text == null) return "";
        return text.replace("<", "").replace(">", "").replaceAll("\\s+", " ").trim();
    }

    private static boolean hasMeaningfulContent(String text) {
        if (text == null) return false;
        return text.codePoints().anyMatch(cp -> Character.isLetterOrDigit(cp));
    }

    private static String normalize(String text) {
        if (text == null) return "";
        String value = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace("đ", "d");
        return value.replaceAll("\\s+", " ");
    }
}

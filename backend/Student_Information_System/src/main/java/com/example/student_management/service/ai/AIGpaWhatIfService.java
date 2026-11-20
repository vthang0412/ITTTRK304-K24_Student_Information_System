package com.example.student_management.service.ai;

import com.example.student_management.dto.grade.StudentGradeDto;
import com.example.student_management.entity.auth.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GPA what-if được tính hoàn toàn bằng backend; không ghi đè dữ liệu điểm thật. */
public class AIGpaWhatIfService {

    private static final Pattern GPA_TARGET = Pattern.compile(
            "(?:gpa|dtb|diem\\s*trung\\s*binh).{0,30}?([0-4](?:[.,]\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREDIT_COUNT = Pattern.compile(
            "(\\d{1,2})\\s*(?:tc|tin\\s*chi|tich\\s*luy)", Pattern.CASE_INSENSITIVE);

    public static class WhatIfResult {
        private final boolean complete;
        private final String answer;
        private final String missingField;

        private WhatIfResult(boolean complete, String answer, String missingField) {
            this.complete = complete;
            this.answer = answer;
            this.missingField = missingField;
        }

        public static WhatIfResult ask(String question) {
            return new WhatIfResult(false, question, question);
        }

        public static WhatIfResult done(String answer) {
            return new WhatIfResult(true, answer, null);
        }

        public boolean isComplete() { return complete; }
        public String getAnswer() { return answer; }
        public String getMissingField() { return missingField; }
    }

    public WhatIfResult simulate(User user, String question, Map<String, String> slots,
                                 AIChatToolsService tools) {
        BigDecimal targetGpa = extractGpa(question, slots);
        Integer newCredits = extractCredits(question, slots);
        if (targetGpa == null) {
            return WhatIfResult.ask("Bạn muốn giả định GPA học kỳ tới là bao nhiêu (thang 4.0)?");
        }
        if (newCredits == null) {
            return WhatIfResult.ask("Bạn dự kiến học bao nhiêu tín chỉ trong học kỳ tới?");
        }
        if (newCredits <= 0 || newCredits > 60) {
            return WhatIfResult.done("Số tín chỉ giả định phải nằm trong khoảng 1 đến 60. Vui lòng cung cấp số hợp lệ để mình mô phỏng.");
        }

        List<StudentGradeDto> grades = tools.getPublishedGrades(user);
        Map<String, Object> summary = tools.computeGpaAndCredits(grades);
        BigDecimal currentGpa = toDecimal(summary.get("gpa4"));
        int gradedCredits = 0;
        for (StudentGradeDto g : grades) {
            if (g.getFinalScore() != null && g.getCredits() != null && g.getCredits() > 0) {
                gradedCredits += g.getCredits();
            }
        }
        if (grades.isEmpty() || gradedCredits == 0) {
            return WhatIfResult.done("Hiện hệ thống chưa có điểm đã công bố để xác định GPA tích lũy. Mình chưa thể mô phỏng chính xác; vui lòng kiểm tra lại sau khi có điểm.");
        }

        BigDecimal numerator = currentGpa.multiply(BigDecimal.valueOf(gradedCredits))
                .add(targetGpa.multiply(BigDecimal.valueOf(newCredits)));
        BigDecimal denominator = BigDecimal.valueOf(gradedCredits + newCredits);
        BigDecimal projected = numerator.divide(denominator, 2, RoundingMode.HALF_UP);
        BigDecimal delta = projected.subtract(currentGpa).setScale(2, RoundingMode.HALF_UP);

        List<String> warnings = buildWarnings(grades);
        StringBuilder sb = new StringBuilder();
        sb.append("### Mô phỏng GPA tích lũy (thang 4.0)\n\n");
        sb.append("| Hạng mục | Giá trị | Nguồn |\n|---|---|---|\n");
        sb.append("| GPA hiện tại | **").append(currentGpa.toPlainString()).append("** | Dữ liệu thực tế SIS (điểm đã công bố) |\n");
        sb.append("| Tín chỉ đã có điểm | ").append(gradedCredits).append(" TC | Dữ liệu thực tế SIS |\n");
        sb.append("| GPA học kỳ giả định | ").append(targetGpa.toPlainString()).append(" | Giả định của bạn |\n");
        sb.append("| Tín chỉ kỳ mới giả định | ").append(newCredits).append(" TC | Giả định của bạn |\n");
        sb.append("| **GPA tích lũy dự kiến** | **").append(projected.toPlainString()).append("** | Kết quả mô phỏng |\n");
        sb.append("| Mức tăng/giảm | ").append(delta.toPlainString()).append(" | Kết quả mô phỏng |\n\n");
        sb.append("**Công thức:** (").append(currentGpa.toPlainString()).append(" × ").append(gradedCredits)
                .append(" + ").append(targetGpa.toPlainString()).append(" × ").append(newCredits)
                .append(") / (").append(gradedCredits).append(" + ").append(newCredits).append(") = ")
                .append(projected.toPlainString()).append("\n\n");
        sb.append("**Giả định:** hệ thống hiện tính GPA hệ 4 bằng điểm trung bình có trọng số theo *tín chỉ đã có điểm*; ");
        sb.append("mọi tín chỉ giả định đều được tính vào mẫu số. Số liệu trên là mô phỏng, **không phải GPA thật** và không được ghi vào database.\n\n");
        if (warnings.isEmpty()) {
            sb.append("**Cảnh báo:** không phát hiện môn trượt hoặc bản ghi trùng môn trong điểm đã công bố. ");
            sb.append("Hệ thống hiện chưa có quy tắc thay thế điểm học lại được cấu hình.\n");
        } else {
            sb.append("**Cảnh báo:**\n");
            for (String warning : warnings) sb.append("- ").append(warning).append("\n");
        }
        return WhatIfResult.done(sb.toString());
    }

    private List<String> buildWarnings(List<StudentGradeDto> grades) {
        List<String> warnings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        int failed = 0;
        int missingScore = 0;
        for (StudentGradeDto g : grades) {
            String code = g.getCourseCode() == null ? "N/A" : g.getCourseCode();
            if (!seen.add(code)) duplicates.add(code);
            boolean passed = Boolean.TRUE.equals(g.getPassed())
                    || (g.getFinalScore() != null && g.getFinalScore().compareTo(new BigDecimal("4.00")) >= 0);
            if (!passed) failed++;
            if (g.getFinalScore() == null) missingScore++;
        }
        if (failed > 0) warnings.add("Có " + failed + " học phần chưa đạt trong dữ liệu công bố; GPA hiện tại đã tính cả các học phần này theo chính sách của hệ thống.");
        if (!duplicates.isEmpty()) warnings.add("Có bản ghi trùng mã môn (" + String.join(", ", duplicates) + "). Hệ thống chưa có quy tắc thay thế điểm học lại nên mô phỏng giữ nguyên từng bản ghi; cần kiểm tra thêm.");
        if (missingScore > 0) warnings.add("Có " + missingScore + " học phần chưa có điểm nên không được tính vào mẫu số.");
        warnings.add("Hệ thống chỉ lấy học phần đang đăng ký có điểm công bố; học phần đã rút hoặc hủy không nằm trong tập này.");
        return warnings;
    }

    private BigDecimal extractGpa(String question, Map<String, String> slots) {
        if (slots != null && slots.get("targetGpa") != null) {
            BigDecimal fromSlot = parseDecimal(slots.get("targetGpa"));
            if (fromSlot != null) return fromSlot;
        }
        Matcher matcher = GPA_TARGET.matcher(normalize(question));
        return matcher.find() ? parseDecimal(matcher.group(1)) : null;
    }

    private Integer extractCredits(String question, Map<String, String> slots) {
        if (slots != null && slots.get("newCredits") != null) {
            try { return Integer.valueOf(slots.get("newCredits")); } catch (NumberFormatException ignored) { }
        }
        Matcher matcher = CREDIT_COUNT.matcher(normalize(question));
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String normalize(String text) {
        if (text == null) return "";
        String value = java.text.Normalizer.normalize(text.toLowerCase(java.util.Locale.ROOT),
                java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return value.replace("đ", "d");
    }

    private BigDecimal parseDecimal(String raw) {
        try { return new BigDecimal(raw.replace(',', '.')); } catch (Exception e) { return null; }
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal b) return b;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(value)); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}

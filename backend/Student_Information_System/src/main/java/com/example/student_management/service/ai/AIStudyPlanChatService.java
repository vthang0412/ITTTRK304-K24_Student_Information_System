package com.example.student_management.service.ai;

import com.example.student_management.dto.grade.StudentGradeDto;
import com.example.student_management.entity.auth.User;
import com.example.student_management.service.timetable.ScheduleParser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lập kế hoạch ôn tập theo ngày từ điểm/lịch thi/lịch học có thật trong SIS. */
public class AIStudyPlanChatService {

    private static final Pattern DAYS = Pattern.compile("(\\d{1,2})\\s*ngay");
    private static final Pattern MINUTES = Pattern.compile("(\\d{2,3})\\s*phut");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM");

    public String build(User user, String question, AIChatToolsService tools) {
        int days = 7;
        Matcher dayMatcher = DAYS.matcher(normalize(question));
        if (dayMatcher.find()) {
            int parsed = Integer.parseInt(dayMatcher.group(1));
            if (parsed >= 1 && parsed <= 90) days = parsed;
        }
        int minutes = 90;
        Matcher minuteMatcher = MINUTES.matcher(normalize(question));
        if (minuteMatcher.find()) {
            int parsed = Integer.parseInt(minuteMatcher.group(1));
            if (parsed >= 15 && parsed <= 480) minutes = parsed;
        }

        List<StudentGradeDto> grades = tools.getPublishedGrades(user);
        List<Focus> focus = buildFocus(grades);
        List<Map<String, Object>> exams = tools.getUpcomingExams(user.getStudent());
        Set<LocalDate> examDates = new HashSet<>();
        for (Map<String, Object> ex : exams) {
            try { examDates.add(LocalDate.parse(String.valueOf(ex.get("examDate")))); }
            catch (Exception ignored) { }
        }
        Set<Integer> classDays = collectClassDays(tools, user);

        StringBuilder sb = new StringBuilder();
        sb.append("### Kế hoạch học tập ").append(days).append(" ngày\n\n");
        sb.append("| Ngày | Môn học | Nội dung | Thời lượng | Mục tiêu |\n|---|---|---|---|---|\n");
        LocalDate start = LocalDate.now().plusDays(1);
        for (int i = 0; i < days; i++) {
            LocalDate date = start.plusDays(i);
            Focus selected = focus.isEmpty()
                    ? new Focus("Chung", null, false)
                    : focus.get(i % focus.size());
            boolean examDay = examDates.contains(date);
            boolean classDay = classDays.contains(date.getDayOfWeek().getValue());
            String content = examDay
                    ? "Ôn nhẹ 20 phút, xem lại đề cương và nghỉ sớm trước kỳ thi"
                    : (selected.failed() ? "Ôn lại kiến thức nền tảng, làm lại 5 bài tập đã sai"
                        : (selected.score() != null && selected.score().compareTo(new BigDecimal("6.5")) < 0
                            ? "Ôn chương điểm yếu, làm 10 câu trắc nghiệm và sửa lỗi"
                            : "Đọc tài liệu chuyên sâu, làm 5 bài tập vận dụng và tự kiểm tra"));
            String goal = examDay
                    ? "Giữ tâm thế ổn định, không học nội dung mới"
                    : (selected.failed() ? "Hiểu lại 3 khái niệm đã sai và làm được bài cơ bản"
                        : "Hoàn thành số bài tập đã đặt ra và ghi lại 3 điểm chưa hiểu");
            String duration = examDay ? "20 phút" : minutes + " phút";
            String note = examDay ? " (ngày thi)" : (classDay ? " (có lịch học trong ngày)" : "");
            sb.append("| ").append(DATE.format(date)).append(note).append(" | ")
                    .append(cell(selected.course())).append(" | ").append(content).append(" | ")
                    .append(duration).append(" | ").append(goal).append(" |\n");
        }

        sb.append("\n### Căn cứ và giả định\n");
        if (focus.isEmpty()) {
            sb.append("- Chưa có điểm công bố hoặc học phần đang học để xác định môn ưu tiên. Hãy cho mình biết môn bạn muốn ôn và khung giờ rảnh của bạn.\n");
        } else {
            sb.append("- Ưu tiên theo điểm đã công bố: ")
                    .append(String.join(", ", focus.stream().map(f -> f.course() + (f.score() == null ? "" : " " + f.score() + "/10")).toList()))
                    .append(".\n");
        }
        sb.append("- Đã tránh nhồi nhét vào ").append(examDates.size()).append(" ngày có lịch thi từ hệ thống")
                .append(classDays.isEmpty() ? "; không có dữ liệu lịch học để xác định ngày có lớp." : "; có lịch học lặp trong tuần nên hãy chọn khung giờ ngoài giờ học của bạn.").append("\n");
        sb.append("- Giả định: bạn tự chọn được khung rảnh ").append(minutes).append(" phút/ngày; hệ thống hiện **không lưu thời gian rảnh** nên không khẳng định khung này hoàn toàn trống.\n");
        sb.append("- Kế hoạch này chỉ hiển thị trong hội thoại, **chưa được lưu**. Nếu muốn lưu, hãy dùng trang AI Study Planner hoặc nói rõ yêu cầu lưu kế hoạch.\n");
        sb.append("\n_Nguồn: Published Grades & Academic Records, Enrollment Records, Exam Schedule._");
        return sb.toString();
    }

    private List<Focus> buildFocus(List<StudentGradeDto> grades) {
        List<Focus> focus = new ArrayList<>();
        for (StudentGradeDto g : grades) {
            String code = g.getCourseCode() == null ? "N/A" : g.getCourseCode();
            if (focus.stream().anyMatch(f -> f.course().equals(code))) continue;
            boolean failed = Boolean.TRUE.equals(g.getPassed())
                    ? false
                    : (g.getFinalScore() != null && g.getFinalScore().compareTo(new BigDecimal("4.00")) < 0);
            focus.add(new Focus(code, g.getFinalScore(), failed));
        }
        focus.sort(Comparator.comparing(f -> f.score() == null ? new BigDecimal("10") : f.score()));
        return focus;
    }

    private Set<Integer> collectClassDays(AIChatToolsService tools, User user) {
        Set<Integer> days = new HashSet<>();
        Map<String, Object> summary = tools.summarizeEnrollments(tools.getAllEnrollments(user.getStudent()));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) summary.getOrDefault("items", List.of());
        for (Map<String, Object> item : items) {
            if (!"ENROLLED".equals(String.valueOf(item.get("status")))) continue;
            for (ScheduleParser.Slot slot : ScheduleParser.parse(String.valueOf(item.get("schedule")))) {
                days.add(slot.dayOfWeek());
            }
        }
        return days;
    }

    private String normalize(String text) {
        if (text == null) return "";
        String s = java.text.Normalizer.normalize(text.toLowerCase(java.util.Locale.ROOT),
                java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replace("đ", "d");
    }

    private String cell(String value) {
        if (value == null || value.isBlank()) return "Chung";
        return value.replace("|", "/");
    }

    private record Focus(String course, BigDecimal score, boolean failed) { }
}

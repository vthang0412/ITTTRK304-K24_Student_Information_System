package com.example.student_management.service.ai;

import com.example.student_management.entity.auth.Student;
import com.example.student_management.service.timetable.ScheduleParser;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tổng hợp lịch học (lịch lặp) và lịch thi (ngày cụ thể) mà không bịa ngày cho lịch học. */
public class AITimetableAssistantService {

    public String build(String studentKey, Student student, AIChatToolsService tools, boolean includeExams) {
        Map<String, Object> enrollments = tools.summarizeEnrollments(tools.getAllEnrollments(student));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) enrollments.getOrDefault("items", List.of());
        List<Map<String, Object>> classes = items.stream()
                .filter(i -> "ENROLLED".equals(String.valueOf(i.get("status"))))
                .filter(i -> i.get("schedule") != null && !"Chưa có".equals(String.valueOf(i.get("schedule"))))
                .toList();
        List<Map<String, Object>> exams = includeExams ? tools.getUpcomingExams(student) : List.of();

        StringBuilder sb = new StringBuilder();
        sb.append("### Phân tích lịch ").append(includeExams ? "học và lịch thi" : "thi").append("\n\n");

        if (includeExams) {
            sb.append("#### 1. Lịch học (lịch lặp theo hệ thống)\n\n");
            if (classes.isEmpty()) {
                sb.append("Hiện hệ thống chưa có dữ liệu lịch học có thể phân tích (các lớp đang học chưa xếp lịch hoặc lịch không parse được). "
                        + "Hệ thống hiện chỉ truy xuất được lịch thi; chưa đủ dữ liệu để phân tích đầy đủ lịch học.\n\n");
            } else {
                sb.append("| Thứ | Thời gian | Môn học | Lớp | Địa điểm |\n|---|---|---|---|---|\n");
                List<Row> rows = new ArrayList<>();
                for (Map<String, Object> item : classes) {
                    for (ScheduleParser.Slot slot : ScheduleParser.parse(String.valueOf(item.get("schedule")))) {
                        rows.add(new Row(slot.dayOfWeek(), slot.startTime(), slot.endTime(),
                                String.valueOf(item.get("courseName")),
                                String.valueOf(item.get("sectionCode")), slot.room()));
                    }
                }
                rows.sort(Comparator.comparingInt(Row::day).thenComparing(Row::start));
                for (Row r : rows) {
                    sb.append("| ").append(dayName(r.day())).append(" | ").append(r.start())
                            .append(" - ").append(r.end()).append(" | ").append(cell(r.course()))
                            .append(" | ").append(cell(r.section())).append(" | ").append(cell(r.room())).append(" |\n");
                }
                sb.append("\n");
            }
            sb.append("#### 2. Lịch thi (theo ngày cụ thể)\n\n");
        } else {
            sb.append("#### Lịch thi (theo ngày cụ thể)\n\n");
        }

        if (exams.isEmpty()) {
            sb.append("Hiện hệ thống chưa có dữ liệu lịch thi sắp tới cho các lớp bạn đang theo học.\n\n");
        } else {
            sb.append("| Ngày | Thời gian | Môn học | Phòng | Trạng thái |\n|---|---|---|---|---|\n");
            for (Map<String, Object> ex : exams) {
                sb.append("| ").append(cell(String.valueOf(ex.get("examDate")))).append(" | ")
                        .append(cell(String.valueOf(ex.getOrDefault("startTime", "")))).append(" - ")
                        .append(cell(String.valueOf(ex.getOrDefault("endTime", "")))).append(" | ")
                        .append(cell(String.valueOf(ex.get("courseName")))).append(" | ")
                        .append(cell(String.valueOf(ex.getOrDefault("room", "Chưa có")))).append(" | ")
                        .append(cell(String.valueOf(ex.getOrDefault("status", "")))).append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("#### Cảnh báo\n");
        List<String> warnings = new ArrayList<>();
        warnings.addAll(classConflicts(classes));
        warnings.addAll(examGaps(exams));
        if (warnings.isEmpty()) {
            sb.append("- Không phát hiện trùng lịch hoặc khoảng nghỉ quá ngắn từ dữ liệu hiện có. ");
            sb.append("Lịch học là lịch lặp theo thứ nên chưa thể đối chiếu trực tiếp từng ngày với lịch thi.\n");
        } else {
            for (String w : warnings) sb.append("- ").append(w).append("\n");
        }
        sb.append("\n_Nguồn: enrollment records (course section schedule) và exam schedule trong SIS; ");
        sb.append("không tạo thêm lịch nào ngoài dữ liệu hệ thống._");
        return sb.toString();
    }

    private List<String> classConflicts(List<Map<String, Object>> classes) {
        List<ScheduleParser.Slot> all = new ArrayList<>();
        for (Map<String, Object> item : classes) {
            for (ScheduleParser.Slot slot : ScheduleParser.parse(String.valueOf(item.get("schedule")))) {
                all.add(slot);
            }
        }
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                ScheduleParser.Slot a = all.get(i);
                ScheduleParser.Slot b = all.get(j);
                if (a.dayOfWeek() == b.dayOfWeek() && a.startTime().isBefore(b.endTime())
                        && b.startTime().isBefore(a.endTime())) {
                    warnings.add("Trùng lịch học: " + dayName(a.dayOfWeek()) + " " + a.startTime() + " - " + a.endTime()
                            + " và " + b.startTime() + " - " + b.endTime() + ".");
                }
            }
        }
        return warnings;
    }

    private List<String> examGaps(List<Map<String, Object>> exams) {
        List<String> warnings = new ArrayList<>();
        Map<String, List<Map<String, Object>>> byDate = new LinkedHashMap<>();
        for (Map<String, Object> ex : exams) {
            byDate.computeIfAbsent(String.valueOf(ex.get("examDate")), k -> new ArrayList<>()).add(ex);
        }
        for (Map.Entry<String, List<Map<String, Object>>> entry : byDate.entrySet()) {
            List<Map<String, Object>> day = entry.getValue();
            day.sort(Comparator.comparing(e -> String.valueOf(e.getOrDefault("startTime", ""))));
            for (int i = 1; i < day.size(); i++) {
                LocalTime prevEnd = parseTime(day.get(i - 1).get("endTime"));
                LocalTime nextStart = parseTime(day.get(i).get("startTime"));
                if (prevEnd == null || nextStart == null) continue;
                long minutes = Duration.between(prevEnd, nextStart).toMinutes();
                if (minutes < 30) {
                    warnings.add("Khoảng nghỉ giữa hai kỳ thi ngày " + entry.getKey() + " chỉ " + minutes
                            + " phút, có thể không đủ thời gian di chuyển.");
                }
            }
        }
        return warnings;
    }

    private LocalTime parseTime(Object raw) {
        if (raw == null) return null;
        String value = String.valueOf(raw);
        if (value.isBlank() || "null".equals(value)) return null;
        try { return LocalTime.parse(value.length() == 5 ? value : value.substring(0, 5)); }
        catch (Exception e) { return null; }
    }

    private String cell(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) return "Chưa có";
        return value.replace("|", "/").replace("\n", " ");
    }

    private String dayName(int day) {
        return day == 7 ? "Chủ nhật" : "Thứ " + day;
    }

    private record Row(int day, LocalTime start, LocalTime end, String course, String section, String room) { }
}

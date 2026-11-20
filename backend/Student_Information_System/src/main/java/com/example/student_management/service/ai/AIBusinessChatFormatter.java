package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.AIRecommendationDto;
import com.example.student_management.dto.ai.AISemesterInsightDto;
import com.example.student_management.dto.ai.AIInsightsDto;
import com.example.student_management.dto.ai.CourseAdvisorResponseDto;
import com.example.student_management.dto.ai.CourseRecommendationItemDto;
import com.example.student_management.dto.ai.CourseSectionOptionDto;

import java.util.ArrayList;
import java.util.List;

/** Chuyển DTO nghiệp vụ sang Markdown cho chat; không dùng LLM để sửa số liệu. */
public final class AIBusinessChatFormatter {

    private AIBusinessChatFormatter() { }

    public static String formatAdvisor(CourseAdvisorResponseDto a) {
        if (a == null) return "Hiện hệ thống chưa có dữ liệu tư vấn môn học cho bạn.";
        StringBuilder sb = new StringBuilder();
        sb.append("### Tư vấn môn học kỳ tới\n\n");
        sb.append("- Tín chỉ tích lũy: **").append(a.getAccumulatedCredits()).append(" TC**\n");
        sb.append("- Tín chỉ đang học: **").append(a.getActiveCredits()).append(" TC**\n");
        sb.append("- Giới hạn đăng ký mỗi kỳ: **").append(a.getMaxCreditsPerSemester()).append(" TC**\n\n");

        List<CourseRecommendationItemDto> recommended = a.getRecommendedCourses() == null
                ? List.of() : a.getRecommendedCourses();
        int totalCredits = recommended.stream().mapToInt(r -> r.getCredits() == null ? 0 : r.getCredits()).sum();
        sb.append("**Đề xuất: ").append(recommended.size()).append(" môn, tổng ").append(totalCredits).append(" TC**\n\n");
        if (recommended.isEmpty()) {
            sb.append("Hiện chưa có môn nào đủ điều kiện và có lớp mở phù hợp.\n\n");
        } else {
            sb.append("| Môn học | TC | Ưu tiên | Trạng thái | Lý do |\n|---|---:|---|---|---|\n");
            for (CourseRecommendationItemDto r : recommended) {
                sb.append("| ").append(cell(r.getCourseCode())).append(" - ").append(cell(r.getCourseName()))
                        .append(" | ").append(r.getCredits() == null ? 0 : r.getCredits())
                        .append(" | ").append(cell(r.getPriority()))
                        .append(" | ").append(cell(r.getStatus()))
                        .append(" | ").append(cell(r.getReason())).append(" |\n");
            }
            sb.append("\n");
            List<String> blocked = new ArrayList<>();
            for (CourseRecommendationItemDto r : recommended) {
                for (CourseSectionOptionDto s : r.getAvailableSections() == null ? List.<CourseSectionOptionDto>of() : r.getAvailableSections()) {
                    if (s.isHasScheduleConflict()) blocked.add(s.getSectionCode() + ": " + s.getConflictWith());
                }
            }
            if (!blocked.isEmpty()) {
                sb.append("**Lớp bị trùng lịch:** ").append(String.join("; ", blocked)).append("\n\n");
            }
        }

        List<CourseRecommendationItemDto> notRecommended = a.getNotRecommendedCourses() == null
                ? List.of() : a.getNotRecommendedCourses();
        List<CourseRecommendationItemDto> reasons = notRecommended.stream()
                .filter(r -> r.getStatus() != null && (r.getStatus().contains("PREREQUISITE")
                        || r.getStatus().contains("CONFLICT") || r.getStatus().contains("FULL")))
                .limit(8).toList();
        if (!reasons.isEmpty()) {
            sb.append("**Lý do không đề xuất (mẫu):**\n");
            for (CourseRecommendationItemDto r : reasons) {
                sb.append("- ").append(cell(r.getCourseCode())).append(" - ").append(cell(r.getCourseName()))
                        .append(": ").append(cell(r.getReason())).append("\n");
            }
            sb.append("\n");
        }
        sb.append("_").append(cell(a.getDisclaimer())).append("_\n");
        return sb.toString();
    }

    public static String formatInsights(AIInsightsDto d) {
        if (d == null) return "Hiện hệ thống chưa có dữ liệu phân tích học tập cho bạn.";
        StringBuilder sb = new StringBuilder();
        sb.append("### Phân tích kết quả học tập\n\n");
        sb.append("| Chỉ số | Giá trị |\n|---|---|\n");
        sb.append("| GPA hệ 10 | **").append(d.getGpa10()).append("** |\n");
        sb.append("| GPA hệ 4 | **").append(d.getGpa4()).append("** |\n");
        sb.append("| Tín chỉ đã hoàn thành | ").append(d.getCompletedCredits()).append(" TC |\n");
        sb.append("| Tín chỉ đang học | ").append(d.getActiveCredits()).append(" TC |\n");
        sb.append("| Môn đạt / chưa đạt | ").append(d.getTotalCompletedCourses()).append(" / ")
                .append(d.getTotalFailedCourses()).append(" |\n");
        sb.append("| Xếp loại | ").append(cell(d.getAcademicClassification())).append(" |\n\n");

        List<AISemesterInsightDto> semesters = d.getSemesterInsights() == null ? List.of() : d.getSemesterInsights();
        if (!semesters.isEmpty()) {
            sb.append("**Xu hướng theo học kỳ:**\n");
            for (AISemesterInsightDto s : semesters) {
                sb.append("- ").append(cell(s.getSemesterName())).append(": GPA ").append(s.getGpa10())
                        .append("/10, đạt ").append(s.getCoursesCompleted()).append("/")
                        .append(s.getCoursesCompleted() + s.getCoursesFailed()).append(" môn, xu hướng ")
                        .append(cell(s.getTrend())).append("\n");
            }
            sb.append("\n");
        }
        List<AIRecommendationDto> recs = d.getRecommendations() == null ? List.of() : d.getRecommendations();
        if (!recs.isEmpty()) {
            sb.append("**Khuyến nghị:**\n");
            for (AIRecommendationDto r : recs) {
                sb.append("- [").append(cell(r.getPriority())).append("] ").append(cell(r.getTitle()))
                        .append(": ").append(cell(r.getDescription()))
                        .append(r.getRelatedCourseCode() == null ? "" : " (").append(r.getRelatedCourseCode()).append(")");
                sb.append("\n");
            }
            sb.append("\n");
        }
        if (d.getLowScoreCourses() != null && !d.getLowScoreCourses().isEmpty()) {
            sb.append("**Môn điểm thấp cần cải thiện:** ").append(d.getLowScoreCourses().size()).append(" môn.\n\n");
        }
        sb.append("_Nguồn: dữ liệu điểm đã công bố và đăng ký học phần trong SIS; các nhận xét ở trên là gợi ý, không phải quyết định học vụ._");
        return sb.toString();
    }

    private static String cell(Object value) {
        if (value == null) return "Chưa có";
        return String.valueOf(value).replace("|", "/").replace("\n", " ");
    }
}

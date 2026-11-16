package com.example.student_management.service.ai;

import com.example.student_management.dto.grade.StudentGradeDto;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AIContextService {

    @Autowired
    private AIChatToolsService toolsService;

    @Transactional(readOnly = true)
    public Map<String, Object> getStudentAcademicSummary(Student student, User user) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Object> profile = toolsService.getStudentProfile(student);
        summary.putAll(profile);
        List<StudentGradeDto> grades = toolsService.getPublishedGrades(user);
        summary.put("publishedGrades", grades);
        Map<String, Object> gpa = toolsService.computeGpaAndCredits(grades);
        summary.putAll(gpa);
        List<com.example.student_management.entity.academic.Enrollment> enrollments = toolsService.getAllEnrollments(student);
        Map<String, Object> enr = toolsService.summarizeEnrollments(enrollments);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) enr.getOrDefault("items", List.of());
        summary.put("allEnrollments", items);
        List<Map<String, Object>> active = items.stream().filter(i -> "ENROLLED".equals(String.valueOf(i.get("status")))).toList();
        summary.put("activeEnrollments", active);
        summary.put("activeCredits", enr.getOrDefault("activeCredits", 0));
        summary.put("semesterBreakdown", toolsService.getSemesterBreakdown(grades));
        return summary;
    }

    @Transactional(readOnly = true)
    public String buildSystemContextPrompt(Student student, User user) {
        Map<String, Object> data = getStudentAcademicSummary(student, user);
        StringBuilder sb = new StringBuilder();
        sb.append("=== DỮ LIỆU HỌC TẬP THỰC TẾ CỦA SINH VIÊN (NGUỒN HỆ THỐNG SIS) ===\n");
        sb.append("Họ và tên: ").append(data.get("fullName")).append("\n");
        sb.append("Mã sinh viên: ").append(data.get("studentCode")).append("\n");
        sb.append("Email: ").append(data.get("email")).append("\n");
        sb.append("Khoa: ").append(data.get("department")).append("\n");
        sb.append("Ngành: ").append(data.get("major")).append("\n");
        sb.append("Lớp: ").append(data.get("className")).append("\n");
        sb.append("Trạng thái học tập: ").append(data.get("academicStatus")).append("\n");
        sb.append("Tín chỉ tích lũy: ").append(data.get("accumulatedCredits")).append(" tín chỉ\n");
        sb.append("GPA (thang 10): ").append(data.get("gpa10")).append("\n");
        sb.append("GPA (thang 4): ").append(data.get("gpa4")).append("\n\n");

        sb.append("Môn đã hoàn thành/đã có điểm công bố:\n");
        @SuppressWarnings("unchecked") List<Map<String, Object>> passed = (List<Map<String, Object>>) data.getOrDefault("passedCourses", List.of());
        @SuppressWarnings("unchecked") List<Map<String, Object>> failed = (List<Map<String, Object>>) data.getOrDefault("failedCourses", List.of());
        if (passed.isEmpty() && failed.isEmpty()) sb.append("- Hiện chưa có dữ liệu điểm công bố.\n");
        for (Map<String, Object> p : passed) {
            sb.append("- [ĐẠT] ").append(p.get("courseCode")).append(" - ").append(p.get("courseName"))
              .append(" (").append(p.get("credits")).append(" TC, ").append(p.get("semester")).append(") điểm ").append(p.get("finalScore")).append("\n");
        }
        for (Map<String, Object> f : failed) {
            sb.append("- [CHƯA ĐẠT] ").append(f.get("courseCode")).append(" - ").append(f.get("courseName"))
              .append(" (").append(f.get("credits")).append(" TC, ").append(f.get("semester")).append(") điểm ").append(f.get("finalScore")).append("\n");
        }

        sb.append("\nLớp học phần đang đăng ký (ENROLLED):\n");
        @SuppressWarnings("unchecked") List<Map<String, Object>> active = (List<Map<String, Object>>) data.getOrDefault("activeEnrollments", List.of());
        if (active.isEmpty()) sb.append("- Hiện chưa có lớp học phần đang theo học.\n");
        for (Map<String, Object> a : active) {
            sb.append("- ").append(a.get("sectionCode")).append(" - ").append(a.get("courseName"))
              .append(" (").append(a.get("credits")).append(" TC), GV: ").append(a.get("lecturer"))
              .append(", Lịch: ").append(a.get("schedule")).append("\n");
        }

        sb.append("\n=== NGUYÊN TẮC PHẢN HỒI (BẮT BUỘC) ===\n");
        sb.append("1. Bạn là AI Study Assistant của hệ thống SIS, chỉ hỗ trợ sinh viên đang đăng nhập.\n");
        sb.append("2. Chỉ sử dụng dữ liệu SIS được cung cấp. Không bịa điểm, tín chỉ, môn học, lịch, quy định.\n");
        sb.append("3. Nếu thiếu dữ liệu, nói rõ: 'Hiện hệ thống chưa có dữ liệu này'.\n");
        sb.append("4. Trả lời tiếng Việt, rõ ràng, có cấu trúc; dùng danh sách/bảng khi phù hợp.\n");
        sb.append("5. Phân biệt dữ liệu thực tế và nhận xét/gợi ý của AI.\n");
        sb.append("6. Không thực hiện hoặc khẳng định đã thực hiện đăng ký học phần/sửa điểm.\n");
        return sb.toString();
    }
}

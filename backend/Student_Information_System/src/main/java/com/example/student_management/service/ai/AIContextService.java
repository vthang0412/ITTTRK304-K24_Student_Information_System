package com.example.student_management.service.ai;

import com.example.student_management.dto.grade.StudentGradeDto;
import com.example.student_management.entity.academic.Course;
import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.academic.Enrollment;
import com.example.student_management.entity.academic.EnrollmentStatus;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.repository.academic.CourseRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.service.grade.GradebookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIContextService {

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private GradebookService gradebookService;

    @Transactional(readOnly = true)
    public Map<String, Object> getStudentAcademicSummary(Student student, User user) {
        Map<String, Object> summary = new LinkedHashMap<>();

        // 1. Thông tin cá nhân
        summary.put("studentCode", student.getStudentCode());
        summary.put("fullName", student.getFullName());
        summary.put("email", student.getEmail());
        summary.put("department", student.getDepartment() != null ? student.getDepartment() : "Chưa cập nhật");
        summary.put("className", student.getClassName() != null ? student.getClassName() : "Chưa cập nhật");
        summary.put("major", student.getMajor() != null ? student.getMajor() : "Chưa cập nhật");
        summary.put("academicStatus", student.getAcademicStatus() != null ? student.getAcademicStatus() : "Đang học");

        // 2. Điểm số chính thức đã công bố
        List<StudentGradeDto> publishedGrades = gradebookService.getStudentPublishedGrades(user.getUsername());
        summary.put("publishedGrades", publishedGrades);

        // 3. Tính toán tín chỉ tích lũy và GPA
        int accumulatedCredits = 0;
        BigDecimal totalQualityPoints = BigDecimal.ZERO;
        int totalGradedCredits = 0;
        List<Map<String, Object>> passedCourses = new ArrayList<>();
        List<Map<String, Object>> failedCourses = new ArrayList<>();

        for (StudentGradeDto g : publishedGrades) {
            int credits = g.getCredits() != null ? g.getCredits() : 0;
            BigDecimal finalScore = g.getFinalScore();
            boolean isPassed = Boolean.TRUE.equals(g.getPassed()) || (finalScore != null && finalScore.compareTo(new BigDecimal("4.00")) >= 0);

            Map<String, Object> courseInfo = new LinkedHashMap<>();
            courseInfo.put("courseCode", g.getCourseCode());
            courseInfo.put("courseName", g.getCourseName());
            courseInfo.put("credits", credits);
            courseInfo.put("finalScore", finalScore);
            courseInfo.put("classification", g.getClassification());
            courseInfo.put("semester", g.getSemesterName());

            if (isPassed) {
                accumulatedCredits += credits;
                passedCourses.add(courseInfo);
            } else {
                failedCourses.add(courseInfo);
            }

            if (finalScore != null && credits > 0) {
                totalQualityPoints = totalQualityPoints.add(finalScore.multiply(BigDecimal.valueOf(credits)));
                totalGradedCredits += credits;
            }
        }

        BigDecimal gpa10 = totalGradedCredits > 0
                ? totalQualityPoints.divide(BigDecimal.valueOf(totalGradedCredits), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal gpa4 = calculateGpa4(gpa10);

        summary.put("accumulatedCredits", accumulatedCredits);
        summary.put("gpa10", gpa10);
        summary.put("gpa4", gpa4);
        summary.put("passedCourses", passedCourses);
        summary.put("failedCourses", failedCourses);

        // 4. Lớp học phần đang theo học (ENROLLED)
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(student.getId());
        List<Map<String, Object>> activeEnrollments = new ArrayList<>();
        List<Map<String, Object>> allEnrollments = new ArrayList<>();

        for (Enrollment e : enrollments) {
            CourseSection sec = e.getCourseSection();
            Course c = sec != null ? sec.getCourse() : null;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("enrollmentId", e.getId());
            item.put("status", e.getStatus().name());
            item.put("sectionCode", sec != null ? sec.getSectionCode() : "N/A");
            item.put("courseCode", c != null ? c.getCourseCode() : "N/A");
            item.put("courseName", c != null ? c.getCourseName() : "N/A");
            item.put("credits", c != null ? c.getCredits() : 0);
            item.put("schedule", sec != null ? sec.getSchedule() : "Chưa có");
            item.put("lecturer", (sec != null && sec.getLecturer() != null) ? sec.getLecturer().getFullName() : "Chưa phân công");
            item.put("semester", (sec != null && sec.getSemester() != null) ? sec.getSemester().getSemesterName() : "N/A");
            item.put("registeredAt", e.getRegisteredAt());

            allEnrollments.add(item);
            if (e.getStatus() == EnrollmentStatus.ENROLLED) {
                activeEnrollments.add(item);
            }
        }

        summary.put("activeEnrollments", activeEnrollments);
        summary.put("allEnrollments", allEnrollments);

        return summary;
    }

    /**
     * Tạo chuỗi ngữ cảnh dữ liệu học tập thực tế (System Prompt Context) cho AI
     */
    @Transactional(readOnly = true)
    public String buildSystemContextPrompt(Student student, User user) {
        Map<String, Object> data = getStudentAcademicSummary(student, user);

        StringBuilder sb = new StringBuilder();
        sb.append("=== DỮ LIỆU HỌC TẬP THỰC TẾ CỦA SINH VIÊN (NGUỒN HỆ THỐNG SIS) ===\n");
        sb.append("• Họ và tên: ").append(data.get("fullName")).append("\n");
        sb.append("• Mã sinh viên: ").append(data.get("studentCode")).append("\n");
        sb.append("• Email: ").append(data.get("email")).append("\n");
        sb.append("• Khoa: ").append(data.get("department")).append("\n");
        sb.append("• Ngành học: ").append(data.get("major")).append("\n");
        sb.append("• Lớp hành chính: ").append(data.get("className")).append("\n");
        sb.append("• Trạng thái học tập: ").append(data.get("academicStatus")).append("\n");
        sb.append("• Tổng số tín chỉ đã tích lũy: ").append(data.get("accumulatedCredits")).append(" tín chỉ\n");
        sb.append("• Điểm trung bình tích lũy (GPA thang 10): ").append(data.get("gpa10")).append("/10\n");
        sb.append("• Điểm trung bình tích lũy (GPA thang 4): ").append(data.get("gpa4")).append("/4.0\n\n");

        sb.append("--- DANH SÁCH MÔN HỌC ĐÃ CÓ ĐIỂM CHÍNH THỨC ---\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> passed = (List<Map<String, Object>>) data.get("passedCourses");
        if (passed.isEmpty()) {
            sb.append("(Chưa có môn học nào hoàn thành)\n");
        } else {
            for (Map<String, Object> p : passed) {
                sb.append("- ").append(p.get("courseCode")).append(" - ").append(p.get("courseName"))
                        .append(" (").append(p.get("credits")).append(" TC, Học kỳ: ").append(p.get("semester"))
                        .append("): Điểm = ").append(p.get("finalScore")).append(" (").append(p.get("classification")).append(")\n");
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failed = (List<Map<String, Object>>) data.get("failedCourses");
        if (!failed.isEmpty()) {
            sb.append("\n--- CÁC MÔN HỌC CHƯA ĐẠT (CẦN HỌC LẠI / CẢI THIỆN) ---\n");
            for (Map<String, Object> f : failed) {
                sb.append("- [CẦN HỌC LẠI] ").append(f.get("courseCode")).append(" - ").append(f.get("courseName"))
                        .append(" (").append(f.get("credits")).append(" TC): Điểm = ").append(f.get("finalScore"))
                        .append(" (").append(f.get("classification")).append(")\n");
            }
        }

        sb.append("\n--- CÁC LỚP HỌC PHẦN ĐANG THEO HỌC TRONG KỲ ---\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> active = (List<Map<String, Object>>) data.get("activeEnrollments");
        if (active.isEmpty()) {
            sb.append("(Hiện tại chưa đăng ký lớp học phần nào)\n");
        } else {
            for (Map<String, Object> a : active) {
                sb.append("- Lớp ").append(a.get("sectionCode")).append(": ").append(a.get("courseName"))
                        .append(" (").append(a.get("credits")).append(" TC) | GV: ").append(a.get("lecturer"))
                        .append(" | Thời khóa biểu: ").append(a.get("schedule")).append("\n");
            }
        }

        sb.append("\n=== NGUYÊN TẮC PHẢN HỒI CỦA TRỢ LÝ AI (BẮT BUỘC TUÂN THỦ) ===\n");
        sb.append("1. Bạn là Trợ lý học tập thông minh (AI Study Assistant) của trường học SIS.\n");
        sb.append("2. Chỉ sử dụng dữ liệu thực tế bên trên để trả lời câu hỏi của sinh viên. TUYỆT ĐỐI KHÔNG tự bịa hoặc suy đoán số liệu.\n");
        sb.append("3. Nếu sinh viên hỏi về dữ liệu chưa có trong hệ thống, hãy thông báo lịch sự là 'Hiện tại hệ thống chưa có dữ liệu này'.\n");
        sb.append("4. Giọng điệu thân thiện, súc tích, khuyến khích tinh thần học tập, định dạng câu trả lời rõ ràng (dùng Markdown, gạch đầu dòng, in đậm các số liệu quan trọng).\n");

        return sb.toString();
    }

    private BigDecimal calculateGpa4(BigDecimal gpa10) {
        if (gpa10 == null || gpa10.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        double val = gpa10.doubleValue();
        if (val >= 8.5) return new BigDecimal("4.00");
        if (val >= 8.0) return new BigDecimal("3.50");
        if (val >= 7.0) return new BigDecimal("3.00");
        if (val >= 6.5) return new BigDecimal("2.50");
        if (val >= 5.5) return new BigDecimal("2.00");
        if (val >= 5.0) return new BigDecimal("1.50");
        if (val >= 4.0) return new BigDecimal("1.00");
        return BigDecimal.ZERO;
    }
}


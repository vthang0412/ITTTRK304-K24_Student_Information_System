package com.example.student_management.service.ai;

import com.example.student_management.dto.grade.StudentGradeDto;
import com.example.student_management.entity.academic.*;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.entity.exam.ExamSchedule;
import com.example.student_management.entity.exam.ExamScheduleStatus;
import com.example.student_management.repository.academic.CourseRepository;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.exam.ExamScheduleRepository;
import com.example.student_management.service.grade.GradebookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIChatToolsService {

    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseSectionRepository courseSectionRepository;
    @Autowired private ExamScheduleRepository examScheduleRepository;
    @Autowired private GradebookService gradebookService;

    @Transactional(readOnly = true)
    public Map<String, Object> getStudentProfile(Student student) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("studentCode", student.getStudentCode());
        p.put("fullName", student.getFullName());
        p.put("email", student.getEmail());
        p.put("department", nz(student.getDepartment(), "Chưa cập nhật"));
        p.put("major", nz(student.getMajor(), "Chưa cập nhật"));
        p.put("className", nz(student.getClassName(), "Chưa cập nhật"));
        p.put("academicStatus", nz(student.getAcademicStatus(), "Đang học"));
        return p;
    }

    @Transactional(readOnly = true)
    public List<StudentGradeDto> getPublishedGrades(User user) {
        try {
            return gradebookService.getStudentPublishedGrades(user.getUsername());
        } catch (Exception e) {
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public List<Enrollment> getAllEnrollments(Student student) {
        return enrollmentRepository.findByStudentIdOrderByRegisteredAtDesc(student.getId());
    }

    @Transactional(readOnly = true)
    public List<Enrollment> getActiveEnrollments(Student student) {
        return enrollmentRepository.findByStudentIdAndStatus(student.getId(), EnrollmentStatus.ENROLLED);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> computeGpaAndCredits(List<StudentGradeDto> grades) {
        Map<String, Object> out = new LinkedHashMap<>();
        int accumulatedCredits = 0, totalGradedCredits = 0, passedCount = 0, failedCount = 0;
        BigDecimal totalQualityPoints = BigDecimal.ZERO;
        List<Map<String, Object>> passed = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (StudentGradeDto g : grades) {
            int credits = g.getCredits() != null ? g.getCredits() : 0;
            BigDecimal finalScore = g.getFinalScore();
            boolean isPassed = Boolean.TRUE.equals(g.getPassed()) || (finalScore != null && finalScore.compareTo(new BigDecimal("4.00")) >= 0);
            Map<String, Object> item = gradeItem(g);
            if (finalScore != null && credits > 0) {
                totalQualityPoints = totalQualityPoints.add(finalScore.multiply(BigDecimal.valueOf(credits)));
                totalGradedCredits += credits;
            }
            if (isPassed) {
                accumulatedCredits += credits; passedCount++; passed.add(item);
            } else {
                failedCount++; failed.add(item);
            }
        }
        BigDecimal gpa10 = totalGradedCredits > 0 ? totalQualityPoints.divide(BigDecimal.valueOf(totalGradedCredits), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        out.put("accumulatedCredits", accumulatedCredits);
        out.put("gpa10", gpa10);
        out.put("gpa4", calculateGpa4(gpa10));
        out.put("passedCount", passedCount);
        out.put("failedCount", failedCount);
        out.put("passedCourses", passed);
        out.put("failedCourses", failed);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summarizeEnrollments(List<Enrollment> enrollments) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        int activeCredits = 0;
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Enrollment e : enrollments) {
            CourseSection sec = e.getCourseSection();
            Course c = sec != null ? sec.getCourse() : null;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("enrollmentId", e.getId());
            item.put("status", e.getStatus() != null ? e.getStatus().name() : "N/A");
            item.put("sectionCode", sec != null ? sec.getSectionCode() : "N/A");
            item.put("courseCode", c != null ? c.getCourseCode() : "N/A");
            item.put("courseName", c != null ? c.getCourseName() : "N/A");
            item.put("credits", c != null ? c.getCredits() : 0);
            item.put("schedule", sec != null ? nz(sec.getSchedule(), "Chưa có") : "Chưa có");
            item.put("lecturer", (sec != null && sec.getLecturer() != null) ? sec.getLecturer().getFullName() : "Chưa phân công");
            item.put("semester", (sec != null && sec.getSemester() != null) ? sec.getSemester().getSemesterName() : "N/A");
            items.add(item);
            String st = item.get("status").toString();
            byStatus.put(st, byStatus.getOrDefault(st, 0L) + 1);
            if (e.getStatus() == EnrollmentStatus.ENROLLED) {
                activeCredits += c != null && c.getCredits() != null ? c.getCredits() : 0;
            }
        }
        out.put("items", items);
        out.put("statusCounts", byStatus);
        out.put("activeCredits", activeCredits);
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSemesterBreakdown(List<StudentGradeDto> grades) {
        Map<String, List<StudentGradeDto>> bySem = new LinkedHashMap<>();
        for (StudentGradeDto g : grades) {
            String sem = g.getSemesterName() != null ? g.getSemesterName() : "Không rõ học kỳ";
            bySem.computeIfAbsent(sem, k -> new ArrayList<>()).add(g);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (var e : bySem.entrySet()) {
            List<StudentGradeDto> list = e.getValue();
            int credits = 0, passed = 0, failed = 0, gradedCredits = 0;
            BigDecimal qp = BigDecimal.ZERO;
            for (StudentGradeDto g : list) {
                int c = g.getCredits() != null ? g.getCredits() : 0;
                boolean isPassed = Boolean.TRUE.equals(g.getPassed()) || (g.getFinalScore() != null && g.getFinalScore().compareTo(new BigDecimal("4.00")) >= 0);
                if (isPassed) { credits += c; passed++; } else { failed++; }
                if (g.getFinalScore() != null && c > 0) { qp = qp.add(g.getFinalScore().multiply(BigDecimal.valueOf(c))); gradedCredits += c; }
            }
            BigDecimal gpa = gradedCredits > 0 ? qp.divide(BigDecimal.valueOf(gradedCredits), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("semester", e.getKey());
            row.put("courseCount", list.size());
            row.put("passedCount", passed);
            row.put("failedCount", failed);
            row.put("earnedCredits", credits);
            row.put("gpa10", gpa);
            result.add(row);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUpcomingExams(Student student) {
        try {
            List<Enrollment> active = getActiveEnrollments(student);
            List<Long> sectionIds = active.stream().map(en -> en.getCourseSection().getId()).distinct().toList();
            if (sectionIds.isEmpty()) return List.of();
            List<ExamSchedule> exams = examScheduleRepository.findByCourseSectionIdInAndStatusNot(sectionIds, ExamScheduleStatus.CANCELLED);
            LocalDate today = LocalDate.now();
            List<Map<String, Object>> out = new ArrayList<>();
            for (ExamSchedule ex : exams) {
                if (ex.getExamDate() == null || ex.getExamDate().isBefore(today.minusDays(1))) continue;
                CourseSection sec = ex.getCourseSection(); Course c = sec != null ? sec.getCourse() : null;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("examId", ex.getId());
                m.put("courseCode", c != null ? c.getCourseCode() : "N/A");
                m.put("courseName", c != null ? c.getCourseName() : "N/A");
                m.put("sectionCode", sec != null ? sec.getSectionCode() : "N/A");
                m.put("examType", ex.getExamType() != null ? ex.getExamType().name() : "N/A");
                m.put("examDate", ex.getExamDate().toString());
                m.put("startTime", ex.getStartTime() != null ? ex.getStartTime().toString() : null);
                m.put("endTime", ex.getEndTime() != null ? ex.getEndTime().toString() : null);
                m.put("room", ex.getRoom() != null ? ex.getRoom().getRoomCode() : ex.getExamLocation());
                m.put("status", ex.getStatus() != null ? ex.getStatus().name() : "SCHEDULED");
                out.add(m);
            }
            out.sort(Comparator.comparing(m -> String.valueOf(m.get("examDate"))));
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> gradeItem(StudentGradeDto g) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("courseCode", g.getCourseCode());
        item.put("courseName", g.getCourseName());
        item.put("credits", g.getCredits() != null ? g.getCredits() : 0);
        item.put("finalScore", g.getFinalScore());
        item.put("classification", g.getClassification());
        item.put("semester", g.getSemesterName());
        item.put("passed", Boolean.TRUE.equals(g.getPassed()) || (g.getFinalScore() != null && g.getFinalScore().compareTo(new BigDecimal("4.00")) >= 0));
        return item;
    }

    private String nz(String v, String d) { return (v == null || v.isBlank()) ? d : v; }

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


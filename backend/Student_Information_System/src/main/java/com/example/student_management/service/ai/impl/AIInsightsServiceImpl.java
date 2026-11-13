package com.example.student_management.service.ai.impl;

import com.example.student_management.dto.ai.AIInsightsDto;
import com.example.student_management.dto.ai.AIRecommendationDto;
import com.example.student_management.dto.ai.AISemesterInsightDto;
import com.example.student_management.dto.grade.StudentGradeDto;
import com.example.student_management.entity.academic.Course;
import com.example.student_management.entity.academic.Enrollment;
import com.example.student_management.entity.academic.EnrollmentStatus;
import com.example.student_management.entity.ai.AIUsageLog;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.ai.AIUsageLogRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.ai.AIContextService;
import com.example.student_management.service.ai.AIInsightsService;
import com.example.student_management.service.ai.AIProviderService;
import com.example.student_management.service.grade.GradebookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AIInsightsServiceImpl implements AIInsightsService {

    private static final BigDecimal PASS_THRESHOLD = new BigDecimal("4.00");
    private static final BigDecimal LOW_SCORE_THRESHOLD = new BigDecimal("6.50");
    private static final BigDecimal HIGH_SCORE_THRESHOLD = new BigDecimal("8.50");
    private static final BigDecimal TREND_DELTA = new BigDecimal("0.20");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private GradebookService gradebookService;

    @Autowired
    private AIContextService contextService;

    @Autowired
    private AIProviderService providerService;

    @Autowired
    private AIUsageLogRepository usageLogRepository;

    @Value("${app.ai.rate-limit-per-hour:60}")
    private int rateLimitPerHour;

    private final Map<Long, AIInsightsDto> insightsCache = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public AIInsightsDto getInsights(String username) {
        User user = getStudentUser(username);
        Student student = user.getStudent();
        AIInsightsDto cached = insightsCache.get(student.getId());
        if (cached != null) {
            return cached;
        }
        return generateAndLogInsights(user, student);
    }

    @Override
    @Transactional
    public AIInsightsDto refreshInsights(String username) {
        User user = getStudentUser(username);
        Student student = user.getStudent();

        long recentCalls = usageLogRepository.countByStudentIdAndCreatedAtAfter(
                student.getId(), LocalDateTime.now().minusHours(1));
        if (recentCalls >= rateLimitPerHour) {
            throw new BadRequestException("Bạn đã vượt quá giới hạn " + rateLimitPerHour + " lượt phân tích AI trong 1 giờ. Vui lòng thử lại sau.");
        }

        insightsCache.remove(student.getId());
        return generateAndLogInsights(user, student);
    }

    private AIInsightsDto generateAndLogInsights(User user, Student student) {
        long start = System.currentTimeMillis();
        List<StudentGradeDto> publishedGrades = gradebookService.getStudentPublishedGrades(user.getUsername());
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(student.getId());

        // 1. Tính toán tín chỉ đang theo học
        int activeCredits = 0;
        for (Enrollment e : enrollments) {
            if (e.getStatus() == EnrollmentStatus.ENROLLED && e.getCourseSection() != null) {
                Course course = e.getCourseSection().getCourse();
                if (course != null && course.getCredits() != null) {
                    activeCredits += course.getCredits();
                }
            }
        }

        // 2. Tính tổng GPA, tín chỉ tích lũy, môn thấp điểm & nhóm theo học kỳ
        int completedCredits = 0;
        int totalCompletedCourses = 0;
        int totalFailedCourses = 0;
        BigDecimal totalQualityPoints = BigDecimal.ZERO;
        int totalGradedCredits = 0;

        List<Map<String, Object>> lowScoreCourses = new ArrayList<>();
        List<StudentGradeDto> highScoreCourses = new ArrayList<>();
        Map<String, List<StudentGradeDto>> bySemester = new LinkedHashMap<>();

        for (StudentGradeDto g : publishedGrades) {
            String semName = g.getSemesterName() != null ? g.getSemesterName() : "Chưa rõ học kỳ";
            bySemester.computeIfAbsent(semName, k -> new ArrayList<>()).add(g);

            int credits = g.getCredits() != null ? g.getCredits() : 0;
            BigDecimal finalScore = g.getFinalScore();
            boolean passed = isPassed(g);

            if (passed) {
                completedCredits += credits;
                totalCompletedCourses++;
            } else {
                totalFailedCourses++;
            }

            if (finalScore != null && credits > 0) {
                totalQualityPoints = totalQualityPoints.add(finalScore.multiply(BigDecimal.valueOf(credits)));
                totalGradedCredits += credits;
            }

            if (!passed || (finalScore != null && finalScore.compareTo(LOW_SCORE_THRESHOLD) < 0)) {
                Map<String, Object> low = new LinkedHashMap<>();
                low.put("courseCode", g.getCourseCode());
                low.put("courseName", g.getCourseName());
                low.put("credits", credits);
                low.put("finalScore", finalScore);
                low.put("classification", g.getClassification());
                low.put("passed", passed);
                low.put("semesterName", semName);
                lowScoreCourses.add(low);
            } else if (finalScore != null && finalScore.compareTo(HIGH_SCORE_THRESHOLD) >= 0) {
                highScoreCourses.add(g);
            }
        }

        BigDecimal gpa10 = totalGradedCredits > 0
                ? totalQualityPoints.divide(BigDecimal.valueOf(totalGradedCredits), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal gpa4 = toGpa4(gpa10);
        String academicClassification = classifyGpa(gpa10, publishedGrades.isEmpty());

        // 3. Phân tích từng học kỳ & so sánh xu hướng
        List<AISemesterInsightDto> semesterInsights = buildSemesterInsights(bySemester);

        // 4. Tạo các đề xuất cải thiện dựa trên dữ liệu thật
        List<AIRecommendationDto> recommendations = buildRecommendations(
                publishedGrades, lowScoreCourses, highScoreCourses, semesterInsights, gpa10);

        // 5. Tạo nhận xét AI (External LLM nếu có API key, fallback nội bộ nếu chưa cấu hình)
        String aiSummary = buildAiSummary(student, user, gpa10, gpa4, completedCredits,
                totalCompletedCourses, totalFailedCourses, semesterInsights, lowScoreCourses, highScoreCourses, start);

        AIInsightsDto dto = AIInsightsDto.builder()
                .studentCode(student.getStudentCode())
                .fullName(student.getFullName())
                .department(student.getDepartment() != null ? student.getDepartment() : "Chưa cập nhật")
                .major(student.getMajor() != null ? student.getMajor() : "Chưa cập nhật")
                .className(student.getClassName() != null ? student.getClassName() : "Chưa cập nhật")
                .gpa10(gpa10)
                .gpa4(gpa4)
                .completedCredits(completedCredits)
                .activeCredits(activeCredits)
                .totalCompletedCourses(totalCompletedCourses)
                .totalFailedCourses(totalFailedCourses)
                .academicClassification(academicClassification)
                .semesterInsights(semesterInsights)
                .lowScoreCourses(lowScoreCourses)
                .aiSummary(aiSummary)
                .recommendations(recommendations)
                .generatedAt(LocalDateTime.now())
                .build();

        insightsCache.put(student.getId(), dto);
        return dto;
    }

    private List<AISemesterInsightDto> buildSemesterInsights(Map<String, List<StudentGradeDto>> bySemester) {
        List<AISemesterInsightDto> list = new ArrayList<>();
        BigDecimal prevGpa10 = null;

        for (Map.Entry<String, List<StudentGradeDto>> entry : bySemester.entrySet()) {
            String semesterName = entry.getKey();
            List<StudentGradeDto> items = entry.getValue();

            BigDecimal semPoints = BigDecimal.ZERO;
            int semGradedCredits = 0;
            int semEarnedCredits = 0;
            int passedCount = 0;
            int failedCount = 0;

            for (StudentGradeDto g : items) {
                int credits = g.getCredits() != null ? g.getCredits() : 0;
                if (isPassed(g)) {
                    semEarnedCredits += credits;
                    passedCount++;
                } else {
                    failedCount++;
                }
                if (g.getFinalScore() != null && credits > 0) {
                    semPoints = semPoints.add(g.getFinalScore().multiply(BigDecimal.valueOf(credits)));
                    semGradedCredits += credits;
                }
            }

            BigDecimal semGpa10 = semGradedCredits > 0
                    ? semPoints.divide(BigDecimal.valueOf(semGradedCredits), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String trend;
            if (prevGpa10 == null) {
                trend = "FIRST";
            } else {
                BigDecimal diff = semGpa10.subtract(prevGpa10);
                if (diff.compareTo(TREND_DELTA) >= 0) {
                    trend = "UP";
                } else if (diff.compareTo(TREND_DELTA.negate()) <= 0) {
                    trend = "DOWN";
                } else {
                    trend = "STABLE";
                }
            }

            list.add(AISemesterInsightDto.builder()
                    .semesterName(semesterName)
                    .gpa10(semGpa10)
                    .gpa4(toGpa4(semGpa10))
                    .creditsEarned(semEarnedCredits)
                    .coursesCompleted(passedCount)
                    .coursesFailed(failedCount)
                    .trend(trend)
                    .build());

            prevGpa10 = semGpa10;
        }
        return list;
    }

    private List<AIRecommendationDto> buildRecommendations(
            List<StudentGradeDto> publishedGrades,
            List<Map<String, Object>> lowScoreCourses,
            List<StudentGradeDto> highScoreCourses,
            List<AISemesterInsightDto> semesterInsights,
            BigDecimal gpa10
    ) {
        List<AIRecommendationDto> recs = new ArrayList<>();

        if (publishedGrades.isEmpty()) {
            recs.add(AIRecommendationDto.builder()
                    .type("ACTION")
                    .title("Chưa có dữ liệu bảng điểm chính thức")
                    .description("Hệ thống chưa ghi nhận học phần nào được công bố điểm. Hãy hoàn thành các bài kiểm tra và theo dõi khi giảng viên công bố điểm.")
                    .priority("MEDIUM")
                    .build());
            return recs;
        }

        // Môn rớt hoặc điểm thấp
        for (Map<String, Object> low : lowScoreCourses) {
            boolean passed = Boolean.TRUE.equals(low.get("passed"));
            String code = String.valueOf(low.get("courseCode"));
            String name = String.valueOf(low.get("courseName"));
            Object score = low.get("finalScore");

            if (!passed) {
                recs.add(AIRecommendationDto.builder()
                        .type("WARNING")
                        .title("Cần học lại môn " + name + " (" + code + ")")
                        .description("Môn học chưa đạt yêu cầu (Điểm: " + (score != null ? score : "N/A") + "). Bạn nên ưu tiên đăng ký học lại ở học kỳ tới để tích lũy đủ tín chỉ.")
                        .relatedCourseCode(code)
                        .priority("HIGH")
                        .build());
            } else {
                recs.add(AIRecommendationDto.builder()
                        .type("IMPROVEMENT")
                        .title("Cần cải thiện môn " + name + " (" + code + ")")
                        .description("Kết quả hiện tại là " + score + "/10, thấp hơn mức khá (6.5). Nên ôn tập lại kiến thức nền tảng của môn này hoặc cân nhắc học cải thiện nếu muốn nâng GPA.")
                        .relatedCourseCode(code)
                        .priority("MEDIUM")
                        .build());
            }
        }

        // Phân tích xu hướng giảm giữa các học kỳ
        if (semesterInsights.size() >= 2) {
            AISemesterInsightDto latest = semesterInsights.get(semesterInsights.size() - 1);
            AISemesterInsightDto prev = semesterInsights.get(semesterInsights.size() - 2);
            if ("DOWN".equals(latest.getTrend())) {
                recs.add(AIRecommendationDto.builder()
                        .type("WARNING")
                        .title("Xu hướng điểm giảm ở " + latest.getSemesterName())
                        .description("GPA học kỳ gần nhất (" + latest.getGpa10() + ") giảm so với " + prev.getSemesterName() + " (" + prev.getGpa10() + "). Hãy điều chỉnh khối lượng tín chỉ hoặc lập kế hoạch ôn tập sớm hơn.")
                        .priority("HIGH")
                        .build());
            } else if ("UP".equals(latest.getTrend())) {
                recs.add(AIRecommendationDto.builder()
                        .type("STRENGTH")
                        .title("Tiến độ tích cực qua các học kỳ")
                        .description("GPA của bạn tăng từ " + prev.getGpa10() + " (" + prev.getSemesterName() + ") lên " + latest.getGpa10() + " (" + latest.getSemesterName() + "). Tiếp tục duy trì phương pháp học hiện tại.")
                        .priority("LOW")
                        .build());
            }
        }

        // Thế mạnh
        if (!highScoreCourses.isEmpty()) {
            StudentGradeDto top = highScoreCourses.stream()
                    .max(Comparator.comparing(g -> g.getFinalScore() != null ? g.getFinalScore() : BigDecimal.ZERO))
                    .orElse(highScoreCourses.get(0));
            recs.add(AIRecommendationDto.builder()
                    .type("STRENGTH")
                    .title("Thế mạnh nổi bật ở môn " + top.getCourseName())
                    .description("Bạn đạt kết quả xuất sắc " + top.getFinalScore() + "/10 ở môn " + top.getCourseCode() + " (cùng " + highScoreCourses.size() + " môn đạt từ 8.5 trở lên).")
                    .relatedCourseCode(top.getCourseCode())
                    .priority("LOW")
                    .build());
        }

        // Mục tiêu GPA
        if (gpa10.compareTo(new BigDecimal("8.00")) >= 0) {
            recs.add(AIRecommendationDto.builder()
                    .type("STRENGTH")
                    .title("Duy trì thành tích học tập xuất sắc/giỏi")
                    .description("GPA tích lũy hiện tại đạt " + gpa10 + "/10. Bạn đang giữ vững phong độ tốt và có cơ hội xét học bổng khuyến khích học tập.")
                    .priority("LOW")
                    .build());
        }

        return recs;
    }

    private String buildAiSummary(
            Student student,
            User user,
            BigDecimal gpa10,
            BigDecimal gpa4,
            int completedCredits,
            int totalCompletedCourses,
            int totalFailedCourses,
            List<AISemesterInsightDto> semesterInsights,
            List<Map<String, Object>> lowScoreCourses,
            List<StudentGradeDto> highScoreCourses,
            long startMs
    ) {
        String systemContext = contextService.buildSystemContextPrompt(student, user);
        String prompt = "Hãy viết một đoạn nhận xét tổng quan ngắn gọn (3-5 câu bằng tiếng Việt, dùng Markdown) phân tích tình hình học tập thực tế của sinh viên dựa trên dữ liệu SIS: GPA 10 = "
                + gpa10 + ", GPA 4 = " + gpa4 + ", Tín chỉ tích lũy = " + completedCredits
                + ", Số môn đạt = " + totalCompletedCourses + ", Số môn chưa đạt = " + totalFailedCourses
                + ", Số học kỳ đã có điểm = " + semesterInsights.size()
                + ". Chỉ nêu số liệu chính xác, không tự bịa.";

        AIProviderService.AIProviderResult external = providerService.generateExternalAnalysis(systemContext, prompt);

        // Ghi log sử dụng AI
        try {
            usageLogRepository.save(AIUsageLog.builder()
                    .student(student)
                    .featureType("INSIGHTS")
                    .provider(external.getProviderUsed())
                    .model(external.getModelUsed())
                    .status("SUCCESS")
                    .latencyMs(System.currentTimeMillis() - startMs)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception ignored) {}

        if (external.isSuccess() && external.getContent() != null && !external.getContent().isBlank()) {
            return external.getContent();
        }

        // Fallback tổng hợp tiếng Việt chuẩn xác từ dữ liệu thật
        if (totalCompletedCourses == 0 && totalFailedCourses == 0) {
            return "Hiện tại hệ thống **chưa có dữ liệu bảng điểm chính thức** nào được công bố cho **"
                    + student.getFullName() + "**. Sau khi giảng viên công bố điểm các học phần, hệ thống sẽ tự động phân tích GPA và xu hướng học tập của bạn.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Sinh viên **").append(student.getFullName()).append("** (").append(student.getStudentCode())
                .append(") hiện đã tích lũy **").append(completedCredits).append(" tín chỉ** qua **")
                .append(totalCompletedCourses).append(" môn học đạt** với GPA tích lũy **")
                .append(gpa10).append("/10** (**").append(gpa4).append("/4.0**). ");

        if (!highScoreCourses.isEmpty()) {
            sb.append("Bạn thể hiện thế mạnh rõ rệt với **").append(highScoreCourses.size())
                    .append(" môn đạt từ 8.5 trở lên**. ");
        }
        if (totalFailedCourses > 0) {
            sb.append("Tuy nhiên cần lưu ý có **").append(totalFailedCourses)
                    .append(" môn chưa đạt** cần ưu tiên kế hoạch học lại. ");
        } else if (!lowScoreCourses.isEmpty()) {
            sb.append("Có **").append(lowScoreCourses.size())
                    .append(" môn điểm dưới 6.5** nên củng cố lại kiến thức để nâng cao GPA tổng thể. ");
        }
        if (semesterInsights.size() >= 2) {
            AISemesterInsightDto latest = semesterInsights.get(semesterInsights.size() - 1);
            if ("UP".equals(latest.getTrend())) {
                sb.append("Xu hướng học tập ở **").append(latest.getSemesterName()).append("** đang **cải thiện tích cực**.");
            } else if ("DOWN".equals(latest.getTrend())) {
                sb.append("Xu hướng điểm ở **").append(latest.getSemesterName()).append("** có dấu hiệu **giảm nhẹ**, cần cân đối lại thời gian học tập.");
            }
        }
        return sb.toString().trim();
    }

    private boolean isPassed(StudentGradeDto g) {
        return Boolean.TRUE.equals(g.getPassed())
                || (g.getFinalScore() != null && g.getFinalScore().compareTo(PASS_THRESHOLD) >= 0);
    }

    private BigDecimal toGpa4(BigDecimal gpa10) {
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

    private String classifyGpa(BigDecimal gpa10, boolean empty) {
        if (empty || gpa10 == null) return "Chưa có dữ liệu";
        double val = gpa10.doubleValue();
        if (val >= 9.0) return "Xuất sắc";
        if (val >= 8.0) return "Giỏi";
        if (val >= 7.0) return "Khá";
        if (val >= 5.0) return "Trung bình";
        return "Yếu";
    }

    private User getStudentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
        if (user.getRole() != Role.STUDENT || user.getStudent() == null) {
            throw new ForbiddenException("Chỉ tài khoản sinh viên mới có quyền truy cập AI Learning Insights");
        }
        return user;
    }
}


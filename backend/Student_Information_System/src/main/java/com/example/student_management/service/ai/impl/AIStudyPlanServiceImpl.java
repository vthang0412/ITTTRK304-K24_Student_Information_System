package com.example.student_management.service.ai.impl;

import com.example.student_management.dto.ai.*;
import com.example.student_management.entity.academic.Course;
import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.academic.Enrollment;
import com.example.student_management.entity.academic.EnrollmentStatus;
import com.example.student_management.entity.ai.AIStudyPlan;
import com.example.student_management.entity.ai.AIStudyPlanItem;
import com.example.student_management.entity.ai.AIUsageLog;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.CourseRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.ai.AIStudyPlanItemRepository;
import com.example.student_management.repository.ai.AIStudyPlanRepository;
import com.example.student_management.repository.ai.AIUsageLogRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.ai.AIProviderService;
import com.example.student_management.service.ai.AIStudyPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIStudyPlanServiceImpl implements AIStudyPlanService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AIStudyPlanRepository studyPlanRepository;

    @Autowired
    private AIStudyPlanItemRepository studyPlanItemRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AIProviderService providerService;

    @Autowired
    private AIUsageLogRepository usageLogRepository;

    @Value("${app.ai.rate-limit-per-hour:60}")
    private int rateLimitPerHour;

    @Override
    @Transactional
    public StudyPlanDto createStudyPlan(String username, CreateStudyPlanRequestDto request) {
        long startTime = System.currentTimeMillis();
        User user = getStudentUser(username);
        Student student = user.getStudent();

        // 1. Kiểm tra giới hạn tần suất (Rate limit)
        long recentCalls = usageLogRepository.countByStudentIdAndCreatedAtAfter(
                student.getId(), LocalDateTime.now().minusHours(1));
        if (recentCalls >= rateLimitPerHour) {
            throw new BadRequestException("Bạn đã dùng hết " + rateLimitPerHour + " lượt gọi AI trong 1 giờ. Vui lòng chờ ít phút.");
        }

        // 2. Chuẩn hóa ngày bắt đầu và kết thúc
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : startDate.plusDays(6);
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("Ngày kết thúc (" + endDate + ") không thể trước ngày bắt đầu (" + startDate + ")");
        }

        String title = (request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle().trim()
                : "Kế hoạch học tập tuần (" + startDate + " đến " + endDate + ")";

        String goal = (request.getGoal() != null && !request.getGoal().isBlank())
                ? request.getGoal().trim()
                : "Nâng cao kết quả học tập và hoàn thành các mục tiêu môn học đúng tiến độ.";

        // 3. Xác định các môn học cần tập trung
        List<Course> focusCourses = resolveFocusCourses(student, request.getFocusCourseIds());

        // 4. Tạo đối tượng kế hoạch
        AIStudyPlan plan = AIStudyPlan.builder()
                .student(student)
                .title(title)
                .goal(goal)
                .startDate(startDate)
                .endDate(endDate)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();

        AIStudyPlan savedPlan = studyPlanRepository.save(plan);

        // 5. Tạo danh sách nhiệm vụ học tập (Study Plan Items)
        List<AIStudyPlanItem> items = new ArrayList<>();
        if (request.getManualItems() != null && !request.getManualItems().isEmpty()) {
            for (StudyPlanItemDto m : request.getManualItems()) {
                Course course = null;
                if (m.getCourseId() != null) {
                    course = courseRepository.findById(m.getCourseId()).orElse(null);
                }
                LocalDate taskDate = m.getStudyDate() != null ? m.getStudyDate() : startDate;
                items.add(AIStudyPlanItem.builder()
                        .studyPlan(savedPlan)
                        .course(course)
                        .title(m.getTitle() != null ? m.getTitle() : "Nhiệm vụ tự học")
                        .description(m.getDescription())
                        .studyDate(taskDate)
                        .durationMinutes(m.getDurationMinutes() != null ? m.getDurationMinutes() : 60)
                        .status("PENDING")
                        .build());
            }
        } else {
            // Tự động phân bổ nhiệm vụ theo ngày dựa trên các môn cần tập trung
            items = generateAutoStudyItems(savedPlan, startDate, endDate, focusCourses, request.getTargetHoursPerDay());
        }

        savedPlan.setItems(items);
        studyPlanItemRepository.saveAll(items);

        // 6. Gọi AI để sinh lời khuyên học tập cá nhân hóa
        String aiAdvice = generateStudyAdvice(student, savedPlan, focusCourses, request.getCustomNote(), startTime);

        return mapToDto(savedPlan, aiAdvice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyPlanDto> getStudentStudyPlans(String username) {
        User user = getStudentUser(username);
        Student student = user.getStudent();
        List<AIStudyPlan> plans = studyPlanRepository.findByStudentIdOrderByCreatedAtDesc(student.getId());
        return plans.stream().map(p -> mapToDto(p, null)).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public StudyPlanDto getStudyPlanDetail(String username, Long planId) {
        User user = getStudentUser(username);
        Student student = user.getStudent();
        AIStudyPlan plan = getOwnedPlan(planId, student);
        return mapToDto(plan, null);
    }

    @Override
    @Transactional
    public StudyPlanDto updateStudyPlan(String username, Long planId, UpdateStudyPlanRequestDto request) {
        User user = getStudentUser(username);
        Student student = user.getStudent();
        AIStudyPlan plan = getOwnedPlan(planId, student);

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            plan.setTitle(request.getTitle().trim());
        }
        if (request.getGoal() != null) {
            plan.setGoal(request.getGoal().trim());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String st = request.getStatus().toUpperCase();
            if (!List.of("ACTIVE", "COMPLETED", "ARCHIVED").contains(st)) {
                throw new BadRequestException("Trạng thái kế hoạch không hợp lệ. Chỉ chấp nhận: ACTIVE, COMPLETED, ARCHIVED");
            }
            plan.setStatus(st);
        }

        AIStudyPlan updated = studyPlanRepository.save(plan);
        return mapToDto(updated, null);
    }

    @Override
    @Transactional
    public void deleteStudyPlan(String username, Long planId) {
        User user = getStudentUser(username);
        Student student = user.getStudent();
        AIStudyPlan plan = getOwnedPlan(planId, student);
        studyPlanRepository.delete(plan);
    }

    @Override
    @Transactional
    public StudyPlanItemDto updatePlanItem(String username, Long planId, Long itemId, UpdateStudyPlanItemRequestDto request) {
        User user = getStudentUser(username);
        Student student = user.getStudent();
        AIStudyPlan plan = getOwnedPlan(planId, student);

        AIStudyPlanItem item = studyPlanItemRepository.findByIdAndStudyPlanId(itemId, plan.getId())
                .orElseThrow(() -> new NotFoundException("Nhiệm vụ không tồn tại trong kế hoạch này"));

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String st = request.getStatus().toUpperCase();
            if (!List.of("PENDING", "COMPLETED", "SKIPPED").contains(st)) {
                throw new BadRequestException("Trạng thái nhiệm vụ không hợp lệ. Chỉ chấp nhận: PENDING, COMPLETED, SKIPPED");
            }
            item.setStatus(st);
        }
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            item.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            item.setDescription(request.getDescription().trim());
        }
        if (request.getStudyDate() != null) {
            item.setStudyDate(request.getStudyDate());
        }
        if (request.getDurationMinutes() != null && request.getDurationMinutes() > 0) {
            item.setDurationMinutes(request.getDurationMinutes());
        }

        AIStudyPlanItem updatedItem = studyPlanItemRepository.save(item);

        // Tự động chuyển kế hoạch sang COMPLETED nếu tất cả nhiệm vụ đã hoàn thành
        boolean allDone = plan.getItems().stream().allMatch(it -> "COMPLETED".equalsIgnoreCase(it.getStatus()));
        if (allDone && !plan.getItems().isEmpty() && "ACTIVE".equals(plan.getStatus())) {
            plan.setStatus("COMPLETED");
            studyPlanRepository.save(plan);
        }

        return mapItemToDto(updatedItem);
    }

    /**
     * Xác định danh sách môn học cần ưu tiên ôn tập
     */
    private List<Course> resolveFocusCourses(Student student, List<Long> focusCourseIds) {
        if (focusCourseIds != null && !focusCourseIds.isEmpty()) {
            List<Course> byIds = courseRepository.findAllById(focusCourseIds);
            if (!byIds.isEmpty()) return byIds;
        }

        // Nếu sinh viên không chọn môn cụ thể, lấy các môn sinh viên đang theo học
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(student.getId());
        List<Course> enrolledCourses = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        for (Enrollment e : enrollments) {
            if (e.getStatus() == EnrollmentStatus.ENROLLED && e.getCourseSection() != null) {
                Course c = e.getCourseSection().getCourse();
                if (c != null && seenIds.add(c.getId())) {
                    enrolledCourses.add(c);
                }
            }
        }

        if (!enrolledCourses.isEmpty()) {
            return enrolledCourses;
        }

        // Fallback: lấy tối đa 3 môn học bất kỳ trong hệ thống
        return courseRepository.findAll().stream().limit(3).collect(Collectors.toList());
    }

    /**
     * Thuật toán tự động sinh nhiệm vụ học tập theo ngày
     */
    private List<AIStudyPlanItem> generateAutoStudyItems(
            AIStudyPlan plan,
            LocalDate startDate,
            LocalDate endDate,
            List<Course> courses,
            Integer targetHoursPerDay
    ) {
        List<AIStudyPlanItem> items = new ArrayList<>();
        int duration = (targetHoursPerDay != null && targetHoursPerDay > 0) ? targetHoursPerDay * 60 : 90;
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        String[] taskTemplates = {
                "Ôn tập lý thuyết & slide bài giảng chương mới",
                "Làm bài tập thực hành & viết code ứng dụng",
                "Đọc tài liệu chuyên khảo & chuẩn bị câu hỏi thảo luận",
                "Luyện tập giải đề mẫu và trắc nghiệm củng cố",
                "Rà soát lỗi bài tập & review lại nội dung trọng tâm",
                "Chuẩn bị bài thuyết trình & tổng kết chuyên đề tuần",
                "Tổng kết tiến độ tuần và hoàn thiện ghi chú"
        };

        int courseIndex = 0;
        for (int i = 0; i < daysBetween; i++) {
            LocalDate currentDate = startDate.plusDays(i);
            Course assignedCourse = courses.isEmpty() ? null : courses.get(courseIndex % courses.size());
            courseIndex++;

            String courseName = assignedCourse != null ? assignedCourse.getCourseName() : "Chung";
            String template = taskTemplates[i % taskTemplates.length];
            String title = template + " (" + courseName + ")";
            String desc = "Dành " + duration + " phút để " + template.toLowerCase() + " môn " + courseName
                    + ". Chú ý ghi lại các thắc mắc để hỏi giảng viên.";

            items.add(AIStudyPlanItem.builder()
                    .studyPlan(plan)
                    .course(assignedCourse)
                    .title(title)
                    .description(desc)
                    .studyDate(currentDate)
                    .durationMinutes(duration)
                    .status("PENDING")
                    .build());
        }

        return items;
    }

    /**
     * Gọi AI Provider để tạo lời khuyên phân bổ thời gian học thông minh
     */
    private String generateStudyAdvice(Student student, AIStudyPlan plan, List<Course> focusCourses, String customNote, long startTime) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Sinh viên: ").append(student.getFullName()).append(" (").append(student.getStudentCode()).append(").\n");
        prompt.append("Kế hoạch: ").append(plan.getTitle()).append(", Mục tiêu: ").append(plan.getGoal()).append(".\n");
        prompt.append("Thời gian: từ ").append(plan.getStartDate()).append(" đến ").append(plan.getEndDate())
                .append(" (").append(plan.getItems().size()).append(" nhiệm vụ).\n");
        if (focusCourses != null && !focusCourses.isEmpty()) {
            prompt.append("Các môn trọng tâm: ")
                    .append(focusCourses.stream().map(Course::getCourseName).collect(Collectors.joining(", ")))
                    .append(".\n");
        }
        if (customNote != null && !customNote.isBlank()) {
            prompt.append("Nguyện vọng sinh viên: ").append(customNote).append(".\n");
        }

        String sysInstruction = "Bạn là AI Study Planner của hệ thống SIS. Hãy viết một đoạn hướng dẫn học tập ngắn gọn (3-4 câu tiếng Việt, định dạng Markdown) "
                + "khích lệ tinh thần và đưa ra phương pháp học tập hiệu quả (như kỹ thuật Pomodoro, phân bổ thời gian nghỉ ngơi) cho kế hoạch học tập này.";

        AIProviderService.AIProviderResult external = providerService.generateExternalAnalysis(sysInstruction, prompt.toString());

        // Ghi log sử dụng AI
        try {
            usageLogRepository.save(AIUsageLog.builder()
                    .student(student)
                    .featureType("PLANNER")
                    .provider(external.getProviderUsed())
                    .model(external.getModelUsed())
                    .status("SUCCESS")
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception ignored) {}

        if (external.isSuccess() && external.getContent() != null && !external.getContent().isBlank()) {
            return external.getContent();
        }

        // Fallback nội bộ
        return "Chào **" + student.getFullName() + "**, kế hoạch tuần của bạn đã được thiết lập với **"
                + plan.getItems().size() + " nhiệm vụ học tập**. Hãy áp dụng kỹ thuật **Pomodoro (25 phút học, 5 phút nghỉ)** "
                + "để duy trì sự tập trung cao độ và nhớ đánh dấu hoàn thành sau mỗi buổi học nhé!";
    }

    private StudyPlanDto mapToDto(AIStudyPlan plan, String aiAdvice) {
        int totalTasks = plan.getItems() != null ? plan.getItems().size() : 0;
        long completedTasks = (plan.getItems() != null)
                ? plan.getItems().stream().filter(it -> "COMPLETED".equalsIgnoreCase(it.getStatus())).count()
                : 0;

        double rate = totalTasks == 0 ? 0.0 : Math.round(((double) completedTasks / totalTasks) * 1000.0) / 10.0;
        int totalMinutes = (plan.getItems() != null)
                ? plan.getItems().stream().mapToInt(it -> it.getDurationMinutes() != null ? it.getDurationMinutes() : 0).sum()
                : 0;

        List<StudyPlanItemDto> itemDtos = (plan.getItems() != null)
                ? plan.getItems().stream().map(this::mapItemToDto).collect(Collectors.toList())
                : Collections.emptyList();

        return StudyPlanDto.builder()
                .id(plan.getId())
                .studentId(plan.getStudent().getId())
                .studentCode(plan.getStudent().getStudentCode())
                .fullName(plan.getStudent().getFullName())
                .title(plan.getTitle())
                .goal(plan.getGoal())
                .startDate(plan.getStartDate())
                .endDate(plan.getEndDate())
                .status(plan.getStatus())
                .totalTasks(totalTasks)
                .completedTasks((int) completedTasks)
                .completionRate(rate)
                .totalStudyMinutes(totalMinutes)
                .items(itemDtos)
                .aiGeneratedAdvice(aiAdvice)
                .createdAt(plan.getCreatedAt())
                .build();
    }

    private StudyPlanItemDto mapItemToDto(AIStudyPlanItem item) {
        return StudyPlanItemDto.builder()
                .id(item.getId())
                .courseId(item.getCourse() != null ? item.getCourse().getId() : null)
                .courseCode(item.getCourse() != null ? item.getCourse().getCourseCode() : null)
                .courseName(item.getCourse() != null ? item.getCourse().getCourseName() : null)
                .title(item.getTitle())
                .description(item.getDescription())
                .studyDate(item.getStudyDate())
                .durationMinutes(item.getDurationMinutes())
                .status(item.getStatus())
                .build();
    }

    private AIStudyPlan getOwnedPlan(Long planId, Student student) {
        return studyPlanRepository.findByIdAndStudentId(planId, student.getId())
                .orElseThrow(() -> new NotFoundException("Kế hoạch học tập không tồn tại hoặc bạn không có quyền truy cập"));
    }

    private User getStudentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
        if (user.getRole() != Role.STUDENT || user.getStudent() == null) {
            throw new ForbiddenException("Chỉ tài khoản sinh viên mới có quyền sử dụng AI Study Planner");
        }
        return user;
    }
}


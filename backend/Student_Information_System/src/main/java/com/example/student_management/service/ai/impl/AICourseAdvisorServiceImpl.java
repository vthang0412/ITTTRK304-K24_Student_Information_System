package com.example.student_management.service.ai.impl;

import com.example.student_management.dto.ai.*;
import com.example.student_management.dto.grade.StudentGradeDto;
import com.example.student_management.entity.academic.*;
import com.example.student_management.entity.ai.AIUsageLog;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.CourseRepository;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.ai.AIUsageLogRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.ai.AICourseAdvisorService;
import com.example.student_management.service.ai.AIProviderService;
import com.example.student_management.service.grade.GradebookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AICourseAdvisorServiceImpl implements AICourseAdvisorService {

    public static final int MAX_CREDITS_PER_SEMESTER = 24;

    // Ma trận môn tiên quyết chuẩn ngành CNTT
    private static final Map<String, List<String>> PREREQUISITES = Map.of(
            "INT3401", List.of("INT2204"),                     // Cấu trúc dữ liệu & giải thuật <- Lập trình hướng đối tượng
            "INT3507", List.of("INT2204"),                     // Công nghệ phần mềm <- Lập trình hướng đối tượng
            "INT3303", List.of("INT2204", "INT3306"),          // Lập trình web <- OOP, CSDL
            "INT3405", List.of("INT3401", "MAT1013"),          // Trí tuệ nhân tạo <- CTDL&GT, Đại số tuyến tính
            "INT3501", List.of("INT3306"),                     // Phân tích thiết kế hệ thống <- CSDL
            "INT3305", List.of("INT3102"),                     // An toàn thông tin <- Mạng máy tính
            "INT3202", List.of("INT3102", "INT3401"),          // Hệ điều hành <- Mạng máy tính, CTDL&GT
            "INT3301", List.of("INT2204")                      // Ngôn ngữ lập trình <- OOP
    );

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private GradebookService gradebookService;

    @Autowired
    private AIProviderService providerService;

    @Autowired
    private AIUsageLogRepository usageLogRepository;

    @Value("${app.ai.rate-limit-per-hour:60}")
    private int rateLimitPerHour;

    private final Map<Long, CourseAdvisorResponseDto> advisorCache = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public CourseAdvisorResponseDto getCourseRecommendations(String username) {
        User user = getStudentUser(username);
        Student student = user.getStudent();
        CourseAdvisorResponseDto cached = advisorCache.get(student.getId());
        if (cached != null) {
            return cached;
        }
        return generateCourseAdvice(user, student, null);
    }

    @Override
    @Transactional
    public CourseAdvisorResponseDto recommendCoursesWithPreferences(String username, CourseAdvisorRequestDto request) {
        User user = getStudentUser(username);
        Student student = user.getStudent();

        long recentCalls = usageLogRepository.countByStudentIdAndCreatedAtAfter(
                student.getId(), LocalDateTime.now().minusHours(1));
        if (recentCalls >= rateLimitPerHour) {
            throw new BadRequestException("Bạn đã dùng hết " + rateLimitPerHour + " lượt gọi AI trong 1 giờ. Vui lòng chờ ít phút.");
        }

        advisorCache.remove(student.getId());
        return generateCourseAdvice(user, student, request);
    }

    private CourseAdvisorResponseDto generateCourseAdvice(User user, Student student, CourseAdvisorRequestDto preferences) {
        long startTime = System.currentTimeMillis();

        // 1. Lấy dữ liệu học tập thực tế từ SIS
        List<StudentGradeDto> publishedGrades = gradebookService.getStudentPublishedGrades(user.getUsername());
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(student.getId());

        Set<String> completedCourseCodes = new HashSet<>();
        Set<String> failedCourseCodes = new HashSet<>();
        int accumulatedCredits = 0;

        for (StudentGradeDto g : publishedGrades) {
            boolean passed = Boolean.TRUE.equals(g.getPassed())
                    || (g.getFinalScore() != null && g.getFinalScore().compareTo(new BigDecimal("4.00")) >= 0);
            if (passed) {
                if (g.getCourseCode() != null) completedCourseCodes.add(g.getCourseCode());
                if (g.getCredits() != null) accumulatedCredits += g.getCredits();
            } else {
                if (g.getCourseCode() != null) failedCourseCodes.add(g.getCourseCode());
            }
        }

        // Lấy danh sách các môn và lớp đang học trong kỳ
        Set<String> currentlyEnrolledCourseCodes = new HashSet<>();
        List<CourseSection> activeSections = new ArrayList<>();
        int activeCredits = 0;

        for (Enrollment e : enrollments) {
            if (e.getStatus() == EnrollmentStatus.ENROLLED && e.getCourseSection() != null) {
                CourseSection sec = e.getCourseSection();
                // Bỏ qua các lớp học phần đã hoàn thành, bị hủy hoặc thuộc môn sinh viên đã thi đạt
                if (sec.getStatus() == CourseSectionStatus.COMPLETED || sec.getStatus() == CourseSectionStatus.CANCELLED) {
                    continue;
                }
                Course c = sec.getCourse();
                if (c != null && completedCourseCodes.contains(c.getCourseCode())) {
                    continue;
                }
                activeSections.add(sec);
                if (c != null) {
                    if (c.getCourseCode() != null) currentlyEnrolledCourseCodes.add(c.getCourseCode());
                    if (c.getCredits() != null) activeCredits += c.getCredits();
                }
            }
        }

        // 2. Lấy tất cả các môn học trong chương trình đào tạo
        List<Course> allCourses = courseRepository.findAll().stream()
                .filter(c -> c.getStatus() == AccountStatus.ACTIVE)
                .collect(Collectors.toList());

        List<CourseRecommendationItemDto> recommendedList = new ArrayList<>();
        List<CourseRecommendationItemDto> notRecommendedList = new ArrayList<>();

        for (Course course : allCourses) {
            String code = course.getCourseCode();
            List<String> prereqs = PREREQUISITES.getOrDefault(code, List.of());
            List<String> missingPrereqs = prereqs.stream()
                    .filter(p -> !completedCourseCodes.contains(p))
                    .collect(Collectors.toList());
            boolean prereqSatisfied = missingPrereqs.isEmpty();

            // Lấy các lớp học phần của môn này
            List<CourseSection> sections = courseSectionRepository.findByCourseId(course.getId());
            List<CourseSectionOptionDto> sectionOptions = buildSectionOptions(sections, activeSections);

            boolean hasOpenSection = sectionOptions.stream().anyMatch(CourseSectionOptionDto::isOpen);
            boolean hasConflictFreeSection = sectionOptions.stream()
                    .anyMatch(s -> s.isOpen() && !s.isHasScheduleConflict() && s.getRemainingSeats() > 0);

            // Trường hợp 1: Đã hoàn thành môn học
            if (completedCourseCodes.contains(code)) {
                notRecommendedList.add(CourseRecommendationItemDto.builder()
                        .courseId(course.getId())
                        .courseCode(code)
                        .courseName(course.getCourseName())
                        .credits(course.getCredits())
                        .prerequisites(prereqs)
                        .prerequisitesSatisfied(true)
                        .missingPrerequisites(List.of())
                        .status("ALREADY_COMPLETED")
                        .priority("NONE")
                        .reason("Bạn đã hoàn thành và đạt điểm môn học này.")
                        .availableSections(sectionOptions)
                        .build());
                continue;
            }

            // Trường hợp 2: Đang theo học trong kỳ này
            if (currentlyEnrolledCourseCodes.contains(code)) {
                notRecommendedList.add(CourseRecommendationItemDto.builder()
                        .courseId(course.getId())
                        .courseCode(code)
                        .courseName(course.getCourseName())
                        .credits(course.getCredits())
                        .prerequisites(prereqs)
                        .prerequisitesSatisfied(true)
                        .missingPrerequisites(List.of())
                        .status("CURRENTLY_ENROLLED")
                        .priority("NONE")
                        .reason("Bạn đang theo học môn này trong học kỳ hiện tại.")
                        .availableSections(sectionOptions)
                        .build());
                continue;
            }

            // Trường hợp 3: Chưa hoàn thành môn tiên quyết
            if (!prereqSatisfied) {
                notRecommendedList.add(CourseRecommendationItemDto.builder()
                        .courseId(course.getId())
                        .courseCode(code)
                        .courseName(course.getCourseName())
                        .credits(course.getCredits())
                        .prerequisites(prereqs)
                        .prerequisitesSatisfied(false)
                        .missingPrerequisites(missingPrereqs)
                        .status("PREREQUISITE_NOT_MET")
                        .priority("LOW")
                        .reason("Chưa đủ điều kiện tiên quyết. Cần hoàn thành các môn trước: " + String.join(", ", missingPrereqs) + ".")
                        .availableSections(sectionOptions)
                        .build());
                continue;
            }

            // Trường hợp 4: Môn học từng bị rớt (Ưu tiên học lại)
            if (failedCourseCodes.contains(code)) {
                String retakeReason = hasConflictFreeSection
                        ? "Môn học chưa đạt yêu cầu. Đã có lớp mở phù hợp, bạn nên ưu tiên đăng ký học lại sớm nhất để tích lũy tín chỉ."
                        : (hasOpenSection ? "Môn học cần học lại nhưng các lớp mở đang bị trùng lịch hoặc hết chỗ." : "Môn học cần học lại nhưng hiện tại chưa có lớp học phần nào mở.");

                recommendedList.add(0, CourseRecommendationItemDto.builder() // Đưa lên đầu danh sách
                        .courseId(course.getId())
                        .courseCode(code)
                        .courseName(course.getCourseName())
                        .credits(course.getCredits())
                        .prerequisites(prereqs)
                        .prerequisitesSatisfied(true)
                        .missingPrerequisites(List.of())
                        .status("MUST_RETAKE")
                        .priority("HIGH")
                        .reason(retakeReason)
                        .availableSections(sectionOptions)
                        .build());
                continue;
            }

            // Trường hợp 5: Môn học đủ điều kiện tiên quyết
            if (!hasOpenSection) {
                notRecommendedList.add(CourseRecommendationItemDto.builder()
                        .courseId(course.getId())
                        .courseCode(code)
                        .courseName(course.getCourseName())
                        .credits(course.getCredits())
                        .prerequisites(prereqs)
                        .prerequisitesSatisfied(true)
                        .missingPrerequisites(List.of())
                        .status("SECTIONS_FULL")
                        .priority("LOW")
                        .reason("Môn học đủ điều kiện tiên quyết nhưng hiện tại chưa mở lớp học phần cho học kỳ tới.")
                        .availableSections(sectionOptions)
                        .build());
            } else if (!hasConflictFreeSection) {
                notRecommendedList.add(CourseRecommendationItemDto.builder()
                        .courseId(course.getId())
                        .courseCode(code)
                        .courseName(course.getCourseName())
                        .credits(course.getCredits())
                        .prerequisites(prereqs)
                        .prerequisitesSatisfied(true)
                        .missingPrerequisites(List.of())
                        .status("SCHEDULE_CONFLICT")
                        .priority("MEDIUM")
                        .reason("Các lớp học phần đang mở của môn này đều bị trùng lịch học với các lớp bạn đã đăng ký hoặc đã hết chỗ.")
                        .availableSections(sectionOptions)
                        .build());
            } else {
                // Đủ điều kiện và có lớp hợp lệ
                String priority = isPreferredCourse(code, preferences) ? "HIGH" : "MEDIUM";
                String reason = isPreferredCourse(code, preferences)
                        ? "Môn học thuộc đúng định hướng chuyên sâu (" + preferences.getCareerOrientation() + ") của bạn, đủ điều kiện tiên quyết và có lớp trống lịch."
                        : "Đã hoàn thành các môn tiên quyết, có lớp học phần đang mở còn chỗ và không bị trùng lịch học.";

                recommendedList.add(CourseRecommendationItemDto.builder()
                        .courseId(course.getId())
                        .courseCode(code)
                        .courseName(course.getCourseName())
                        .credits(course.getCredits())
                        .prerequisites(prereqs)
                        .prerequisitesSatisfied(true)
                        .missingPrerequisites(List.of())
                        .status("RECOMMENDED")
                        .priority(priority)
                        .reason(reason)
                        .availableSections(sectionOptions)
                        .build());
            }
        }

        // Sắp xếp ưu tiên đề xuất: HIGH trước, sau đó đến MEDIUM
        recommendedList.sort((a, b) -> {
            if ("HIGH".equals(a.getPriority()) && !"HIGH".equals(b.getPriority())) return -1;
            if (!"HIGH".equals(a.getPriority()) && "HIGH".equals(b.getPriority())) return 1;
            return a.getCourseCode().compareTo(b.getCourseCode());
        });

        // 3. Tạo lời khuyên tổng quan AI (Advisor Summary)
        String advisorSummary = buildAdvisorSummary(student, accumulatedCredits, activeCredits, recommendedList, preferences, startTime);

        String disclaimer = "Lưu ý: Các đề xuất môn học trên chỉ mang tính chất tham khảo học thuật từ dữ liệu thực tế hệ thống SIS. "
                + "Hệ thống AI không tự động đăng ký hoặc hủy học phần; sinh viên vui lòng chủ động sử dụng chức năng Đăng ký học phần để hoàn tất đăng ký.";

        CourseAdvisorResponseDto response = CourseAdvisorResponseDto.builder()
                .studentCode(student.getStudentCode())
                .fullName(student.getFullName())
                .department(student.getDepartment() != null ? student.getDepartment() : "CNTT")
                .major(student.getMajor() != null ? student.getMajor() : "Chưa cập nhật")
                .accumulatedCredits(accumulatedCredits)
                .activeCredits(activeCredits)
                .maxCreditsPerSemester(MAX_CREDITS_PER_SEMESTER)
                .recommendedCourses(recommendedList)
                .notRecommendedCourses(notRecommendedList)
                .advisorSummary(advisorSummary)
                .disclaimer(disclaimer)
                .generatedAt(LocalDateTime.now())
                .build();

        advisorCache.put(student.getId(), response);
        return response;
    }

    private List<CourseSectionOptionDto> buildSectionOptions(List<CourseSection> sections, List<CourseSection> activeSections) {
        List<CourseSectionOptionDto> list = new ArrayList<>();
        for (CourseSection sec : sections) {
            int capacity = sec.getCapacity() != null ? sec.getCapacity() : 40;
            long enrolledCount = enrollmentRepository.countByCourseSectionIdAndStatus(sec.getId(), EnrollmentStatus.ENROLLED);
            int remaining = Math.max(0, capacity - (int) enrolledCount);
            boolean isOpen = sec.getStatus() == CourseSectionStatus.OPEN;

            String conflictWith = findScheduleConflict(sec.getSchedule(), activeSections);
            boolean hasConflict = conflictWith != null;

            list.add(CourseSectionOptionDto.builder()
                    .sectionId(sec.getId())
                    .sectionCode(sec.getSectionCode())
                    .lecturerName((sec.getLecturer() != null && sec.getLecturer().getFullName() != null) ? sec.getLecturer().getFullName() : "Chưa phân công")
                    .schedule(sec.getSchedule() != null ? sec.getSchedule() : "Chưa xếp lịch")
                    .capacity(capacity)
                    .enrolledCount(enrolledCount)
                    .remainingSeats(remaining)
                    .status(sec.getStatus() != null ? sec.getStatus().name() : "UNKNOWN")
                    .isOpen(isOpen)
                    .hasScheduleConflict(hasConflict)
                    .conflictWith(conflictWith)
                    .build());
        }
        return list;
    }

    private boolean isPreferredCourse(String courseCode, CourseAdvisorRequestDto pref) {
        if (pref == null || pref.getCareerOrientation() == null || pref.getCareerOrientation().isBlank()) {
            return false;
        }
        String orient = pref.getCareerOrientation().toLowerCase();
        if (orient.contains("di động") || orient.contains("mobile") || orient.contains("android") || orient.contains("ios")) {
            return "INT3304".equals(courseCode);
        }
        if (orient.contains("ai") || orient.contains("trí tuệ nhân tạo")) {
            return "INT3405".equals(courseCode) || "INT3401".equals(courseCode) || "MAT1013".equals(courseCode);
        }
        if (orient.contains("web") || orient.contains("frontend") || orient.contains("backend")) {
            return "INT3303".equals(courseCode) || "INT3306".equals(courseCode);
        }
        if (orient.contains("an toàn") || orient.contains("security") || orient.contains("bảo mật")) {
            return "INT3305".equals(courseCode) || "INT3102".equals(courseCode);
        }
        if (orient.contains("phần mềm") || orient.contains("software")) {
            return "INT3507".equals(courseCode) || "INT3501".equals(courseCode);
        }
        return false;
    }

    private String buildAdvisorSummary(
            Student student,
            int accumulatedCredits,
            int activeCredits,
            List<CourseRecommendationItemDto> recommended,
            CourseAdvisorRequestDto pref,
            long startTime
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Sinh viên: ").append(student.getFullName()).append(" (").append(student.getStudentCode()).append(").\n");
        prompt.append("Tín chỉ tích lũy: ").append(accumulatedCredits).append(", Tín chỉ đang học: ").append(activeCredits).append(".\n");
        if (pref != null && pref.getCareerOrientation() != null && !pref.getCareerOrientation().isBlank()) {
            prompt.append("Định hướng chuyên sâu: ").append(pref.getCareerOrientation()).append(".\n");
        }
        prompt.append("Danh sách môn học đề xuất đăng ký: ");
        for (CourseRecommendationItemDto r : recommended) {
            prompt.append(r.getCourseCode()).append(" - ").append(r.getCourseName()).append(" (").append(r.getCredits()).append(" TC, Ưu tiên: ").append(r.getPriority()).append("); ");
        }

        String sysInstruction = "Bạn là AI Course Advisor của trường đại học SIS. Hãy viết một đoạn tư vấn học tập ngắn gọn (3-4 câu tiếng Việt, định dạng Markdown) "
                + "gợi ý sinh viên nên chọn đăng ký môn nào trong học kỳ tới dựa trên các môn đã thỏa mãn điều kiện tiên quyết và định hướng của họ. Không suy đoán số liệu.";

        AIProviderService.AIProviderResult external = providerService.generateExternalAnalysis(sysInstruction, prompt.toString());

        // Ghi log sử dụng AI
        try {
            usageLogRepository.save(AIUsageLog.builder()
                    .student(student)
                    .featureType("ADVISOR")
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

        // Fallback tư vấn nội bộ
        if (recommended.isEmpty()) {
            return "Hiện tại hệ thống chưa tìm thấy lớp học phần mở nào phù hợp với điều kiện tiên quyết của bạn. Bạn vui lòng kiểm tra lại khi đợt đăng ký mới mở ra.";
        }

        int totalRecCredits = recommended.stream().mapToInt(r -> r.getCredits() != null ? r.getCredits() : 0).sum();
        StringBuilder fallback = new StringBuilder();
        fallback.append("Chào **").append(student.getFullName()).append("**, hệ thống đã phân tích dữ liệu tích lũy hiện tại (**")
                .append(accumulatedCredits).append(" tín chỉ**) và đề xuất **").append(recommended.size())
                .append(" môn học phù hợp** (tổng cộng **").append(totalRecCredits).append(" tín chỉ**).\n\n");

        Optional<CourseRecommendationItemDto> retake = recommended.stream().filter(r -> "MUST_RETAKE".equals(r.getStatus())).findFirst();
        if (retake.isPresent()) {
            fallback.append("- ⚠️ **Ưu tiên hàng đầu:** Bạn nên đăng ký học lại môn **").append(retake.get().getCourseName())
                    .append(" (").append(retake.get().getCourseCode()).append(")** để hoàn thiện điều kiện tốt nghiệp.\n");
        }

        List<CourseRecommendationItemDto> high = recommended.stream().filter(r -> "HIGH".equals(r.getPriority())).collect(Collectors.toList());
        if (!high.isEmpty()) {
            fallback.append("- 🎯 **Môn học định hướng:** ");
            for (CourseRecommendationItemDto h : high) {
                fallback.append("**").append(h.getCourseName()).append("** (").append(h.getCredits()).append(" TC), ");
            }
            fallback.append("đây là các môn đã đủ điều kiện tiên quyết và có lớp mở thuận lợi.\n");
        }

        fallback.append("- 💡 **Lưu ý tín chỉ:** Bạn đang học ").append(activeCredits).append(" tín chỉ. Khối lượng đăng ký tối đa mỗi kỳ là ")
                .append(MAX_CREDITS_PER_SEMESTER).append(" tín chỉ để đảm bảo kết quả học tập tốt nhất.");

        return fallback.toString();
    }

    /**
     * Kiểm tra xem lịch học của lớp mới có bị trùng với bất kỳ lớp nào sinh viên đang học không.
     * Trả về tên lớp và lịch bị trùng nếu có xung đột, hoặc null nếu không trùng.
     */
    private String findScheduleConflict(String newSchedule, List<CourseSection> activeSections) {
        if (newSchedule == null || newSchedule.isBlank() || activeSections == null) {
            return null;
        }
        List<ScheduleSlot> newSlots = parseSchedule(newSchedule);
        if (newSlots.isEmpty()) return null;

        for (CourseSection activeSec : activeSections) {
            if (activeSec.getSchedule() == null || activeSec.getSchedule().isBlank()) continue;
            List<ScheduleSlot> activeSlots = parseSchedule(activeSec.getSchedule());
            for (ScheduleSlot n : newSlots) {
                for (ScheduleSlot a : activeSlots) {
                    if (n.day == a.day && Math.max(n.startMinutes, a.startMinutes) < Math.min(n.endMinutes, a.endMinutes)) {
                        return activeSec.getSectionCode() + " (" + activeSec.getSchedule() + ")";
                    }
                }
            }
        }
        return null;
    }

    private static class ScheduleSlot {
        int day; // 1: CN, 2: T2, 3: T3, ..., 7: T7
        int startMinutes;
        int endMinutes;

        ScheduleSlot(int day, int startMinutes, int endMinutes) {
            this.day = day;
            this.startMinutes = startMinutes;
            this.endMinutes = endMinutes;
        }
    }

    private List<ScheduleSlot> parseSchedule(String scheduleText) {
        List<ScheduleSlot> slots = new ArrayList<>();
        if (scheduleText == null) return slots;

        String[] parts = scheduleText.split(";");
        Pattern timePattern = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*[-–]\\s*(\\d{1,2}):(\\d{2})");

        for (String part : parts) {
            String lower = part.toLowerCase();
            int day = 0;
            if (lower.contains("thứ 2") || lower.contains("thứ hai") || lower.contains("thu 2")) day = 2;
            else if (lower.contains("thứ 3") || lower.contains("thứ ba") || lower.contains("thu 3")) day = 3;
            else if (lower.contains("thứ 4") || lower.contains("thứ tư") || lower.contains("thu 4")) day = 4;
            else if (lower.contains("thứ 5") || lower.contains("thứ năm") || lower.contains("thu 5")) day = 5;
            else if (lower.contains("thứ 6") || lower.contains("thứ sáu") || lower.contains("thu 6")) day = 6;
            else if (lower.contains("thứ 7") || lower.contains("thứ bảy") || lower.contains("thu 7")) day = 7;
            else if (lower.contains("chủ nhật") || lower.contains("cn")) day = 1;

            if (day == 0) continue;

            Matcher matcher = timePattern.matcher(part);
            if (matcher.find()) {
                int startHour = Integer.parseInt(matcher.group(1));
                int startMin = Integer.parseInt(matcher.group(2));
                int endHour = Integer.parseInt(matcher.group(3));
                int endMin = Integer.parseInt(matcher.group(4));

                slots.add(new ScheduleSlot(day, startHour * 60 + startMin, endHour * 60 + endMin));
            }
        }
        return slots;
    }

    private User getStudentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
        if (user.getRole() != Role.STUDENT || user.getStudent() == null) {
            throw new ForbiddenException("Chỉ tài khoản sinh viên mới có quyền sử dụng AI Course Advisor");
        }
        return user;
    }
}


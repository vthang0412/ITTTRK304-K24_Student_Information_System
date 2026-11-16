package com.example.student_management.service.academic.impl;

import com.example.student_management.entity.academic.*;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.CourseSectionScheduleRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.academic.RecurringCourseScheduleRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.academic.EnrollmentService;
import com.example.student_management.service.notification.NotificationService;
import com.example.student_management.service.timetable.ScheduleParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

@Service
public class EnrollmentServiceImpl implements EnrollmentService {
    private static final Logger log = LoggerFactory.getLogger(EnrollmentServiceImpl.class);
    private static final List<EnrollmentStatus> ACTIVE_STATUSES =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.ENROLLED);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private RecurringCourseScheduleRepository recurringCourseScheduleRepository;
    @Autowired
    private CourseSectionRepository courseSectionRepository;
    @Autowired
    private CourseSectionScheduleRepository courseSectionScheduleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationService notificationService;

    @Value("${app.enrollment.max-credits-per-semester:24}")
    private int maxCreditsPerSemester;

    @Override
    @Transactional(readOnly = true)
    public List<Enrollment> getMyEnrollments(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getStudent() == null) {
            throw new BadRequestException("Hồ sơ sinh viên chưa được liên kết với tài khoản");
        }
        return enrollmentRepository.findByStudentIdOrderByRegisteredAtDesc(user.getStudent().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Enrollment> getPendingEnrollments() {
        return enrollmentRepository.findByStatusOrderByRegisteredAtAsc(EnrollmentStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Enrollment> getEnrollmentsForAdmin(EnrollmentStatus status) {
        return status == null
                ? enrollmentRepository.findAllAdminDetails()
                : enrollmentRepository.findByStatusOrderByRegisteredAtAsc(status);
    }

    @Override
    @Transactional
    public Enrollment enroll(String username, Long sectionId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getStudent() == null) {
            throw new BadRequestException("Student profile not found");
        }
        Student student = user.getStudent();
        ensureStudentEligible(student);

        // Khóa ghi trên lớp học phần để kiểm tra sức chứa an toàn khi có nhiều request đồng thời.
        CourseSection section = courseSectionRepository.findByIdForUpdate(sectionId)
                .orElseThrow(() -> new NotFoundException("Course section not found"));
        if (section.getStatus() != CourseSectionStatus.OPEN) {
            throw new BadRequestException("Course section is not open for registration");
        }
        Semester semester = section.getSemester();
        ensureRegistrationOpen(semester);
        ensureNoScheduleConflict(student.getId(), section);
        ensureCreditLimit(student.getId(), section, null);

        int capacity = section.getCapacity() != null ? section.getCapacity() : 40;
        long activeCount = enrollmentRepository.countByCourseSectionIdAndStatusIn(sectionId, ACTIVE_STATUSES);
        if (activeCount >= capacity) {
            throw new ConflictException("Lớp học phần đã đủ số lượng đăng ký");
        }

        LocalDateTime now = LocalDateTime.now();
        Enrollment saved = enrollmentRepository.findByStudentIdAndCourseSectionId(student.getId(), sectionId)
                .map(existing -> {
                    if (existing.getStatus() == EnrollmentStatus.ENROLLED) {
                        throw new ConflictException("Bạn đã đăng ký lớp học phần này");
                    }
                    if (existing.getStatus() == EnrollmentStatus.PENDING) {
                        throw new ConflictException("Đơn đăng ký lớp học phần này đang chờ duyệt");
                    }
                    // REJECTED / WITHDRAWN: cho phép đăng ký lại, đưa về trạng thái chờ duyệt.
                    existing.setStatus(EnrollmentStatus.PENDING);
                    existing.setRegisteredAt(now);
                    existing.setWithdrawnAt(null);
                    existing.setApprovedAt(null);
                    existing.setApprovedBy(null);
                    existing.setRejectedAt(null);
                    existing.setRejectionReason(null);
                    existing.setRejectedBy(null);
                    return enrollmentRepository.save(existing);
                })
                .orElseGet(() -> enrollmentRepository.save(Enrollment.builder()
                        .student(student)
                        .courseSection(section)
                        .status(EnrollmentStatus.PENDING)
                        .registeredAt(now)
                        .build()));

        sendEnrollmentNotification(user,
                "Đã gửi đơn đăng ký học phần: " + section.getSectionCode(),
                buildRequestContent(section, now));

        return saved;
    }

    @Override
    @Transactional
    public Enrollment approve(String adminUsername, Long enrollmentId) {
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException("Enrollment not found"));
        CourseSection section = validateEnrollmentForApproval(enrollment, enrollmentId);

        LocalDateTime now = LocalDateTime.now();
        if (enrollmentRepository.approveIfPending(enrollmentId, EnrollmentStatus.PENDING,
                EnrollmentStatus.ENROLLED, now, admin) != 1) {
            throw new BadRequestException("Yêu cầu đăng ký đã được xử lý trước đó");
        }
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setApprovedAt(now);
        enrollment.setApprovedBy(admin);
        enrollment.setRejectedAt(null);
        enrollment.setRejectionReason(null);
        enrollment.setRejectedBy(null);

        User recipient = enrollment.getStudent() != null ? enrollment.getStudent().getUser() : null;
        if (recipient != null) {
            String content = "Đơn đăng ký lớp " + section.getSectionCode() + " - "
                    + courseName(section) + " đã được duyệt.\n"
                    + "• Giảng viên: " + lecturerName(section) + "\n"
                    + "• Thời khóa biểu: " + schedule(section) + "\n"
                    + "• Ngày duyệt: " + now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            sendEnrollmentNotification(recipient,
                    "Đăng ký học phần được duyệt: " + section.getSectionCode(), content);
        }

        return enrollment;
    }

    @Override
    @Transactional
    public void validateApproval(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findAdminDetailsById(enrollmentId)
                .orElseThrow(() -> new NotFoundException("Enrollment not found"));
        validateEnrollmentForApproval(enrollment, enrollmentId);
    }

    private CourseSection validateEnrollmentForApproval(Enrollment enrollment, Long enrollmentId) {
        if (enrollment.getStatus() != EnrollmentStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể duyệt đơn đang ở trạng thái chờ duyệt");
        }
        ensureStudentEligible(enrollment.getStudent());

        if (enrollment.getCourseSection() == null) {
            throw new BadRequestException("Yêu cầu đăng ký không còn lớp học phần hợp lệ");
        }
        CourseSection section = courseSectionRepository
                .findByIdForUpdate(enrollment.getCourseSection().getId())
                .orElseThrow(() -> new NotFoundException("Course section not found"));

        if (section.getStatus() != CourseSectionStatus.OPEN) {
            throw new BadRequestException("Lớp học phần không còn mở để duyệt đăng ký");
        }
        Semester semester = section.getSemester();
        if (semester != null && semester.getStatus() == SemesterStatus.COMPLETED) {
            throw new BadRequestException("Học kỳ này đã kết thúc, không thể duyệt đăng ký");
        }
        ensureNoScheduleConflict(enrollment.getStudent().getId(), section);

        int capacity = section.getCapacity() != null ? section.getCapacity() : 40;
        long activeOtherCount = enrollmentRepository.countByCourseSectionIdAndStatusInAndIdNot(
                section.getId(), ACTIVE_STATUSES, enrollmentId);
        if (activeOtherCount >= capacity) {
            throw new ConflictException("Lớp học phần đã đủ số lượng, không thể duyệt thêm");
        }
        ensureCreditLimit(enrollment.getStudent().getId(), section, enrollmentId);
        return section;
    }

    private void ensureStudentEligible(Student student) {
        if (student == null) {
            throw new BadRequestException("Yêu cầu đăng ký không có hồ sơ sinh viên hợp lệ");
        }
        if (student.getStatus() == AccountStatus.INACTIVE
                || (student.getUser() != null && student.getUser().getStatus() == AccountStatus.INACTIVE)) {
            throw new ForbiddenException("Tài khoản sinh viên đã bị vô hiệu hóa");
        }

        String academicStatus = student.getAcademicStatus();
        if (academicStatus == null) {
            return;
        }
        String normalizedStatus = academicStatus.trim().toLowerCase(Locale.ROOT);
        if (List.of("suspended", "on leave", "leave of absence", "withdrawn", "inactive", "reserved",
                "bao luu", "dang bao luu", "tam nghi", "tam nghi hoc", "nghi hoc", "dang nghi hoc",
                "da nghi hoc", "thoi hoc", "da thoi hoc")
                .contains(normalizeAcademicStatus(normalizedStatus))) {
            throw new ForbiddenException("Sinh viên đang bảo lưu, tạm nghỉ hoặc đã nghỉ học");
        }
    }

    private String normalizeAcademicStatus(String status) {
        return java.text.Normalizer.normalize(status, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");
    }

    private void ensureRegistrationOpen(Semester semester) {
        if (semester == null || semester.getStatus() == SemesterStatus.COMPLETED) {
            throw new BadRequestException("Học kỳ này không mở đăng ký học phần");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        boolean hasRegistrationWindow = semester.getRegistrationStart() != null
                || semester.getRegistrationEnd() != null;
        boolean statusAllowsRegistration = semester.getStatus() == SemesterStatus.REGISTRATION_OPEN
                || (semester.getStatus() == SemesterStatus.IN_PROGRESS && hasRegistrationWindow);
        if (!statusAllowsRegistration
                || (semester.getRegistrationStart() != null && now.isBefore(semester.getRegistrationStart()))
                || (semester.getRegistrationEnd() != null && now.isAfter(semester.getRegistrationEnd()))) {
            throw new BadRequestException("Học kỳ này hiện không trong thời gian đăng ký học phần");
        }
    }

    private void ensureCreditLimit(Long studentId, CourseSection candidate, Long excludedEnrollmentId) {
        if (maxCreditsPerSemester <= 0) {
            return;
        }

        Integer candidateCredits = candidate.getCourse() != null ? candidate.getCourse().getCredits() : null;
        int requestedCredits = candidateCredits != null ? candidateCredits : 0;
        int currentCredits = enrollmentRepository.findByStudentId(studentId).stream()
                .filter(current -> excludedEnrollmentId == null || !excludedEnrollmentId.equals(current.getId()))
                .filter(current -> ACTIVE_STATUSES.contains(current.getStatus()))
                .filter(current -> current.getCourseSection() != null
                        && current.getCourseSection().getSemester() != null
                        && candidate.getSemester() != null
                        && current.getCourseSection().getSemester().getId()
                        .equals(candidate.getSemester().getId()))
                .map(Enrollment::getCourseSection)
                .map(CourseSection::getCourse)
                .filter(course -> course != null && course.getCredits() != null)
                .mapToInt(course -> course.getCredits())
                .sum();

        if (currentCredits + requestedCredits > maxCreditsPerSemester) {
            throw new ConflictException("Vượt quá giới hạn " + maxCreditsPerSemester
                    + " tín chỉ trong học kỳ");
        }
    }

    private void ensureNoScheduleConflict(Long studentId, CourseSection candidate) {
        List<SchedulePeriod> candidatePeriods = schedulePeriods(candidate);
        if (candidatePeriods.isEmpty()) {
            return;
        }

        for (Enrollment current : enrollmentRepository.findByStudentId(studentId)) {
            if (current.getCourseSection() == null
                    || current.getCourseSection().getId().equals(candidate.getId())
                    || !ACTIVE_STATUSES.contains(current.getStatus())) {
                continue;
            }

            CourseSection other = current.getCourseSection();
            if (!semestersOverlap(candidate.getSemester(), other.getSemester())) {
                continue;
            }

            for (SchedulePeriod candidatePeriod : candidatePeriods) {
                for (SchedulePeriod otherPeriod : schedulePeriods(other)) {
                    LocalDate overlapStart = laterOf(candidatePeriod.start(), otherPeriod.start());
                    LocalDate overlapEnd = earlierOf(candidatePeriod.end(), otherPeriod.end());
                    if (overlapStart.isAfter(overlapEnd)) {
                        continue;
                    }

                    for (ScheduleParser.Slot candidateSlot : ScheduleParser.parse(candidatePeriod.schedule())) {
                        for (ScheduleParser.Slot otherSlot : ScheduleParser.parse(otherPeriod.schedule())) {
                            if (candidateSlot.dayOfWeek() == otherSlot.dayOfWeek()
                                    && timeRangesOverlap(candidateSlot.startTime(), candidateSlot.endTime(),
                                    otherSlot.startTime(), otherSlot.endTime())
                                    && weekdayOccursDuringOverlap(overlapStart, overlapEnd,
                                    candidateSlot.dayOfWeek())) {
                                throw new ConflictException(
                                        "Lịch học bị trùng với lớp " + other.getSectionCode());
                            }
                        }
                    }
                }
            }
        }
    }

    private List<SchedulePeriod> schedulePeriods(CourseSection section) {
        Semester semester = section.getSemester();
        if (semester == null || semester.getStartDate() == null || semester.getEndDate() == null) {
            return List.of();
        }

        List<CourseSectionSchedule> history =
                courseSectionScheduleRepository.findByCourseSectionIdOrderByEffectiveFromAsc(section.getId());
        List<SchedulePeriod> periods = new java.util.ArrayList<>();
        if (history.isEmpty()) {
            if (section.getSchedule() != null && !section.getSchedule().isBlank()) {
                periods.add(new SchedulePeriod(semester.getStartDate(), semester.getEndDate(), section.getSchedule()));
            }
        } else {
            history.stream()
                    .filter(entry -> entry.getCancelledFrom() == null
                            || entry.getEffectiveFrom().isBefore(entry.getCancelledFrom()))
                    .map(entry -> {
                        LocalDate effectiveEnd = entry.getEffectiveTo() == null
                                ? semester.getEndDate() : entry.getEffectiveTo();
                        if (entry.getCancelledFrom() != null
                                && effectiveEnd.isAfter(entry.getCancelledFrom().minusDays(1))) {
                            effectiveEnd = entry.getCancelledFrom().minusDays(1);
                        }
                        return new SchedulePeriod(laterOf(semester.getStartDate(), entry.getEffectiveFrom()),
                                earlierOf(semester.getEndDate(), effectiveEnd), entry.getSchedule());
                    })
                    .filter(period -> !period.start().isAfter(period.end()))
                    .forEach(periods::add);
        }

        recurringCourseScheduleRepository.findByCourseSectionIdOrderByDayOfWeekAscStartTimeAsc(section.getId())
                .stream()
                .map(rule -> {
                    LocalDate end = rule.getEndDate();
                    if (rule.getCancelledFrom() != null
                            && end.isAfter(rule.getCancelledFrom().minusDays(1))) {
                        end = rule.getCancelledFrom().minusDays(1);
                    }
                    String weekday = rule.getDayOfWeek() == 7
                            ? "Chủ Nhật" : "Thứ " + (rule.getDayOfWeek() + 1);
                    String schedule = weekday + ", "
                            + ScheduleParser.format(rule.getStartTime()) + " - "
                            + ScheduleParser.format(rule.getEndTime()) + ", " + rule.getClassroom();
                    return new SchedulePeriod(laterOf(semester.getStartDate(), rule.getStartDate()),
                            earlierOf(semester.getEndDate(), end), schedule);
                })
                .filter(period -> !period.start().isAfter(period.end()))
                .forEach(periods::add);
        return periods;
    }

    private boolean semestersOverlap(Semester left, Semester right) {
        if (left == null || right == null || left.getStartDate() == null || left.getEndDate() == null
                || right.getStartDate() == null || right.getEndDate() == null) {
            return false;
        }
        return !left.getEndDate().isBefore(right.getStartDate())
                && !right.getEndDate().isBefore(left.getStartDate());
    }

    private boolean weekdayOccursDuringOverlap(LocalDate start, LocalDate end, int dayOfWeek) {
        LocalDate firstOccurrence = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dayOfWeek)));
        return !firstOccurrence.isAfter(end);
    }

    private LocalDate laterOf(LocalDate left, LocalDate right) {
        return left.isAfter(right) ? left : right;
    }

    private LocalDate earlierOf(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    private boolean timeRangesOverlap(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    private record SchedulePeriod(LocalDate start, LocalDate end, String schedule) {
    }

    @Override
    @Transactional
    public Enrollment reject(String adminUsername, Long enrollmentId, String reason) {
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException("Enrollment not found"));
        if (enrollment.getStatus() != EnrollmentStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể từ chối đơn đang ở trạng thái chờ duyệt");
        }

        String trimmedReason = reason != null ? reason.trim() : "";
        if (trimmedReason.isEmpty()) {
            throw new BadRequestException("Vui lòng nhập lý do từ chối");
        }
        if (trimmedReason.length() > 500) {
            throw new BadRequestException("Lý do từ chối không được vượt quá 500 ký tự");
        }

        LocalDateTime now = LocalDateTime.now();
        if (enrollmentRepository.rejectIfPending(enrollmentId, EnrollmentStatus.PENDING,
                EnrollmentStatus.REJECTED, now, trimmedReason, admin) != 1) {
            throw new BadRequestException("Yêu cầu đăng ký đã được xử lý trước đó");
        }
        enrollment.setStatus(EnrollmentStatus.REJECTED);
        enrollment.setRejectedAt(now);
        enrollment.setRejectionReason(trimmedReason);
        enrollment.setRejectedBy(admin);
        enrollment.setApprovedAt(null);
        enrollment.setApprovedBy(null);

        CourseSection section = enrollment.getCourseSection();
        User recipient = enrollment.getStudent() != null ? enrollment.getStudent().getUser() : null;
        if (recipient != null && section != null) {
            String content = "Đơn đăng ký lớp " + section.getSectionCode() + " - "
                    + courseName(section) + " đã bị từ chối.\n"
                    + "• Lý do: " + trimmedReason + "\n"
                    + "• Ngày xử lý: " + now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            sendEnrollmentNotification(recipient,
                    "Đăng ký học phần bị từ chối: " + section.getSectionCode(), content);
        }

        return enrollment;
    }

    @Override
    @Transactional
    public Enrollment withdraw(String username, Long enrollmentId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getStudent() == null) {
            throw new BadRequestException("Student profile not found");
        }
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException("Enrollment not found"));
        if (!enrollment.getStudent().getId().equals(user.getStudent().getId())) {
            throw new ForbiddenException("Not allowed");
        }
        if (enrollment.getStatus() == EnrollmentStatus.WITHDRAWN) {
            throw new BadRequestException("Đăng ký này đã được hủy trước đó");
        }
        if (enrollment.getStatus() == EnrollmentStatus.REJECTED) {
            throw new BadRequestException("Đơn đăng ký đã bị từ chối, không thể hủy");
        }
        LocalDateTime now = LocalDateTime.now();
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setWithdrawnAt(now);
        Enrollment saved = enrollmentRepository.save(enrollment);

        CourseSection section = enrollment.getCourseSection();
        String sectionCode = section != null ? section.getSectionCode() : "";
        String content = "Bạn đã huỷ đăng ký lớp học phần " + sectionCode + " - "
                + (section != null ? courseName(section) : "Học phần") + " vào ngày "
                + now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ".";
        sendEnrollmentNotification(user, "Huỷ đăng ký học phần: " + sectionCode, content);

        return saved;
    }

    private void sendEnrollmentNotification(User recipient, String title, String content) {
        try {
            notificationService.sendSystemNotification(recipient, title, content, "ENROLLMENT");
        } catch (RuntimeException exception) {
            log.warn("Failed to send enrollment notification to user {}", recipient.getUsername(), exception);
        }
    }

    private String buildRequestContent(CourseSection section, LocalDateTime now) {
        return "Bạn đã gửi đơn đăng ký lớp " + section.getSectionCode() + " - " + courseName(section)
                + " (" + credits(section) + " tín chỉ).\n"
                + "• Giảng viên: " + lecturerName(section) + "\n"
                + "• Thời khóa biểu: " + schedule(section) + "\n"
                + "• Ngày gửi: " + now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "\n"
                + "Đơn đang chờ quản trị viên duyệt. Bạn sẽ nhận thông báo khi có kết quả.";
    }

    private String courseName(CourseSection section) {
        Course course = section.getCourse();
        return course != null ? course.getCourseName() : "Học phần";
    }

    private int credits(CourseSection section) {
        Course course = section.getCourse();
        return course != null && course.getCredits() != null ? course.getCredits() : 0;
    }

    private String schedule(CourseSection section) {
        return (section.getSchedule() != null && !section.getSchedule().isBlank())
                ? section.getSchedule()
                : "Chưa cập nhật";
    }

    private String lecturerName(CourseSection section) {
        return (section.getLecturer() != null && section.getLecturer().getFullName() != null)
                ? section.getLecturer().getFullName()
                : "Chưa phân công";
    }
}

package com.example.student_management.service.academic.impl;

import com.example.student_management.dto.academic.RecurringScheduleRequest;
import com.example.student_management.dto.academic.RecurringScheduleSessionRequest;
import com.example.student_management.dto.academic.RecurringScheduleSessionResponse;
import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.academic.CourseSectionSchedule;
import com.example.student_management.entity.academic.CourseSectionStatus;
import com.example.student_management.entity.academic.Enrollment;
import com.example.student_management.entity.academic.EnrollmentStatus;
import com.example.student_management.entity.academic.RecurringCourseSchedule;
import com.example.student_management.entity.academic.Semester;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.CourseSectionScheduleRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.academic.RecurringCourseScheduleRepository;
import com.example.student_management.service.academic.RecurringCourseScheduleService;
import com.example.student_management.service.timetable.ScheduleParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecurringCourseScheduleServiceImpl implements RecurringCourseScheduleService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CourseSectionRepository sectionRepository;
    private final RecurringCourseScheduleRepository recurringScheduleRepository;
    private final CourseSectionScheduleRepository scheduleHistoryRepository;
    private final EnrollmentRepository enrollmentRepository;

    public RecurringCourseScheduleServiceImpl(
            CourseSectionRepository sectionRepository,
            RecurringCourseScheduleRepository recurringScheduleRepository,
            CourseSectionScheduleRepository scheduleHistoryRepository,
            EnrollmentRepository enrollmentRepository) {
        this.sectionRepository = sectionRepository;
        this.recurringScheduleRepository = recurringScheduleRepository;
        this.scheduleHistoryRepository = scheduleHistoryRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecurringScheduleSessionResponse> getActiveSessions(Long sectionId) {
        requireSection(sectionId);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return recurringScheduleRepository
                .findByCourseSectionIdOrderByDayOfWeekAscStartTimeAsc(sectionId)
                .stream()
                .filter(schedule -> !schedule.getEndDate().isBefore(today))
                .filter(schedule -> schedule.getCancelledFrom() == null
                        || schedule.getCancelledFrom().isAfter(today))
                .map(RecurringScheduleSessionResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void check(Long sectionId, RecurringScheduleRequest request) {
        CourseSection section = requireSection(sectionId);
        List<RuleEntry> candidates = validateRequest(section, request);
        candidates = attachEnrolledStudents(sectionId, candidates);
        validateNoConflicts(section, candidates, sectionRepository.findAll());
    }

    @Override
    @Transactional
    public List<RecurringScheduleSessionResponse> replace(Long sectionId, RecurringScheduleRequest request) {
        List<CourseSection> lockedSections = sectionRepository.findAllForUpdate();
        CourseSection section = lockedSections.stream()
                .filter(item -> item.getId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lớp học phần"));

        List<RuleEntry> candidates = validateRequest(section, request);
        candidates = attachEnrolledStudents(sectionId, candidates);
        validateNoConflicts(section, candidates, lockedSections);

        LocalDate effectiveFrom = request.getStartDate();
        recurringScheduleRepository.findByCourseSectionIdOrderByDayOfWeekAscStartTimeAsc(sectionId)
                .stream()
                .filter(existing -> existing.getCancelledFrom() == null
                        || existing.getCancelledFrom().isAfter(effectiveFrom))
                .forEach(existing -> existing.setCancelledFrom(effectiveFrom));

        List<CourseSectionSchedule> legacyHistory =
                scheduleHistoryRepository.findByCourseSectionIdOrderByEffectiveFromAsc(sectionId);
        if (legacyHistory.isEmpty() && section.getSchedule() != null && !section.getSchedule().isBlank()) {
            LocalDate semesterEnd = section.getSemester().getEndDate();
            scheduleHistoryRepository.save(CourseSectionSchedule.builder()
                    .courseSection(section)
                    .schedule(section.getSchedule())
                    .effectiveFrom(section.getSemester().getStartDate())
                    .effectiveTo(semesterEnd)
                    .cancelledFrom(effectiveFrom)
                    .build());
        }
        legacyHistory.stream()
                .filter(existing -> existing.getCancelledFrom() == null
                        || existing.getCancelledFrom().isAfter(effectiveFrom))
                .forEach(existing -> existing.setCancelledFrom(effectiveFrom));
        if (section.getSchedule() != null && !section.getSchedule().isBlank()) {
            section.setSchedule("");
            sectionRepository.save(section);
        }

        List<RecurringCourseSchedule> saved = request.getSessions().stream()
                .map(session -> RecurringCourseSchedule.builder()
                        .courseSection(section)
                        .dayOfWeek(session.getDayOfWeek())
                        .startPeriod(session.getStartPeriod())
                        .endPeriod(session.getEndPeriod())
                        .startTime(session.getStartTime())
                        .endTime(session.getEndTime())
                        .classroom(session.getClassroom().trim())
                        .startDate(request.getStartDate())
                        .endDate(request.getEndDate())
                        .build())
                .map(recurringScheduleRepository::save)
                .toList();
        return saved.stream().map(RecurringScheduleSessionResponse::from).toList();
    }

    @Override
    @Transactional
    public void cancelActiveSessions(Long sectionId) {
        CourseSection section = sectionRepository.findByIdForUpdate(sectionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lớp học phần"));
        LocalDate cancellationDate = LocalDate.now(BUSINESS_ZONE).plusDays(1);
        recurringScheduleRepository.findByCourseSectionIdOrderByDayOfWeekAscStartTimeAsc(sectionId)
                .stream()
                .filter(schedule -> schedule.getEndDate().isAfter(LocalDate.now(BUSINESS_ZONE))
                        && (schedule.getCancelledFrom() == null
                        || schedule.getCancelledFrom().isAfter(cancellationDate)))
                .forEach(schedule -> schedule.setCancelledFrom(cancellationDate));

        if (section.getSchedule() != null && !section.getSchedule().isBlank()) {
            Semester semester = section.getSemester();
            if (section.getStatus() == CourseSectionStatus.CANCELLED) {
                throw new BadRequestException("Không thể thiết lập lịch cho lớp học phần đã bị hủy");
            }
            if (semester == null || semester.getStartDate() == null || semester.getEndDate() == null) {
                throw new BadRequestException("Không thể lưu lịch sử vì học kỳ chưa có ngày bắt đầu/kết thúc");
            }
            List<CourseSectionSchedule> history =
                    scheduleHistoryRepository.findByCourseSectionIdOrderByEffectiveFromAsc(sectionId);
            if (history.isEmpty()) {
                scheduleHistoryRepository.save(CourseSectionSchedule.builder()
                        .courseSection(section)
                        .schedule(section.getSchedule())
                        .effectiveFrom(semester.getStartDate())
                        .effectiveTo(semester.getEndDate())
                        .cancelledFrom(cancellationDate)
                        .build());
            } else {
                history.stream()
                        .filter(entry -> entry.getCancelledFrom() == null
                                || entry.getCancelledFrom().isAfter(cancellationDate))
                        .forEach(entry -> entry.setCancelledFrom(cancellationDate));
            }
            section.setSchedule("");
            sectionRepository.save(section);
        }
    }

    private CourseSection requireSection(Long sectionId) {
        return sectionRepository.findById(sectionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lớp học phần"));
    }

    private List<RuleEntry> validateRequest(CourseSection section, RecurringScheduleRequest request) {
        if (request == null || request.getStartDate() == null || request.getEndDate() == null
                || request.getSessions() == null || request.getSessions().isEmpty()) {
            throw new BadRequestException("Vui lòng nhập khoảng ngày và ít nhất một buổi học");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BadRequestException("Ngày kết thúc phải bằng hoặc sau ngày bắt đầu");
        }
        if (request.getStartDate().isBefore(LocalDate.now(BUSINESS_ZONE))) {
            throw new BadRequestException("Ngày bắt đầu lịch mới không được ở trong quá khứ");
        }

        Semester semester = section.getSemester();
        if (semester == null || semester.getStartDate() == null || semester.getEndDate() == null) {
            throw new BadRequestException("Học kỳ cần có ngày bắt đầu và kết thúc trước khi thiết lập lịch");
        }
        if (request.getStartDate().isBefore(semester.getStartDate())
                || request.getEndDate().isAfter(semester.getEndDate())) {
            throw new BadRequestException("Khoảng ngày lịch học phải nằm trong thời gian của học kỳ");
        }

        List<RuleEntry> candidates = new ArrayList<>();
        for (RecurringScheduleSessionRequest session : request.getSessions()) {
            if (session.getDayOfWeek() == null || session.getDayOfWeek() < 1 || session.getDayOfWeek() > 7) {
                throw new BadRequestException("Thứ trong tuần không hợp lệ");
            }
            if (session.getStartPeriod() == null || session.getStartPeriod() < 1
                    || session.getEndPeriod() == null || session.getEndPeriod() <= session.getStartPeriod()) {
                throw new BadRequestException("Tiết kết thúc phải sau tiết bắt đầu");
            }
            if (session.getStartTime() == null || session.getEndTime() == null
                    || !session.getEndTime().isAfter(session.getStartTime())) {
                throw new BadRequestException("Giờ kết thúc phải sau giờ bắt đầu");
            }
            if (session.getClassroom() == null || session.getClassroom().isBlank()
                    || session.getClassroom().length() > 100) {
                throw new BadRequestException("Vui lòng nhập phòng học (tối đa 100 ký tự)");
            }
            candidates.add(new RuleEntry(section, session.getDayOfWeek(), session.getStartPeriod(),
                    session.getEndPeriod(), session.getStartTime(), session.getEndTime(),
                    session.getClassroom().trim(), request.getStartDate(), request.getEndDate(), Set.of()));
        }
        return candidates;
    }

    private void validateNoConflicts(CourseSection target, List<RuleEntry> candidates,
                                     List<CourseSection> sections) {
        for (int left = 0; left < candidates.size(); left++) {
            for (int right = left + 1; right < candidates.size(); right++) {
                if (conflicts(candidates.get(left), candidates.get(right))) {
                    throw conflictException(target, candidates.get(left), candidates.get(right));
                }
            }
        }

        List<Long> sectionIds = sections.stream().map(CourseSection::getId).toList();
        if (sectionIds.isEmpty()) {
            return;
        }
        Map<Long, CourseSection> sectionsById = sections.stream()
                .collect(Collectors.toMap(CourseSection::getId, item -> item));
        Map<Long, Set<Long>> enrolledStudents = enrolledStudentsBySection(sectionIds);
        List<RuleEntry> existingEntries = existingRecurringEntries(sectionIds, sectionsById, enrolledStudents);
        existingEntries.addAll(existingLegacyEntries(sectionIds, sectionsById, enrolledStudents));

        for (RuleEntry candidate : candidates) {
            for (RuleEntry existing : existingEntries) {
                if (existing.section().getId().equals(target.getId())) {
                    continue;
                }
                if (conflicts(candidate, existing) && sharesResource(target, candidate, existing)) {
                    throw conflictException(target, candidate, existing);
                }
            }
        }
    }

    private Map<Long, Set<Long>> enrolledStudentsBySection(Collection<Long> sectionIds) {
        Map<Long, Set<Long>> result = new HashMap<>();
        enrollmentRepository.findByCourseSectionIdInAndStatus(sectionIds, EnrollmentStatus.ENROLLED)
                .forEach(enrollment -> result.computeIfAbsent(enrollment.getCourseSection().getId(),
                        ignored -> new HashSet<>()).add(enrollment.getStudent().getId()));
        return result;
    }

    private List<RuleEntry> existingRecurringEntries(Collection<Long> sectionIds,
                                                      Map<Long, CourseSection> sectionsById,
                                                      Map<Long, Set<Long>> enrolledStudents) {
        List<RuleEntry> result = new ArrayList<>();
        for (RecurringCourseSchedule schedule :
                recurringScheduleRepository.findByCourseSectionIdInOrderByStartDateAsc(sectionIds)) {
            LocalDate effectiveEnd = effectiveEnd(schedule.getEndDate(), schedule.getCancelledFrom());
            CourseSection section = schedule.getCourseSection();
            if (effectiveEnd.isBefore(schedule.getStartDate()) || isCancelled(section)) {
                continue;
            }
            result.add(new RuleEntry(sectionsById.get(section.getId()), schedule.getDayOfWeek(),
                    schedule.getStartPeriod(), schedule.getEndPeriod(), schedule.getStartTime(),
                    schedule.getEndTime(), schedule.getClassroom(), schedule.getStartDate(), effectiveEnd,
                    enrolledStudents.getOrDefault(section.getId(), Set.of())));
        }
        return result;
    }

    private List<RuleEntry> existingLegacyEntries(Collection<Long> sectionIds,
                                                   Map<Long, CourseSection> sectionsById,
                                                   Map<Long, Set<Long>> enrolledStudents) {
        List<RuleEntry> result = new ArrayList<>();
        Map<Long, List<CourseSectionSchedule>> historyBySection =
                scheduleHistoryRepository.findByCourseSectionIdInOrderByEffectiveFromAsc(sectionIds).stream()
                        .collect(Collectors.groupingBy(entry -> entry.getCourseSection().getId()));

        for (CourseSection section : sectionsById.values()) {
            if (isCancelled(section) || section.getSemester() == null) {
                continue;
            }
            List<CourseSectionSchedule> history = historyBySection.getOrDefault(section.getId(), List.of());
            if (history.isEmpty()) {
                addLegacyTextEntries(result, section, section.getSchedule(),
                        section.getSemester().getStartDate(), section.getSemester().getEndDate(),
                        enrolledStudents.getOrDefault(section.getId(), Set.of()));
                continue;
            }
            for (CourseSectionSchedule revision : history) {
                LocalDate revisionEnd = revision.getEffectiveTo() != null
                        ? revision.getEffectiveTo() : section.getSemester().getEndDate();
                LocalDate effectiveEnd = effectiveEnd(revisionEnd, revision.getCancelledFrom());
                if (effectiveEnd.isBefore(revision.getEffectiveFrom())) {
                    continue;
                }
                addLegacyTextEntries(result, section, revision.getSchedule(), revision.getEffectiveFrom(),
                        effectiveEnd, enrolledStudents.getOrDefault(section.getId(), Set.of()));
            }
        }
        return result;
    }

    private void addLegacyTextEntries(List<RuleEntry> target, CourseSection section, String rawSchedule,
                                      LocalDate startDate, LocalDate endDate, Set<Long> studentIds) {
        if (startDate == null || endDate == null || rawSchedule == null || rawSchedule.isBlank()) {
            return;
        }
        for (ScheduleParser.Slot slot : ScheduleParser.parse(rawSchedule)) {
            target.add(new RuleEntry(section, slot.dayOfWeek(), null, null, slot.startTime(), slot.endTime(),
                    slot.room(), startDate, endDate, studentIds));
        }
    }

    private boolean sharesResource(CourseSection target, RuleEntry candidate, RuleEntry existing) {
        if (sameRoom(candidate.room(), existing.room())) {
            return true;
        }
        if (target.getLecturer() != null && existing.section().getLecturer() != null
                && target.getLecturer().getId().equals(existing.section().getLecturer().getId())) {
            return true;
        }
        return candidate.studentIds().stream().anyMatch(existing.studentIds()::contains);
    }

    private List<RuleEntry> attachEnrolledStudents(Long sectionId, List<RuleEntry> candidates) {
        Set<Long> studentIds = enrollmentRepository.findByCourseSectionId(sectionId).stream()
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ENROLLED)
                .map(Enrollment::getStudent)
                .filter(student -> student != null)
                .map(student -> student.getId())
                .collect(Collectors.toSet());
        return candidates.stream()
                .map(candidate -> new RuleEntry(candidate.section(), candidate.dayOfWeek(),
                        candidate.startPeriod(), candidate.endPeriod(), candidate.startTime(),
                        candidate.endTime(), candidate.room(), candidate.startDate(), candidate.endDate(),
                        studentIds))
                .toList();
    }

    private boolean conflicts(RuleEntry left, RuleEntry right) {
        if (left.dayOfWeek() != right.dayOfWeek()
                || !left.startTime().isBefore(right.endTime())
                || !right.startTime().isBefore(left.endTime())) {
            return false;
        }
        LocalDate overlapStart = left.startDate().isAfter(right.startDate()) ? left.startDate() : right.startDate();
        LocalDate overlapEnd = left.endDate().isBefore(right.endDate()) ? left.endDate() : right.endDate();
        return !overlapStart.isAfter(overlapEnd)
                && !overlapStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(left.dayOfWeek())))
                .isAfter(overlapEnd);
    }

    private ConflictException conflictException(CourseSection target, RuleEntry candidate, RuleEntry existing) {
        int firstPeriod = candidate.startPeriod() == null || existing.startPeriod() == null
                ? 0 : Math.max(candidate.startPeriod(), existing.startPeriod());
        int lastPeriod = candidate.endPeriod() == null || existing.endPeriod() == null
                ? 0 : Math.min(candidate.endPeriod(), existing.endPeriod());
        String overlap = firstPeriod > 0 && lastPeriod >= firstPeriod
                ? "tiết " + firstPeriod + "–" + lastPeriod
                : "giờ " + candidate.startTime() + "–" + candidate.endTime();
        String courseName = existing.section().getCourse() != null
                ? existing.section().getCourse().getCourseName() : existing.section().getSectionCode();
        return new ConflictException("Lịch học bị trùng với môn " + courseName + " ("
                + existing.section().getSectionCode() + ") vào " + weekdayName(candidate.dayOfWeek())
                + ", " + overlap);
    }

    private boolean sameRoom(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean isCancelled(CourseSection section) {
        return section.getStatus() == CourseSectionStatus.CANCELLED;
    }

    private LocalDate effectiveEnd(LocalDate endDate, LocalDate cancelledFrom) {
        if (cancelledFrom == null) {
            return endDate;
        }
        LocalDate cancellationEnd = cancelledFrom.minusDays(1);
        return endDate.isBefore(cancellationEnd) ? endDate : cancellationEnd;
    }

    private String weekdayName(int dayOfWeek) {
        return dayOfWeek == 7 ? "Chủ Nhật" : "Thứ " + (dayOfWeek + 1);
    }

    private record RuleEntry(CourseSection section, int dayOfWeek, Integer startPeriod, Integer endPeriod,
                             LocalTime startTime, LocalTime endTime, String room,
                             LocalDate startDate, LocalDate endDate, Set<Long> studentIds) {
    }
}

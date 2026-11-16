package com.example.student_management.service.timetable.impl;

import com.example.student_management.dto.timetable.TimetableOccurrenceDto;
import com.example.student_management.dto.timetable.TimetableWeekResponse;
import com.example.student_management.dto.timetable.UnscheduledSectionDto;
import com.example.student_management.entity.academic.Course;
import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.academic.CourseSectionStatus;
import com.example.student_management.entity.academic.CourseSectionSchedule;
import com.example.student_management.entity.academic.Enrollment;
import com.example.student_management.entity.academic.EnrollmentStatus;
import com.example.student_management.entity.academic.Semester;
import com.example.student_management.entity.academic.RecurringCourseSchedule;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.academic.CourseSectionScheduleRepository;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.SemesterRepository;
import com.example.student_management.repository.academic.RecurringCourseScheduleRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.timetable.ScheduleParser;
import com.example.student_management.service.timetable.TimetableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TimetableServiceImpl implements TimetableService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private RecurringCourseScheduleRepository recurringScheduleRepository;

    @Autowired
    private CourseSectionScheduleRepository courseSectionScheduleRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public TimetableWeekResponse getAdminWeek(Long semesterId,
                                              Long sectionId,
                                              Long lecturerId,
                                              String classroom,
                                              LocalDate requestedWeekStart,
                                              int page,
                                              int size) {
        LocalDate weekStart = normalizeWeekStart(requestedWeekStart);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<CourseSection> sections = courseSectionRepository.findForAdminTimetable(
                semesterId, sectionId, lecturerId);
        List<Long> sectionIds = sections.stream().map(CourseSection::getId).toList();

        Map<Long, List<CourseSectionSchedule>> scheduleHistory = sectionIds.isEmpty()
                ? Map.of()
                : courseSectionScheduleRepository.findByCourseSectionIdInOrderByEffectiveFromAsc(sectionIds)
                .stream()
                .collect(Collectors.groupingBy(entry -> entry.getCourseSection().getId()));
        Map<Long, List<RecurringCourseSchedule>> recurringSchedules = sectionIds.isEmpty()
                ? Map.of()
                : recurringScheduleRepository.findByCourseSectionIdInOrderByStartDateAsc(sectionIds)
                .stream()
                .collect(Collectors.groupingBy(entry -> entry.getCourseSection().getId()));

        String roomFilter = classroom == null ? "" : classroom.trim().toLowerCase(java.util.Locale.ROOT);
        Map<String, TimetableOccurrenceDto> occurrences = new LinkedHashMap<>();
        for (CourseSection section : sections) {
            if (section.getStatus() == CourseSectionStatus.CANCELLED || section.getSemester() == null) {
                continue;
            }
            Semester semester = section.getSemester();
            List<CourseSectionSchedule> history = scheduleHistory.getOrDefault(section.getId(), List.of());
            List<RecurringCourseSchedule> recurring = recurringSchedules.getOrDefault(section.getId(), List.of());

            for (int day = 0; day < 7; day++) {
                LocalDate date = weekStart.plusDays(day);
                if ((semester.getStartDate() != null && date.isBefore(semester.getStartDate()))
                        || (semester.getEndDate() != null && date.isAfter(semester.getEndDate()))) {
                    continue;
                }

                for (RecurringCourseSchedule rule : recurring) {
                    LocalDate ruleEnd = effectiveEnd(rule.getEndDate(), rule.getCancelledFrom());
                    if (date.isBefore(rule.getStartDate()) || date.isAfter(ruleEnd)
                            || date.getDayOfWeek().getValue() != rule.getDayOfWeek()
                            || !matchesRoom(roomFilter, rule.getClassroom())) {
                        continue;
                    }
                    TimetableOccurrenceDto dto = toAdminDto(section, date, rule);
                    occurrences.putIfAbsent(occurrenceKey(dto), dto);
                }

                String rawSchedule = scheduleForDate(history, section.getSchedule(), date);
                for (ScheduleParser.Slot slot : ScheduleParser.parse(rawSchedule)) {
                    if (slot.dayOfWeek() != date.getDayOfWeek().getValue()
                            || !matchesRoom(roomFilter, slot.room())) {
                        continue;
                    }
                    TimetableOccurrenceDto dto = toAdminDto(section, date, slot);
                    occurrences.putIfAbsent(occurrenceKey(dto), dto);
                }
            }
        }

        List<TimetableOccurrenceDto> allItems = new ArrayList<>(occurrences.values());
        allItems.sort(Comparator.comparing(TimetableOccurrenceDto::getDate)
                .thenComparing(TimetableOccurrenceDto::getStartTime)
                .thenComparing(TimetableOccurrenceDto::getSectionCode,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        int safeSize = normalizeSize(size);
        int totalElements = allItems.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int safePage = normalizePage(page, totalPages);
        int fromIndex = totalElements == 0 ? 0 : (safePage - 1) * safeSize;
        int toIndex = totalElements == 0 ? 0 : Math.min(fromIndex + safeSize, totalElements);
        List<TimetableOccurrenceDto> pageItems = totalElements == 0
                ? List.of() : allItems.subList(fromIndex, toIndex);

        Semester selectedSemester = sections.stream()
                .map(CourseSection::getSemester)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        return TimetableWeekResponse.builder()
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .semesterId(semesterId)
                .semesterCode(semesterId != null && selectedSemester != null
                        ? selectedSemester.getSemesterCode() : null)
                .semesterName(semesterId != null && selectedSemester != null
                        ? selectedSemester.getSemesterName() : null)
                .page(safePage)
                .size(safeSize)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .items(pageItems)
                .unscheduledSections(List.of())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TimetableWeekResponse getWeek(String username,
                                         Long semesterId,
                                         LocalDate requestedWeekStart,
                                         int page,
                                         int size) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Student student = user.getStudent();
        if (student == null) {
            throw new BadRequestException("Hồ sơ sinh viên chưa được liên kết với tài khoản");
        }

        LocalDate weekStart = normalizeWeekStart(requestedWeekStart);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<Enrollment> enrollments = semesterId == null
                ? enrollmentRepository.findForTimetable(student.getId(), EnrollmentStatus.ENROLLED)
                : enrollmentRepository.findForTimetableBySemester(student.getId(), EnrollmentStatus.ENROLLED, semesterId);

        List<Long> sectionIds = enrollments.stream()
                .map(Enrollment::getCourseSection)
                .filter(section -> section != null && section.getId() != null)
                .map(CourseSection::getId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, List<CourseSectionSchedule>> scheduleHistory = sectionIds.isEmpty()
                ? Map.of()
                : courseSectionScheduleRepository.findByCourseSectionIdInOrderByEffectiveFromAsc(sectionIds)
                .stream()
                .collect(Collectors.groupingBy(entry -> entry.getCourseSection().getId()));
        Map<Long, List<RecurringCourseSchedule>> recurringSchedules = sectionIds.isEmpty()
                ? Map.of()
                : recurringScheduleRepository.findByCourseSectionIdInOrderByStartDateAsc(sectionIds)
                .stream()
                .collect(Collectors.groupingBy(entry -> entry.getCourseSection().getId()));

        Map<String, TimetableOccurrenceDto> occurrences = new LinkedHashMap<>();
        Map<Long, UnscheduledSectionDto> unscheduledSections = new LinkedHashMap<>();
        for (Enrollment enrollment : enrollments) {
            CourseSection section = enrollment.getCourseSection();
            if (section == null || section.getSemester() == null) {
                continue;
            }
            Semester semester = section.getSemester();
            LocalDate effectiveStart = semester.getStartDate();
            LocalDate effectiveEnd = semester.getEndDate();

            List<CourseSectionSchedule> history = scheduleHistory.getOrDefault(section.getId(), List.of());
            List<RecurringCourseSchedule> recurring = recurringSchedules.getOrDefault(section.getId(), List.of());
            boolean hasScheduleForWeek = false;
            for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
                LocalDate date = weekStart.plusDays(dayOfWeek - 1L);
                if (effectiveStart != null && date.isBefore(effectiveStart)) {
                    continue;
                }
                if (effectiveEnd != null && date.isAfter(effectiveEnd)) {
                    continue;
                }

                for (RecurringCourseSchedule rule : recurring) {
                    LocalDate ruleEnd = effectiveEnd(rule.getEndDate(), rule.getCancelledFrom());
                    if (date.isBefore(rule.getStartDate()) || date.isAfter(ruleEnd)
                            || date.getDayOfWeek().getValue() != rule.getDayOfWeek()) {
                        continue;
                    }
                    hasScheduleForWeek = true;
                    TimetableOccurrenceDto dto = toDto(enrollment, section, rule, date);
                    occurrences.putIfAbsent(dto.getId(), dto);
                }

                String schedule = scheduleForDate(history, section.getSchedule(), date);
                List<ScheduleParser.Slot> slots = ScheduleParser.parse(schedule);
                hasScheduleForWeek |= !slots.isEmpty();
                for (ScheduleParser.Slot slot : slots) {
                    if (slot.dayOfWeek() != dayOfWeek) {
                        continue;
                    }
                    TimetableOccurrenceDto dto = toDto(enrollment, section, slot, date);
                    occurrences.putIfAbsent(dto.getId(), dto);
                }
            }
            if (recurring.isEmpty() && !hasScheduleForWeek && section.getId() != null
                    && (effectiveStart == null || !weekEnd.isBefore(effectiveStart))
                    && (effectiveEnd == null || !weekStart.isAfter(effectiveEnd))) {
                Course course = section.getCourse();
                unscheduledSections.putIfAbsent(section.getId(), UnscheduledSectionDto.builder()
                        .sectionId(section.getId())
                        .sectionCode(section.getSectionCode())
                        .courseCode(course != null ? course.getCourseCode() : null)
                        .courseName(course != null ? course.getCourseName() : null)
                        .build());
            }
        }

        List<TimetableOccurrenceDto> allItems = new ArrayList<>(occurrences.values());
        allItems.sort(Comparator
                .comparing(TimetableOccurrenceDto::getDate)
                .thenComparing(TimetableOccurrenceDto::getStartTime)
                .thenComparing(TimetableOccurrenceDto::getSectionCode,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        int safeSize = normalizeSize(size);
        int totalElements = allItems.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int safePage = normalizePage(page, totalPages);

        int fromIndex = totalElements == 0 ? 0 : (safePage - 1) * safeSize;
        int toIndex = totalElements == 0 ? 0 : Math.min(fromIndex + safeSize, totalElements);
        List<TimetableOccurrenceDto> pageItems = totalElements == 0
                ? List.of()
                : allItems.subList(fromIndex, toIndex);

        Semester selectedSemester = semesterId == null ? null : semesterRepository.findById(semesterId).orElse(null);
        if (selectedSemester == null && !enrollments.isEmpty()) {
            selectedSemester = enrollments.get(0).getCourseSection().getSemester();
        }

        return TimetableWeekResponse.builder()
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .semesterId(selectedSemester != null ? selectedSemester.getId() : semesterId)
                .semesterCode(selectedSemester != null ? selectedSemester.getSemesterCode() : null)
                .semesterName(selectedSemester != null ? selectedSemester.getSemesterName() : null)
                .page(safePage)
                .size(safeSize)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .items(pageItems)
                .unscheduledSections(List.copyOf(unscheduledSections.values()))
                .build();
    }

    private TimetableWeekResponse emptyResponse(Long semesterId, LocalDate weekStart, int page, int size) {
        int safeSize = normalizeSize(size);
        Semester semester = semesterId == null ? null : semesterRepository.findById(semesterId).orElse(null);
        return TimetableWeekResponse.builder()
                .weekStart(weekStart)
                .weekEnd(weekStart.plusDays(6))
                .semesterId(semester != null ? semester.getId() : semesterId)
                .semesterCode(semester != null ? semester.getSemesterCode() : null)
                .semesterName(semester != null ? semester.getSemesterName() : null)
                .page(1)
                .size(safeSize)
                .totalPages(0)
                .totalElements(0)
                .items(List.of())
                .unscheduledSections(List.of())
                .build();
    }

    private TimetableOccurrenceDto toDto(Enrollment enrollment,
                                         CourseSection section,
                                         ScheduleParser.Slot slot,
                                         LocalDate date) {
        Course course = section.getCourse();
        String startTime = ScheduleParser.format(slot.startTime());
        String endTime = ScheduleParser.format(slot.endTime());
        return TimetableOccurrenceDto.builder()
                .id(enrollment.getId() + ":" + date + ":" + startTime)
                .enrollmentId(enrollment.getId())
                .sectionId(section.getId())
                .courseId(course != null ? course.getId() : null)
                .semesterId(section.getSemester().getId())
                .semesterCode(section.getSemester().getSemesterCode())
                .semesterName(section.getSemester().getSemesterName())
                .courseCode(course != null ? course.getCourseCode() : null)
                .courseName(course != null ? course.getCourseName() : null)
                .credits(course != null ? course.getCredits() : null)
                .sectionCode(section.getSectionCode())
                .sectionStatus(section.getStatus() != null ? section.getStatus().name() : null)
                .lecturerName(section.getLecturer() != null ? section.getLecturer().getFullName() : null)
                .room(slot.room())
                .date(date)
                .dayOfWeek(slot.dayOfWeek())
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }

    private TimetableOccurrenceDto toAdminDto(CourseSection section,
                                              LocalDate date,
                                              RecurringCourseSchedule rule) {
        Course course = section.getCourse();
        return TimetableOccurrenceDto.builder()
                .id(section.getId() + ":" + date + ":" + rule.getStartTime() + ":" + rule.getId())
                .sectionId(section.getId())
                .courseId(course != null ? course.getId() : null)
                .semesterId(section.getSemester().getId())
                .semesterCode(section.getSemester().getSemesterCode())
                .semesterName(section.getSemester().getSemesterName())
                .courseCode(course != null ? course.getCourseCode() : null)
                .courseName(course != null ? course.getCourseName() : null)
                .credits(course != null ? course.getCredits() : null)
                .sectionCode(section.getSectionCode())
                .sectionStatus(section.getStatus() != null ? section.getStatus().name() : null)
                .lecturerName(section.getLecturer() != null ? section.getLecturer().getFullName() : null)
                .room(rule.getClassroom())
                .date(date)
                .dayOfWeek(rule.getDayOfWeek())
                .startTime(ScheduleParser.format(rule.getStartTime()))
                .endTime(ScheduleParser.format(rule.getEndTime()))
                .startPeriod(rule.getStartPeriod())
                .endPeriod(rule.getEndPeriod())
                .build();
    }

    private TimetableOccurrenceDto toAdminDto(CourseSection section,
                                              LocalDate date,
                                              ScheduleParser.Slot slot) {
        Course course = section.getCourse();
        String startTime = ScheduleParser.format(slot.startTime());
        return TimetableOccurrenceDto.builder()
                .id(section.getId() + ":" + date + ":" + startTime)
                .sectionId(section.getId())
                .courseId(course != null ? course.getId() : null)
                .semesterId(section.getSemester().getId())
                .semesterCode(section.getSemester().getSemesterCode())
                .semesterName(section.getSemester().getSemesterName())
                .courseCode(course != null ? course.getCourseCode() : null)
                .courseName(course != null ? course.getCourseName() : null)
                .credits(course != null ? course.getCredits() : null)
                .sectionCode(section.getSectionCode())
                .sectionStatus(section.getStatus() != null ? section.getStatus().name() : null)
                .lecturerName(section.getLecturer() != null ? section.getLecturer().getFullName() : null)
                .room(slot.room())
                .date(date)
                .dayOfWeek(slot.dayOfWeek())
                .startTime(startTime)
                .endTime(ScheduleParser.format(slot.endTime()))
                .build();
    }

    private String occurrenceKey(TimetableOccurrenceDto occurrence) {
        return occurrence.getSectionId() + ":" + occurrence.getDate() + ":"
                + occurrence.getStartTime() + ":" + occurrence.getEndTime();
    }

    private boolean matchesRoom(String filter, String room) {
        return filter.isEmpty() || (room != null && room.toLowerCase(java.util.Locale.ROOT).contains(filter));
    }

    private TimetableOccurrenceDto toDto(Enrollment enrollment,
                                         CourseSection section,
                                         RecurringCourseSchedule rule,
                                         LocalDate date) {
        Course course = section.getCourse();
        return TimetableOccurrenceDto.builder()
                .id(enrollment.getId() + ":" + date + ":" + rule.getStartTime() + ":" + rule.getId())
                .enrollmentId(enrollment.getId())
                .sectionId(section.getId())
                .courseId(course != null ? course.getId() : null)
                .semesterId(section.getSemester().getId())
                .semesterCode(section.getSemester().getSemesterCode())
                .semesterName(section.getSemester().getSemesterName())
                .courseCode(course != null ? course.getCourseCode() : null)
                .courseName(course != null ? course.getCourseName() : null)
                .credits(course != null ? course.getCredits() : null)
                .sectionCode(section.getSectionCode())
                .sectionStatus(section.getStatus() != null ? section.getStatus().name() : null)
                .lecturerName(section.getLecturer() != null ? section.getLecturer().getFullName() : null)
                .room(rule.getClassroom())
                .date(date)
                .dayOfWeek(rule.getDayOfWeek())
                .startTime(ScheduleParser.format(rule.getStartTime()))
                .endTime(ScheduleParser.format(rule.getEndTime()))
                .startPeriod(rule.getStartPeriod())
                .endPeriod(rule.getEndPeriod())
                .build();
    }

    private String scheduleForDate(List<CourseSectionSchedule> history, String currentSchedule, LocalDate date) {
        if (history.isEmpty()) {
            return currentSchedule;
        }
        return history.stream()
                .filter(entry -> entry.getCancelledFrom() == null || date.isBefore(entry.getCancelledFrom()))
                .filter(entry -> !date.isBefore(entry.getEffectiveFrom()))
                .filter(entry -> entry.getEffectiveTo() == null || !date.isAfter(entry.getEffectiveTo()))
                .map(CourseSectionSchedule::getSchedule)
                .findFirst()
                .orElse(null);
    }

    private LocalDate effectiveEnd(LocalDate endDate, LocalDate cancelledFrom) {
        if (cancelledFrom == null) {
            return endDate;
        }
        LocalDate cancellationEnd = cancelledFrom.minusDays(1);
        return endDate.isBefore(cancellationEnd) ? endDate : cancellationEnd;
    }

    private LocalDate normalizeWeekStart(LocalDate requested) {
        LocalDate base = requested != null ? requested : LocalDate.now(BUSINESS_ZONE);
        return base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private int normalizePage(int page, int totalPages) {
        if (totalPages <= 0) {
            return 1;
        }
        return Math.min(Math.max(page, 1), totalPages);
    }
}

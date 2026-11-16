package com.example.student_management.service.exam.impl;

import com.example.student_management.dto.exam.ExamScheduleDto;
import com.example.student_management.dto.exam.ExamScheduleRequest;
import com.example.student_management.dto.exam.ExamStudentDto;
import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.academic.Enrollment;
import com.example.student_management.entity.academic.EnrollmentStatus;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.entity.exam.ExamRoom;
import com.example.student_management.entity.exam.ExamRoomStatus;
import com.example.student_management.entity.exam.ExamSchedule;
import com.example.student_management.entity.exam.ExamScheduleStatus;
import com.example.student_management.entity.exam.ExamType;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.repository.exam.ExamRoomRepository;
import com.example.student_management.repository.exam.ExamScheduleRepository;
import com.example.student_management.service.exam.ExamScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamScheduleServiceImpl implements ExamScheduleService {

    @Autowired
    private ExamScheduleRepository examScheduleRepository;

    @Autowired
    private ExamRoomRepository examRoomRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<ExamScheduleDto> searchSchedules(
            String keyword,
            Long semesterId,
            Long courseId,
            ExamScheduleStatus status,
            ExamType examType,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        String kw = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return examScheduleRepository.searchSchedules(kw, semesterId, courseId, status, examType, fromDate, toDate, pageable)
                .map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public long getEligibleStudentCount(Long courseSectionId) {
        if (!courseSectionRepository.existsById(courseSectionId)) {
            throw new NotFoundException("Lớp học phần ID " + courseSectionId + " không tồn tại");
        }
        return enrollmentRepository.countByCourseSectionIdAndStatus(courseSectionId, EnrollmentStatus.ENROLLED);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamScheduleDto> getSchedulesForExport(
            String keyword,
            Long semesterId,
            Long courseId,
            ExamScheduleStatus status,
            ExamType examType,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        List<ExamScheduleDto> schedules = new ArrayList<>();
        int page = 0;
        Page<ExamScheduleDto> result;
        do {
            result = searchSchedules(keyword, semesterId, courseId, status, examType, fromDate, toDate,
                    org.springframework.data.domain.PageRequest.of(page++, 500, org.springframework.data.domain.Sort.by("examDate").ascending()));
            schedules.addAll(result.getContent());
        } while (result.hasNext());
        return schedules;
    }

    @Override
    @Transactional(readOnly = true)
    public ExamScheduleDto getScheduleById(Long id) {
        ExamSchedule schedule = findScheduleById(id);
        return mapToDto(schedule);
    }

    @Override
    @Transactional
    public ExamScheduleDto createSchedule(ExamScheduleRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime());

        CourseSection section = courseSectionRepository.findById(request.getCourseSectionId())
                .orElseThrow(() -> new NotFoundException("Lớp học phần ID " + request.getCourseSectionId() + " không tồn tại"));

        // Lấy số sinh viên đăng ký hợp lệ
        long enrolledCount = enrollmentRepository.countByCourseSectionIdAndStatus(section.getId(), EnrollmentStatus.ENROLLED);

        ExamRoom room = null;
        if (request.getExamType() == ExamType.OFFLINE) {
            if (request.getRoomId() == null) {
                throw new BadRequestException("Hình thức thi trực tiếp (OFFLINE) bắt buộc phải chọn phòng thi");
            }
            room = examRoomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new NotFoundException("Phòng thi ID " + request.getRoomId() + " không tồn tại"));

            validateRoomAvailability(room, request.getExamDate(), request.getStartTime(), request.getEndTime(), enrolledCount, null);
        } else if (request.getExamType() == ExamType.ONLINE && request.getRoomId() != null) {
            // Nếu thi online nhưng vẫn chỉ định phòng thi tập trung
            room = examRoomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new NotFoundException("Phòng thi ID " + request.getRoomId() + " không tồn tại"));
            validateRoomAvailability(room, request.getExamDate(), request.getStartTime(), request.getEndTime(), enrolledCount, null);
        }

        // Kiểm tra xung đột lịch thi của sinh viên
        validateStudentConflict(section.getId(), request.getExamDate(), request.getStartTime(), request.getEndTime(), null);

        ExamSchedule schedule = ExamSchedule.builder()
                .courseSection(section)
                .examDate(request.getExamDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .examType(request.getExamType())
                .room(room)
                .examLocation(request.getExamLocation() != null ? request.getExamLocation().trim() : null)
                .examLink(request.getExamLink() != null ? request.getExamLink().trim() : null)
                .status(request.getStatus() != null ? request.getStatus() : ExamScheduleStatus.SCHEDULED)
                .notes(request.getNotes())
                .build();

        ExamSchedule saved = examScheduleRepository.save(schedule);
        return mapToDto(saved);
    }

    @Override
    @Transactional
    public ExamScheduleDto updateSchedule(Long id, ExamScheduleRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime());

        ExamSchedule schedule = findScheduleById(id);

        // Quy tắc: Không cho sửa lịch đã hoàn thành (COMPLETED) trái quy tắc nghiệp vụ
        if (schedule.getStatus() == ExamScheduleStatus.COMPLETED) {
            boolean timeOrRoomChanged = !schedule.getExamDate().equals(request.getExamDate())
                    || !schedule.getStartTime().equals(request.getStartTime())
                    || !schedule.getEndTime().equals(request.getEndTime())
                    || (schedule.getRoom() == null && request.getRoomId() != null)
                    || (schedule.getRoom() != null && !schedule.getRoom().getId().equals(request.getRoomId()))
                    || !schedule.getCourseSection().getId().equals(request.getCourseSectionId())
                    || request.getStatus() == ExamScheduleStatus.SCHEDULED;

            if (timeOrRoomChanged) {
                throw new BadRequestException("Lịch thi đã hoàn thành (COMPLETED). Không được phép chỉnh sửa ngày thi, giờ thi, phòng thi hoặc lớp học phần.");
            }
        }

        CourseSection section = courseSectionRepository.findById(request.getCourseSectionId())
                .orElseThrow(() -> new NotFoundException("Lớp học phần ID " + request.getCourseSectionId() + " không tồn tại"));

        long enrolledCount = enrollmentRepository.countByCourseSectionIdAndStatus(section.getId(), EnrollmentStatus.ENROLLED);

        ExamRoom room = null;
        if (request.getExamType() == ExamType.OFFLINE) {
            if (request.getRoomId() == null) {
                throw new BadRequestException("Hình thức thi trực tiếp (OFFLINE) bắt buộc phải chọn phòng thi");
            }
            room = examRoomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new NotFoundException("Phòng thi ID " + request.getRoomId() + " không tồn tại"));

            validateRoomAvailability(room, request.getExamDate(), request.getStartTime(), request.getEndTime(), enrolledCount, id);
        } else if (request.getExamType() == ExamType.ONLINE && request.getRoomId() != null) {
            room = examRoomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new NotFoundException("Phòng thi ID " + request.getRoomId() + " không tồn tại"));
            validateRoomAvailability(room, request.getExamDate(), request.getStartTime(), request.getEndTime(), enrolledCount, id);
        }

        // Kiểm tra xung đột lịch thi của sinh viên (bỏ qua chính lịch thi đang cập nhật)
        validateStudentConflict(section.getId(), request.getExamDate(), request.getStartTime(), request.getEndTime(), id);

        schedule.setCourseSection(section);
        schedule.setExamDate(request.getExamDate());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setExamType(request.getExamType());
        schedule.setRoom(room);
        schedule.setExamLocation(request.getExamLocation() != null ? request.getExamLocation().trim() : null);
        schedule.setExamLink(request.getExamLink() != null ? request.getExamLink().trim() : null);
        if (request.getStatus() != null) {
            schedule.setStatus(request.getStatus());
        }
        schedule.setNotes(request.getNotes());

        ExamSchedule updated = examScheduleRepository.save(schedule);
        return mapToDto(updated);
    }

    @Override
    @Transactional
    public void deleteSchedule(Long id) {
        ExamSchedule schedule = findScheduleById(id);
        examScheduleRepository.delete(schedule);
    }

    @Override
    @Transactional
    public ExamScheduleDto cancelSchedule(Long id) {
        ExamSchedule schedule = findScheduleById(id);
        if (schedule.getStatus() == ExamScheduleStatus.COMPLETED) {
            throw new BadRequestException("Không thể hủy lịch thi đã hoàn thành (COMPLETED)");
        }
        schedule.setStatus(ExamScheduleStatus.CANCELLED);
        ExamSchedule updated = examScheduleRepository.save(schedule);
        return mapToDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamStudentDto> getStudentsByScheduleId(Long id) {
        ExamSchedule schedule = findScheduleById(id);
        List<Enrollment> enrollments = enrollmentRepository.findByCourseSectionIdAndStatus(
                schedule.getCourseSection().getId(), EnrollmentStatus.ENROLLED);

        return enrollments.stream().map(e -> {
            Student s = e.getStudent();
            return ExamStudentDto.builder()
                    .studentId(s.getId())
                    .studentCode(s.getStudentCode())
                    .fullName(s.getFullName())
                    .email(s.getEmail())
                    .className(s.getClassName())
                    .department(s.getDepartment())
                    .registeredAt(e.getRegisteredAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamScheduleDto> getStudentSchedules(String username, Long semesterId, ExamScheduleStatus status) {
        User user = getStudentUser(username);
        Student student = user.getStudent();

        List<Enrollment> enrollments = enrollmentRepository.findByStudentIdAndStatus(student.getId(), EnrollmentStatus.ENROLLED);
        if (enrollments.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> sectionIds = enrollments.stream()
                .map(e -> e.getCourseSection().getId())
                .collect(Collectors.toList());

        List<ExamSchedule> schedules = examScheduleRepository.findByCourseSectionIdIn(sectionIds);

        return schedules.stream()
                .filter(s -> {
                    // Lọc theo học kỳ nếu có yêu cầu
                    if (semesterId != null && !s.getCourseSection().getSemester().getId().equals(semesterId)) {
                        return false;
                    }
                    // Lọc theo trạng thái
                    if (status != null) {
                        return s.getStatus() == status;
                    }
                    // Mặc định: Lịch đã hủy không hiển thị như kỳ thi sắp tới
                    return s.getStatus() != ExamScheduleStatus.CANCELLED;
                })
                .sorted(Comparator.comparing(ExamSchedule::getExamDate).thenComparing(ExamSchedule::getStartTime))
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ExamScheduleDto getStudentScheduleDetail(String username, Long scheduleId) {
        User user = getStudentUser(username);
        Student student = user.getStudent();

        ExamSchedule schedule = findScheduleById(scheduleId);

        // Bảo mật: Sinh viên A tuyệt đối không được xem lịch thi của sinh viên B
        boolean isEnrolled = enrollmentRepository.existsByStudentIdAndCourseSectionIdAndStatus(
                student.getId(), schedule.getCourseSection().getId(), EnrollmentStatus.ENROLLED);

        if (!isEnrolled) {
            throw new ForbiddenException("Bạn không có quyền xem chi tiết lịch thi này vì không tham gia lớp học phần tương ứng");
        }

        return mapToDto(schedule);
    }

    /**
     * Kiểm tra tính hợp lệ của khoảng thời gian
     */
    private void validateTimeRange(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new BadRequestException("Giờ bắt đầu và giờ kết thúc thi không được để trống");
        }
        if (!end.isAfter(start)) {
            throw new BadRequestException("Giờ kết thúc (" + end + ") phải lớn hơn giờ bắt đầu (" + start + ")");
        }
    }

    /**
     * Kiểm tra phòng thi: trạng thái hoạt động, sức chứa và trùng lịch phòng
     */
    private void validateRoomAvailability(
            ExamRoom room,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            long enrolledCount,
            Long excludeScheduleId
    ) {
        if (room.getStatus() != ExamRoomStatus.ACTIVE) {
            throw new BadRequestException("Phòng thi [" + room.getRoomCode() + "] đang ngừng hoạt động (INACTIVE), không thể xếp lịch thi");
        }

        if (enrolledCount > room.getCapacity()) {
            throw new ConflictException("Số lượng sinh viên dự thi (" + enrolledCount
                    + ") vượt quá sức chứa của phòng thi " + room.getRoomCode()
                    + " (sức chứa tối đa: " + room.getCapacity() + ")");
        }

        // Lấy tất cả lịch thi cùng ngày tại phòng này (bỏ qua lịch CANCELLED)
        List<ExamSchedule> roomSchedules = examScheduleRepository.findByRoomIdAndExamDateAndStatusNot(
                room.getId(), date, ExamScheduleStatus.CANCELLED);

        for (ExamSchedule other : roomSchedules) {
            if (excludeScheduleId != null && other.getId().equals(excludeScheduleId)) {
                continue;
            }
            if (isTimeOverlapping(start, end, other.getStartTime(), other.getEndTime())) {
                throw new ConflictException(String.format(
                        "Phòng thi [%s] đã có lịch thi trùng vào ngày %s, khung giờ %s - %s (Lớp học phần: %s)",
                        room.getRoomCode(),
                        date,
                        other.getStartTime(),
                        other.getEndTime(),
                        other.getCourseSection().getSectionCode()
                ));
            }
        }
    }

    /**
     * Kiểm tra trùng lịch thi của sinh viên
     */
    private void validateStudentConflict(
            Long courseSectionId,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            Long excludeScheduleId
    ) {
        List<Enrollment> currentEnrollments = enrollmentRepository.findByCourseSectionIdAndStatus(
                courseSectionId, EnrollmentStatus.ENROLLED);

        if (currentEnrollments.isEmpty()) {
            return;
        }

        Map<Long, Student> studentMap = currentEnrollments.stream()
                .map(Enrollment::getStudent)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Student::getId, s -> s, (a, b) -> a));

        Set<Long> studentIds = studentMap.keySet();

        // Lấy tất cả lịch thi cùng ngày (không CANCELLED)
        List<ExamSchedule> sameDaySchedules = examScheduleRepository.findByExamDateAndStatusNot(
                date, ExamScheduleStatus.CANCELLED);

        List<String> conflicts = new ArrayList<>();

        for (ExamSchedule other : sameDaySchedules) {
            if (excludeScheduleId != null && other.getId().equals(excludeScheduleId)) {
                continue;
            }
            if (other.getCourseSection().getId().equals(courseSectionId)) {
                continue;
            }
            if (isTimeOverlapping(start, end, other.getStartTime(), other.getEndTime())) {
                // Kiểm tra xem có sinh viên nào của lớp này cũng tham gia lớp của other không
                List<Enrollment> otherEnrollments = enrollmentRepository.findByCourseSectionIdAndStatus(
                        other.getCourseSection().getId(), EnrollmentStatus.ENROLLED);

                for (Enrollment oe : otherEnrollments) {
                    Student os = oe.getStudent();
                    if (os != null && studentIds.contains(os.getId())) {
                        conflicts.add(String.format("Sinh viên %s (%s) bị trùng lịch với môn '%s' - Lớp %s (Khung giờ %s - %s)",
                                os.getFullName(),
                                os.getStudentCode(),
                                other.getCourseSection().getCourse().getCourseName(),
                                other.getCourseSection().getSectionCode(),
                                other.getStartTime(),
                                other.getEndTime()
                        ));
                    }
                }
            }
        }

        if (!conflicts.isEmpty()) {
            throw new ConflictException("Xung đột lịch thi của sinh viên: " + String.join("; ", conflicts));
        }
    }

    /**
     * Kiểm tra 2 khoảng thời gian có giao nhau không
     * [startA, endA] và [startB, endB] giao nhau khi và chỉ khi startA < endB && endA > startB
     */
    private boolean isTimeOverlapping(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    private ExamSchedule findScheduleById(Long id) {
        return examScheduleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lịch thi với ID " + id + " không tồn tại"));
    }

    private User getStudentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
        if (user.getRole() != Role.STUDENT || user.getStudent() == null) {
            throw new ForbiddenException("Chỉ tài khoản sinh viên mới có quyền xem lịch thi cá nhân");
        }
        return user;
    }

    private ExamScheduleDto mapToDto(ExamSchedule schedule) {
        CourseSection cs = schedule.getCourseSection();
        ExamRoom r = schedule.getRoom();
        long enrolledCount = enrollmentRepository.countByCourseSectionIdAndStatus(cs.getId(), EnrollmentStatus.ENROLLED);

        return ExamScheduleDto.builder()
                .id(schedule.getId())
                .courseSectionId(cs.getId())
                .sectionCode(cs.getSectionCode())
                .courseCode(cs.getCourse() != null ? cs.getCourse().getCourseCode() : null)
                .courseName(cs.getCourse() != null ? cs.getCourse().getCourseName() : null)
                .credits(cs.getCourse() != null ? cs.getCourse().getCredits() : null)
                .semesterId(cs.getSemester() != null ? cs.getSemester().getId() : null)
                .semesterName(cs.getSemester() != null ? cs.getSemester().getSemesterName() : null)
                .lecturerName(cs.getLecturer() != null ? cs.getLecturer().getFullName() : null)
                .examDate(schedule.getExamDate())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .examType(schedule.getExamType())
                .roomId(r != null ? r.getId() : null)
                .roomCode(r != null ? r.getRoomCode() : null)
                .roomName(r != null ? r.getRoomName() : null)
                .roomCapacity(r != null ? r.getCapacity() : null)
                .building(r != null ? r.getBuilding() : null)
                .examLocation(schedule.getExamLocation())
                .examLink(schedule.getExamLink())
                .status(schedule.getStatus())
                .notes(schedule.getNotes())
                .enrolledStudentsCount((int) enrolledCount)
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }
}


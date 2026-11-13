package com.example.student_management.controller.academic;

import com.example.student_management.entity.academic.Semester;
import com.example.student_management.entity.academic.SemesterStatus;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.SemesterRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/semesters")
public class SemesterController {
    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @GetMapping
    public ResponseEntity<List<Semester>> getAll() {
        List<Semester> semesters = semesterRepository.findAll();
        Set<Long> linkedSemesterIds = courseSectionRepository.findSemesterIdsWithSections()
                .stream().collect(Collectors.toSet());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        semesters.forEach(semester -> semester.setDeleteBlockedReason(
                deleteBlockedReason(semester, linkedSemesterIds.contains(semester.getId()), today, now)));
        return ResponseEntity.ok(semesters);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Semester> create(@RequestBody Semester semester) {
        return ResponseEntity.ok(semesterRepository.save(semester));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Semester> update(@PathVariable Long id, @RequestBody Semester semester) {
        return semesterRepository.findById(id)
                .map(existing -> {
                    existing.setSemesterName(semester.getSemesterName());
                    existing.setStartDate(semester.getStartDate());
                    existing.setEndDate(semester.getEndDate());
                    existing.setRegistrationStart(semester.getRegistrationStart());
                    existing.setRegistrationEnd(semester.getRegistrationEnd());
                    existing.setStatus(semester.getStatus());
                    return ResponseEntity.ok(semesterRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Semester not found"));

        String blockedReason = deleteBlockedReason(
                semester,
                courseSectionRepository.existsBySemesterId(id),
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),
                LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        if (blockedReason != null) {
            throw new BadRequestException("Không thể xóa học kỳ. " + blockedReason);
        }

        semesterRepository.delete(semester);
        return ResponseEntity.noContent().build();
    }

    private String deleteBlockedReason(Semester semester, boolean hasSections,
                                       LocalDate today, LocalDateTime now) {
        if (hasSections) {
            return "Học kỳ đang có lớp học phần; cần giữ lại đăng ký, bảng điểm và lịch sử lịch học liên quan.";
        }
        if (semester.getStatus() == SemesterStatus.REGISTRATION_OPEN
                || isRegistrationOpen(semester, now)) {
            return "Học kỳ đang mở đăng ký.";
        }
        if (semester.getStatus() == SemesterStatus.IN_PROGRESS
                || (semester.getStartDate() != null && semester.getEndDate() != null
                && !today.isBefore(semester.getStartDate()) && !today.isAfter(semester.getEndDate()))) {
            return "Học kỳ đang diễn ra.";
        }
        return null;
    }

    private boolean isRegistrationOpen(Semester semester, LocalDateTime now) {
        return semester.getRegistrationStart() != null
                && semester.getRegistrationEnd() != null
                && !now.isBefore(semester.getRegistrationStart())
                && !now.isAfter(semester.getRegistrationEnd());
    }
}

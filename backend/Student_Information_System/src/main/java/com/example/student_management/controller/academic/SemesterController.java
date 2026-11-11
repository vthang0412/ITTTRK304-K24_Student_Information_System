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

import java.util.List;

@RestController
@RequestMapping("/api/semesters")
public class SemesterController {
    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @GetMapping
    public ResponseEntity<List<Semester>> getAll() {
        return ResponseEntity.ok(semesterRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Semester> create(@RequestBody Semester semester) {
        if (semester.getSemesterCode() == null || semester.getSemesterCode().isBlank()) {
            throw new BadRequestException("Mã học kỳ không được để trống");
        }
        if (semester.getSemesterName() == null || semester.getSemesterName().isBlank()) {
            throw new BadRequestException("Tên học kỳ không được để trống");
        }
        if (semester.getStartDate() != null && semester.getEndDate() != null
                && !semester.getEndDate().isAfter(semester.getStartDate())) {
            throw new BadRequestException("Ngày kết thúc học kỳ phải sau ngày bắt đầu");
        }
        if (semester.getRegistrationStart() != null && semester.getRegistrationEnd() != null
                && !semester.getRegistrationEnd().isAfter(semester.getRegistrationStart())) {
            throw new BadRequestException("Ngày kết thúc đăng ký phải sau ngày bắt đầu đăng ký");
        }
        semester.setSemesterCode(semester.getSemesterCode().trim());
        semester.setSemesterName(semester.getSemesterName().trim());
        return ResponseEntity.ok(semesterRepository.save(semester));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Semester> update(@PathVariable Long id, @RequestBody Semester semester) {
        if (semester.getSemesterName() == null || semester.getSemesterName().isBlank()) {
            throw new BadRequestException("Tên học kỳ không được để trống");
        }
        if (semester.getStartDate() != null && semester.getEndDate() != null
                && !semester.getEndDate().isAfter(semester.getStartDate())) {
            throw new BadRequestException("Ngày kết thúc học kỳ phải sau ngày bắt đầu");
        }
        if (semester.getRegistrationStart() != null && semester.getRegistrationEnd() != null
                && !semester.getRegistrationEnd().isAfter(semester.getRegistrationStart())) {
            throw new BadRequestException("Ngày kết thúc đăng ký phải sau ngày bắt đầu đăng ký");
        }
        return semesterRepository.findById(id)
                .map(existing -> {
                    existing.setSemesterName(semester.getSemesterName().trim());
                    existing.setStartDate(semester.getStartDate());
                    existing.setEndDate(semester.getEndDate());
                    existing.setRegistrationStart(semester.getRegistrationStart());
                    existing.setRegistrationEnd(semester.getRegistrationEnd());
                    if (semester.getStatus() != null) {
                        existing.setStatus(semester.getStatus());
                    }
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

        if (courseSectionRepository.existsBySemesterId(id)) {
            throw new BadRequestException(
                    "Không thể xóa học kỳ vì đang có ít nhất một học phần được liên kết với học kỳ này.");
        }

        semesterRepository.delete(semester);
        return ResponseEntity.noContent().build();
    }
}

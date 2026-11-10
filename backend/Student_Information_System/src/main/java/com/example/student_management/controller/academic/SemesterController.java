package com.example.student_management.controller.academic;

import com.example.student_management.entity.academic.Semester;
import com.example.student_management.entity.academic.SemesterStatus;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
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
            throw new BadRequestException("Mã học kỳ là bắt buộc");
        }
        if (semester.getSemesterName() == null || semester.getSemesterName().isBlank()) {
            throw new BadRequestException("Tên học kỳ là bắt buộc");
        }
        String code = semester.getSemesterCode().trim();
        if (semesterRepository.existsBySemesterCode(code)) {
            throw new ConflictException("Mã học kỳ đã tồn tại");
        }
        semester.setSemesterCode(code);
        if (semester.getStartDate() == null || semester.getEndDate() == null) {
            throw new BadRequestException("Ngày bắt đầu và ngày kết thúc học kỳ là bắt buộc");
        }
        if (!semester.getEndDate().isAfter(semester.getStartDate())) {
            throw new BadRequestException("Ngày kết thúc phải sau ngày bắt đầu");
        }
        if (semester.getRegistrationStart() != null && semester.getRegistrationEnd() != null) {
            if (!semester.getRegistrationEnd().isAfter(semester.getRegistrationStart())) {
                throw new BadRequestException("Thời gian kết thúc đăng ký phải sau thời gian bắt đầu đăng ký");
            }
        }
        return ResponseEntity.ok(semesterRepository.save(semester));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Semester> update(@PathVariable Long id, @RequestBody Semester semester) {
        return semesterRepository.findById(id)
                .map(existing -> {
                    if (semester.getSemesterName() != null && !semester.getSemesterName().isBlank()) {
                        existing.setSemesterName(semester.getSemesterName().trim());
                    }
                    java.time.LocalDate newStart = semester.getStartDate() != null ? semester.getStartDate() : existing.getStartDate();
                    java.time.LocalDate newEnd = semester.getEndDate() != null ? semester.getEndDate() : existing.getEndDate();
                    if (newStart != null && newEnd != null && !newEnd.isAfter(newStart)) {
                        throw new BadRequestException("Ngày kết thúc phải sau ngày bắt đầu");
                    }
                    existing.setStartDate(newStart);
                    existing.setEndDate(newEnd);

                    java.time.LocalDateTime regStart = semester.getRegistrationStart() != null ? semester.getRegistrationStart() : existing.getRegistrationStart();
                    java.time.LocalDateTime regEnd = semester.getRegistrationEnd() != null ? semester.getRegistrationEnd() : existing.getRegistrationEnd();
                    if (regStart != null && regEnd != null && !regEnd.isAfter(regStart)) {
                        throw new BadRequestException("Thời gian kết thúc đăng ký phải sau thời gian bắt đầu đăng ký");
                    }
                    existing.setRegistrationStart(regStart);
                    existing.setRegistrationEnd(regEnd);

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

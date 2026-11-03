package com.example.student_management.controller;

import com.example.student_management.entity.Semester;
import com.example.student_management.entity.SemesterStatus;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.CourseSectionRepository;
import com.example.student_management.repository.SemesterRepository;
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

        if (courseSectionRepository.existsBySemesterId(id)) {
            throw new BadRequestException(
                    "Không thể xóa học kỳ vì học kỳ này đang còn học phần hoặc dữ liệu liên quan cần được giữ lại.");
        }

        if (semester.getStatus() == SemesterStatus.REGISTRATION_OPEN
                || semester.getStatus() == SemesterStatus.IN_PROGRESS) {
            throw new BadRequestException("Không thể xóa học kỳ vì học kỳ đang mở đăng ký hoặc đang diễn ra.");
        }

        semesterRepository.delete(semester);
        return ResponseEntity.noContent().build();
    }
}

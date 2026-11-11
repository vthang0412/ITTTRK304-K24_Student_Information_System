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
                    "Không thể xóa học kỳ vì đang có ít nhất một học phần được liên kết với học kỳ này.");
        }

        semesterRepository.delete(semester);
        return ResponseEntity.noContent().build();
    }
}

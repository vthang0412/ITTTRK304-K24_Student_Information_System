package com.example.student_management.controller;



import com.example.student_management.entity.Lecturer;
import com.example.student_management.repository.LecturerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lecturers")
public class LecturerController {
    @Autowired
    private LecturerRepository lecturerRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Lecturer>> getAll() {
        return ResponseEntity.ok(lecturerRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Lecturer> create(@RequestBody Lecturer lecturer) {
        return ResponseEntity.ok(lecturerRepository.save(lecturer));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Lecturer> update(@PathVariable Long id, @RequestBody Lecturer lecturer) {
        return lecturerRepository.findById(id)
                .map(existing -> {
                    existing.setFullName(lecturer.getFullName());
                    existing.setEmail(lecturer.getEmail());
                    existing.setDepartment(lecturer.getDepartment());
                    existing.setStatus(lecturer.getStatus());
                    return ResponseEntity.ok(lecturerRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

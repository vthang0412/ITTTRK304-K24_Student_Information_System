package com.example.student_management.controller;

import com.example.student_management.entity.Lecturer;
import com.example.student_management.repository.LecturerRepository;
import com.example.student_management.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lecturers")
public class LecturerController {
    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private UserRepository userRepository;

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
    @Transactional
    public ResponseEntity<Lecturer> update(@PathVariable Long id, @RequestBody Lecturer lecturer) {
        return lecturerRepository.findById(id)
                .map(existing -> {
                    existing.setFullName(lecturer.getFullName());
                    existing.setEmail(lecturer.getEmail());
                    existing.setDepartment(lecturer.getDepartment());
                    if (lecturer.getStatus() != null) {
                        existing.setStatus(lecturer.getStatus());
                    }

                    if (existing.getUser() != null) {
                        if (lecturer.getStatus() != null) {
                            existing.getUser().setStatus(lecturer.getStatus());
                        }
                        if (lecturer.getFullName() != null) {
                            existing.getUser().setFullName(lecturer.getFullName());
                        }
                        if (lecturer.getEmail() != null) {
                            existing.getUser().setEmail(lecturer.getEmail());
                        }
                        userRepository.save(existing.getUser());
                    }

                    return ResponseEntity.ok(lecturerRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

package com.example.student_management.controller;

import com.example.student_management.entity.AccountStatus;
import com.example.student_management.entity.Lecturer;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.NotFoundException;
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
        if (lecturer.getLecturerCode() == null || lecturer.getLecturerCode().isBlank()) {
            String generatedCode;
            do {
                int random = (int) (Math.random() * 900000) + 100000;
                generatedCode = "GV" + random;
            } while (lecturerRepository.existsByLecturerCode(generatedCode));
            lecturer.setLecturerCode(generatedCode);
        }
        String email = lecturer.getEmail() == null ? "" : lecturer.getEmail().trim();
        if (email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (userRepository.findByEmail(email).isPresent()
                || userRepository.findByEmail(email.toLowerCase()).isPresent()
                || lecturerRepository.findAll().stream().anyMatch(existing -> existing.getEmail() != null
                        && existing.getEmail().equalsIgnoreCase(email))) {
            throw new IllegalArgumentException("Email already exists");
        }
        lecturer.setEmail(email);
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
                    if (lecturer.getLecturerCode() != null && !lecturer.getLecturerCode().isBlank()) {
                        existing.setLecturerCode(lecturer.getLecturerCode());
                    }
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Lecturer existing = lecturerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lecturer not found"));

        if (existing.getStatus() != AccountStatus.INACTIVE) {
            throw new BadRequestException("Only inactive accounts can be deleted.");
        }

        if (existing.getUser() != null) {
            var user = existing.getUser();
            if (user.getRole() == com.example.student_management.entity.Role.ADMIN) {
                throw new BadRequestException("Admin accounts cannot be deleted.");
            }
            if (user.getStatus() != AccountStatus.INACTIVE) {
                throw new BadRequestException("Only inactive accounts can be deleted.");
            }
            if (user.getLecturer() != null && user.getLecturer().getId() != null && !user.getLecturer().getId().equals(existing.getId())) {
                throw new BadRequestException("Account is linked to multiple profiles and cannot be deleted automatically.");
            }
            if (user.getStudent() != null) {
                throw new BadRequestException("Account is linked to a student profile and cannot be deleted here.");
            }
        }

        lecturerRepository.delete(existing);

        if (existing.getUser() != null) {
            userRepository.delete(existing.getUser());
        }

        return ResponseEntity.noContent().build();
    }
}

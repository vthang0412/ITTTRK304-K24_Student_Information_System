package com.example.student_management.controller;

import com.example.student_management.entity.AccountStatus;
import com.example.student_management.entity.Lecturer;
import com.example.student_management.entity.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.LecturerRepository;
import com.example.student_management.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lecturers")
public class LecturerController {
    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Lecturer>> getAll() {
        return ResponseEntity.ok(lecturerRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('LECTURER')")
    public ResponseEntity<Lecturer> getById(@PathVariable Long id) {
        return lecturerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
    public ResponseEntity<Lecturer> update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Lecturer existing = lecturerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lecturer not found"));

        String fullName = payload.get("fullName") != null ? payload.get("fullName").toString().trim()
                : existing.getFullName();
        String email = payload.get("email") != null ? payload.get("email").toString().trim() : existing.getEmail();
        String department = payload.get("department") != null ? payload.get("department").toString().trim()
                : existing.getDepartment();
        String statusValue = payload.get("status") != null ? payload.get("status").toString()
                : (existing.getStatus() != null ? existing.getStatus().name() : null);
        String password = payload.get("password") != null ? payload.get("password").toString() : null;

        if (fullName == null || fullName.isBlank()) {
            throw new BadRequestException("Full name is required");
        }
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required");
        }
        if (statusValue != null && !statusValue.isBlank()) {
            existing.setStatus(AccountStatus.valueOf(statusValue));
        }

        User user = existing.getUser();
        if (user != null && (userRepository.existsByEmailAndIdNot(email, user.getId())
                || lecturerRepository.existsByEmailAndIdNot(email, existing.getId()))) {
            throw new BadRequestException("Email already exists");
        }
        if (user == null && lecturerRepository.existsByEmailAndIdNot(email, existing.getId())) {
            throw new BadRequestException("Email already exists");
        }

        if (password != null && !password.isBlank()) {
            if (password.length() < 6) {
                throw new BadRequestException("Password must be at least 6 characters");
            }
            if (user != null) {
                user.setPasswordHash(passwordEncoder.encode(password));
                userRepository.save(user);
            }
        }

        existing.setFullName(fullName);
        existing.setEmail(email);
        existing.setDepartment(department);

        if (user != null) {
            user.setFullName(fullName);
            user.setEmail(email);
            user.setStatus(existing.getStatus());
            userRepository.save(user);
        }

        return ResponseEntity.ok(lecturerRepository.save(existing));
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
            if (user.getLecturer() != null && user.getLecturer().getId() != null
                    && !user.getLecturer().getId().equals(existing.getId())) {
                throw new BadRequestException(
                        "Account is linked to multiple profiles and cannot be deleted automatically.");
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

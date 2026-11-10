package com.example.student_management.controller.auth;

import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.auth.StudentRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.auth.StudentService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/students")
public class StudentController {
    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Student>> getAll() {
        return ResponseEntity.ok(studentService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<Student> getById(@PathVariable Long id, Authentication authentication) {
        Student student = studentService.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sinh viên"));

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            if (student.getUser() == null || !student.getUser().getUsername().equals(authentication.getName())) {
                throw new ForbiddenException("Bạn không có quyền xem thông tin của sinh viên khác");
            }
        }
        return ResponseEntity.ok(student);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Student> create(@Valid @RequestBody Student student) {
        return ResponseEntity.ok(studentService.create(student));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Student> update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Student not found"));

        String fullName = payload.get("fullName") != null ? payload.get("fullName").toString().trim() : existing.getFullName();
        String email = payload.get("email") != null ? payload.get("email").toString().trim() : existing.getEmail();
        String department = payload.get("department") != null ? payload.get("department").toString().trim() : existing.getDepartment();
        String statusValue = payload.get("status") != null ? payload.get("status").toString() : (existing.getStatus() != null ? existing.getStatus().name() : null);
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
                || studentRepository.existsByEmailAndIdNot(email, existing.getId()))) {
            throw new BadRequestException("Email already exists");
        }
        if (user == null && studentRepository.existsByEmailAndIdNot(email, existing.getId())) {
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

        return ResponseEntity.ok(studentRepository.save(existing));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        studentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

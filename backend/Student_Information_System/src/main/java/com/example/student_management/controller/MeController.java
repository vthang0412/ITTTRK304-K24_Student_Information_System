package com.example.student_management.controller;

import com.example.student_management.dto.request.ProfileUpdateRequest;
import com.example.student_management.entity.Lecturer;
import com.example.student_management.entity.Student;
import com.example.student_management.entity.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.LecturerRepository;
import com.example.student_management.repository.StudentRepository;
import com.example.student_management.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class MeController {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(buildProfile(currentUser(authentication)));
    }

    @PutMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> updateCurrentUser(
            @Valid @RequestBody ProfileUpdateRequest request,
            Authentication authentication) {
        User user = currentUser(authentication);

        String fullName = request.getFullName().trim();
        String email = request.getEmail().trim();
        if (fullName.isEmpty()) {
            throw new BadRequestException("Full name is required");
        }
        if (userRepository.existsByEmailAndIdNot(email, user.getId())) {
            throw new BadRequestException("Email already exists");
        }

        Student student = user.getStudent();
        Lecturer lecturer = user.getLecturer();
        if (student != null && studentRepository.existsByEmailAndIdNot(email, student.getId())) {
            throw new BadRequestException("Email already exists");
        }
        if (lecturer != null && lecturerRepository.existsByEmailAndIdNot(email, lecturer.getId())) {
            throw new BadRequestException("Email already exists");
        }

        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw new BadRequestException("Current password is required to change password");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                throw new BadRequestException("Current password is incorrect");
            }
            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        }

        user.setEmail(email);
        user.setFullName(fullName);
        userRepository.save(user);

        if (student != null) {
            student.setFullName(fullName);
            student.setEmail(email);
            if (request.getDepartment() != null) {
                student.setDepartment(request.getDepartment().trim());
            }
            studentRepository.save(student);
        } else if (lecturer != null) {
            lecturer.setFullName(fullName);
            lecturer.setEmail(email);
            if (request.getDepartment() != null) {
                lecturer.setDepartment(request.getDepartment().trim());
            }
            lecturerRepository.save(lecturer);
        }

        return ResponseEntity.ok(buildProfile(user));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null) {
            throw new NotFoundException("Not authenticated");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private Map<String, Object> buildProfile(User user) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("email", user.getEmail());
        result.put("role", user.getRole());
        result.put("status", user.getStatus());
        result.put("createdAt", user.getCreatedAt());

        Student student = user.getStudent();
        if (student != null) {
            result.put("studentId", student.getId());
            result.put("studentCode", student.getStudentCode());
            result.put("fullName", student.getFullName());
            result.put("department", student.getDepartment());
            result.put("profileStatus", student.getStatus());
        }

        Lecturer lecturer = user.getLecturer();
        if (lecturer != null) {
            result.put("lecturerId", lecturer.getId());
            result.put("lecturerCode", lecturer.getLecturerCode());
            result.put("fullName", lecturer.getFullName());
            result.put("department", lecturer.getDepartment());
            result.put("profileStatus", lecturer.getStatus());
        }

        if (student == null && lecturer == null) {
            String name = user.getFullName();
            result.put("fullName", name != null && !name.isBlank() ? name : user.getUsername());
        }

        return result;
    }
}

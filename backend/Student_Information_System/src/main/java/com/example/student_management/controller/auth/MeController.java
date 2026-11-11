package com.example.student_management.controller.auth;

import com.example.student_management.dto.auth.ProfileUpdateRequest;
import com.example.student_management.entity.auth.Lecturer;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.auth.LecturerRepository;
import com.example.student_management.repository.auth.StudentRepository;
import com.example.student_management.repository.auth.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    private static final long MAX_AVATAR_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp");

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(buildProfile(currentUser(authentication)));
    }

    @PostMapping("/avatar")
    @Transactional
    public ResponseEntity<Map<String, Object>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {
        User user = currentUser(authentication);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Avatar file is required");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Only JPG, PNG and WEBP image files are allowed");
        }

        if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new BadRequestException("Avatar image must be smaller than 5MB");
        }

        String uploadDir = Paths.get(System.getProperty("user.dir"), "uploads", "avatars").toString();
        Files.createDirectories(Paths.get(uploadDir));

        String originalFileName = file.getOriginalFilename();
        String extension = ".png";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf('.')).toLowerCase();
        }
        if (!Set.of(".jpg", ".jpeg", ".png", ".webp").contains(extension)) {
            throw new BadRequestException("Unsupported file extension");
        }

        String savedFileName = UUID.randomUUID() + extension;
        Path target = Paths.get(uploadDir, savedFileName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        user.setAvatarUrl("/uploads/avatars/" + savedFileName);
        userRepository.save(user);
        return ResponseEntity.ok(buildProfile(user));
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
        result.put("avatarUrl", user.getAvatarUrl());

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

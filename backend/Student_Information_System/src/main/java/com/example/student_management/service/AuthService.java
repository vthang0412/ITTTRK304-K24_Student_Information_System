package com.example.student_management.service;

import com.example.student_management.dto.AuthResponse;
import com.example.student_management.dto.ForgotPasswordRequest;
import com.example.student_management.dto.LoginRequest;
import com.example.student_management.dto.RegisterRequest;
import com.example.student_management.entity.*;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.UnauthorizedException;
import com.example.student_management.repository.LecturerRepository;
import com.example.student_management.repository.StudentRepository;
import com.example.student_management.repository.UserRepository;
import com.example.student_management.security.CustomUserDetailsService;
import com.example.student_management.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public AuthResponse login(LoginRequest request) {
        String loginIdentifier = (request.getEmail() != null && !request.getEmail().isBlank())
                ? request.getEmail().trim()
                : (request.getUsername() == null ? "" : request.getUsername().trim());

        User user = userRepository.findByEmail(loginIdentifier)
                .or(() -> userRepository.findByUsername(loginIdentifier))
                .orElseThrow(() -> new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác"));

        if (user.getStatus() == AccountStatus.INACTIVE) {
            throw new UnauthorizedException("Tài khoản đã bị ngừng hoạt động. Vui lòng liên hệ quản trị viên.");
        }
        if (user.getStudent() != null && user.getStudent().getStatus() == AccountStatus.INACTIVE) {
            throw new UnauthorizedException(
                    "Tài khoản sinh viên đã bị ngừng hoạt động. Vui lòng liên hệ quản trị viên.");
        }
        if (user.getLecturer() != null && user.getLecturer().getStatus() == AccountStatus.INACTIVE) {
            throw new UnauthorizedException(
                    "Tài khoản giảng viên đã bị ngừng hoạt động. Vui lòng liên hệ quản trị viên.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), request.getPassword()));
        } catch (DisabledException e) {
            throw new UnauthorizedException("Tài khoản đã bị ngừng hoạt động. Vui lòng liên hệ quản trị viên.");
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtUtil.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .userId(user.getId())
                .build();
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim();
        String fullName = request.getFullName() == null ? "" : request.getFullName().trim();

        if (username.isBlank()) {
            throw new BadRequestException("Username is required");
        }
        if (email.isBlank()) {
            throw new BadRequestException("Email is required");
        }
        if (fullName.isBlank()) {
            throw new BadRequestException("Full name is required");
        }

        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Username already exists");
        }
        if (hasEmailConflict(email, null)) {
            throw new BadRequestException("Email already exists");
        }

        Role role = Role.STUDENT;
        if ("ADMIN".equalsIgnoreCase(request.getRole())) {
            throw new BadRequestException("Only admin can create STUDENT or LECTURER accounts");
        }
        if ("LECTURER".equalsIgnoreCase(request.getRole())) {
            role = Role.LECTURER;
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        user = userRepository.save(user);

        if (role == Role.STUDENT) {
            String studentCode = generateUniqueStudentCode();
            Student student = Student.builder()
                    .studentCode(studentCode)
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .department("CNTT")
                    .status(AccountStatus.ACTIVE)
                    .user(user)
                    .build();
            studentRepository.save(student);
        } else if (role == Role.LECTURER) {
            String lecturerCode = generateUniqueLecturerCode();
            Lecturer lecturer = Lecturer.builder()
                    .lecturerCode(lecturerCode)
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .department("CNTT")
                    .status(AccountStatus.ACTIVE)
                    .user(user)
                    .build();
            lecturerRepository.save(lecturer);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtUtil.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .userId(user.getId())
                .build();
    }

    private boolean hasEmailConflict(String email, Long ignoreUserId) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank()) {
            return false;
        }

        if (userRepository.findByEmail(email).isPresent()
                && (ignoreUserId == null || !ignoreUserId.equals(userRepository.findByEmail(email).get().getId()))) {
            return true;
        }

        if (userRepository.findByEmail(email.toLowerCase()).isPresent() && (ignoreUserId == null
                || !ignoreUserId.equals(userRepository.findByEmail(email.toLowerCase()).get().getId()))) {
            return true;
        }

        if (studentRepository.findAll().stream()
                .anyMatch(student -> student.getEmail() != null && student.getEmail().equalsIgnoreCase(normalized)
                        && (student.getUser() == null || !student.getUser().getId().equals(ignoreUserId)))) {
            return true;
        }

        if (lecturerRepository.findAll().stream()
                .anyMatch(lecturer -> lecturer.getEmail() != null && lecturer.getEmail().equalsIgnoreCase(normalized)
                        && (lecturer.getUser() == null || !lecturer.getUser().getId().equals(ignoreUserId)))) {
            return true;
        }

        return false;
    }

    private String generateUniqueStudentCode() {
        String code;
        do {
            int random = (int) (Math.random() * 900000) + 100000;
            code = "SV" + random;
        } while (studentRepository.existsByStudentCode(code));
        return code;
    }

    private String generateUniqueLecturerCode() {
        String code;
        do {
            int random = (int) (Math.random() * 900000) + 100000;
            code = "GV" + random;
        } while (lecturerRepository.existsByLecturerCode(code));
        return code;
    }

    public String forgotPassword(ForgotPasswordRequest request) {
        if (!userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BadRequestException("Email not found");
        }
        return "Password reset link has been sent to your email (demo)";
    }
}

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
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác"));

        if (user.getStatus() == AccountStatus.INACTIVE) {
            throw new UnauthorizedException("Tài khoản đã bị ngừng hoạt động. Vui lòng liên hệ quản trị viên.");
        }
        if (user.getStudent() != null && user.getStudent().getStatus() == AccountStatus.INACTIVE) {
            throw new UnauthorizedException("Tài khoản sinh viên đã bị ngừng hoạt động. Vui lòng liên hệ quản trị viên.");
        }
        if (user.getLecturer() != null && user.getLecturer().getStatus() == AccountStatus.INACTIVE) {
            throw new UnauthorizedException("Tài khoản giảng viên đã bị ngừng hoạt động. Vui lòng liên hệ quản trị viên.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (DisabledException e) {
            throw new UnauthorizedException("Tài khoản đã bị ngừng hoạt động. Vui lòng liên hệ quản trị viên.");
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
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
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already exists");
        }

        Role role = Role.STUDENT;
        if ("LECTURER".equalsIgnoreCase(request.getRole())) {
            role = Role.LECTURER;
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        user = userRepository.save(user);

        if (role == Role.STUDENT) {
            Student student = Student.builder()
                    .studentCode("SV" + System.currentTimeMillis())
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .department("CNTT")
                    .status(AccountStatus.ACTIVE)
                    .user(user)
                    .build();
            studentRepository.save(student);
        } else if (role == Role.LECTURER) {
            Lecturer lecturer = Lecturer.builder()
                    .lecturerCode("GV" + System.currentTimeMillis())
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

    public String forgotPassword(ForgotPasswordRequest request) {
        if (!userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BadRequestException("Email not found");
        }
        return "Password reset link has been sent to your email (demo)";
    }
}

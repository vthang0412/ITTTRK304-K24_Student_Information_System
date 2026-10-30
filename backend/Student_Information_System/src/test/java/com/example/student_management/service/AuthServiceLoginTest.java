package com.example.student_management.service;

import com.example.student_management.dto.LoginRequest;
import com.example.student_management.dto.AuthResponse;
import com.example.student_management.entity.AccountStatus;
import com.example.student_management.entity.Role;
import com.example.student_management.entity.Student;
import com.example.student_management.entity.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.service.impl.StudentServiceImpl;
import com.example.student_management.repository.LecturerRepository;
import com.example.student_management.repository.StudentRepository;
import com.example.student_management.repository.UserRepository;
import com.example.student_management.security.CustomUserDetailsService;
import com.example.student_management.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {
    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private LecturerRepository lecturerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @InjectMocks
    private StudentServiceImpl studentService;

    @Test
    void login_shouldAcceptEmailAsIdentifier() {
        User user = User.builder()
                .id(10L)
                .username("student01")
                .email("student@sis.edu.vn")
                .fullName("Student One")
                .passwordHash("$2a$10$g2FQ0f9h8nA0l9aZJ8Egu.IXmK3T7PK8a6S5B9n2hR2mG3TMO6XQO")
                .role(Role.STUDENT)
                .status(AccountStatus.ACTIVE)
                .build();

        LoginRequest request = new LoginRequest();
        request.setEmail("student@sis.edu.vn");
        request.setPassword("123456");

        when(userRepository.findByEmail("student@sis.edu.vn")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", user.getPasswordHash())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("student01"))
                .thenReturn(new org.springframework.security.core.userdetails.User(
                        "student01",
                        user.getPasswordHash(),
                        true,
                        true,
                        true,
                        true,
                        Collections.emptyList()));
        when(jwtUtil.generateToken(any(UserDetails.class))).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertEquals("jwt-token", response.getToken());
        assertEquals("student01", response.getUsername());
        assertEquals("student@sis.edu.vn", response.getEmail());
    }

    @Test
    void delete_shouldRejectActiveStudentAccount() {
        User linkedUser = User.builder().id(7L).role(Role.STUDENT).status(AccountStatus.ACTIVE).build();
        Student student = Student.builder()
                .id(1L)
                .studentCode("SV100001")
                .fullName("Active Student")
                .email("active@student.test")
                .status(AccountStatus.ACTIVE)
                .user(linkedUser)
                .build();

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> studentService.delete(1L));

        assertEquals("Only inactive accounts can be deleted.", exception.getMessage());
        verify(studentRepository, never()).delete(any());
    }

    @Test
    void delete_shouldDeleteInactiveStudentAndLinkedUser() {
        User linkedUser = User.builder().id(7L).role(Role.STUDENT).status(AccountStatus.INACTIVE).build();
        Student student = Student.builder()
                .id(2L)
                .studentCode("SV100002")
                .fullName("Inactive Student")
                .email("inactive@student.test")
                .status(AccountStatus.INACTIVE)
                .user(linkedUser)
                .build();

        linkedUser.setStudent(student);
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));

        studentService.delete(2L);

        verify(studentRepository).delete(student);
        verify(userRepository).delete(linkedUser);
    }
}

package com.example.student_management.service;

import com.example.student_management.dto.auth.StudentAccountCreateRequest;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.repository.auth.StudentRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.auth.impl.StudentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAccountCreationTest {
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private StudentServiceImpl studentService;

    @Test
    void createsUserAndStudentProfileAsOneLinkedAccount() {
        StudentAccountCreateRequest request = new StudentAccountCreateRequest();
        request.setUsername("newstudent");
        request.setEmail("newstudent@example.edu");
        request.setPassword("initial-password");
        request.setFullName("New Student");
        request.setDepartment("Computer Science");
        request.setStatus(AccountStatus.ACTIVE);

        when(userRepository.existsByUsername("newstudent")).thenReturn(false);
        when(userRepository.existsByEmail("newstudent@example.edu")).thenReturn(false);
        when(studentRepository.existsByEmail("newstudent@example.edu")).thenReturn(false);
        when(studentRepository.existsByStudentCode(any())).thenReturn(false);
        when(passwordEncoder.encode("initial-password")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(21L);
            return user;
        });
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            student.setId(37L);
            return student;
        });

        Student created = studentService.createAccount(request);

        assertEquals(37L, created.getId());
        assertEquals("SV", created.getStudentCode().substring(0, 2));
        assertEquals(Role.STUDENT, created.getUser().getRole());
        assertEquals("bcrypt-hash", created.getUser().getPasswordHash());
        assertSame(created, created.getUser().getStudent());
        assertSame(created.getUser(), created.getUser().getStudent().getUser());
        assertEquals(created.getEmail(), created.getUser().getEmail());
        assertEquals(created.getFullName(), created.getUser().getFullName());
        verify(userRepository).save(any(User.class));
        verify(studentRepository).save(created);
    }
}

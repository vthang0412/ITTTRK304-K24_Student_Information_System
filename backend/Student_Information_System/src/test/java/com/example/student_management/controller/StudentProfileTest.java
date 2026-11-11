package com.example.student_management.controller;

import com.example.student_management.controller.auth.MeController;
import com.example.student_management.dto.auth.ProfileUpdateRequest;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.repository.auth.LecturerRepository;
import com.example.student_management.repository.auth.StudentRepository;
import com.example.student_management.repository.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentProfileTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private LecturerRepository lecturerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private MeController meController;

    private User sampleUser;
    private Student sampleStudent;

    @BeforeEach
    void setUp() {
        sampleStudent = Student.builder()
                .id(1L)
                .studentCode("SV001")
                .fullName("Nguyễn Văn Sinh")
                .email("student@sis.edu.vn")
                .department("CNTT")
                .phone("0912345678")
                .className("K24-CNTT1")
                .major("Công nghệ thông tin")
                .academicStatus("STUDYING")
                .status(AccountStatus.ACTIVE)
                .build();

        sampleUser = User.builder()
                .id(1L)
                .username("student")
                .email("student@sis.edu.vn")
                .fullName("Nguyễn Văn Sinh")
                .role(Role.STUDENT)
                .status(AccountStatus.ACTIVE)
                .student(sampleStudent)
                .build();

        sampleStudent.setUser(sampleUser);
    }

    @Test
    void getCurrentUser_shouldReturnAllStudentProfileFields() {
        when(authentication.getName()).thenReturn("student");
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(sampleUser));

        ResponseEntity<Map<String, Object>> response = meController.getCurrentUser(authentication);

        assertNotNull(response.getBody());
        Map<String, Object> body = response.getBody();
        assertEquals("student", body.get("username"));
        assertEquals("student@sis.edu.vn", body.get("email"));
        assertEquals("SV001", body.get("studentCode"));
        assertEquals("Nguyễn Văn Sinh", body.get("fullName"));
        assertEquals("CNTT", body.get("department"));
        assertEquals("0912345678", body.get("phone"));
        assertEquals("K24-CNTT1", body.get("className"));
        assertEquals("Công nghệ thông tin", body.get("major"));
        assertEquals("STUDYING", body.get("academicStatus"));
        assertEquals(AccountStatus.ACTIVE, body.get("profileStatus"));
        assertEquals(Role.STUDENT, body.get("role"));
    }

    @Test
    void updateCurrentUser_shouldUpdateExtendedStudentProfileFields() {
        when(authentication.getName()).thenReturn("student");
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByEmailAndIdNot(anyString(), anyLong())).thenReturn(false);
        when(studentRepository.existsByEmailAndIdNot(anyString(), anyLong())).thenReturn(false);

        ProfileUpdateRequest updateRequest = new ProfileUpdateRequest();
        updateRequest.setFullName("Nguyễn Văn Sinh (Đã sửa)");
        updateRequest.setEmail("student_updated@sis.edu.vn");
        updateRequest.setDepartment("KTPM");
        updateRequest.setPhone("0987654321");
        updateRequest.setClassName("K24-KTPM1");
        updateRequest.setMajor("Kỹ thuật phần mềm");
        updateRequest.setAcademicStatus("STUDYING");

        ResponseEntity<Map<String, Object>> response = meController.updateCurrentUser(updateRequest, authentication);

        assertNotNull(response.getBody());
        Map<String, Object> body = response.getBody();
        assertEquals("Nguyễn Văn Sinh (Đã sửa)", body.get("fullName"));
        assertEquals("student_updated@sis.edu.vn", body.get("email"));
        assertEquals("KTPM", body.get("department"));
        assertEquals("0987654321", body.get("phone"));
        assertEquals("K24-KTPM1", body.get("className"));
        assertEquals("Kỹ thuật phần mềm", body.get("major"));
        assertEquals("STUDYING", body.get("academicStatus"));

        verify(studentRepository).save(sampleStudent);
        verify(userRepository).save(sampleUser);
    }
}


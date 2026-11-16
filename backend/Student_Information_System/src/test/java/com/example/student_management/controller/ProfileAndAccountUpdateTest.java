package com.example.student_management.controller;

import com.example.student_management.controller.auth.LecturerController;
import com.example.student_management.controller.auth.MeController;
import com.example.student_management.controller.auth.StudentController;
import com.example.student_management.dto.auth.ProfileUpdateRequest;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Lecturer;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.repository.auth.LecturerRepository;
import com.example.student_management.repository.auth.StudentRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.auth.StudentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileAndAccountUpdateTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private LecturerRepository lecturerRepository;
    @Mock
    private StudentService studentService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MeController meController;
    @InjectMocks
    private StudentController studentController;
    @InjectMocks
    private LecturerController lecturerController;

    @Test
    void studentCannotChangeDepartmentThroughProfile() {
        Student student = Student.builder().id(2L).studentCode("SV001").fullName("Sinh Vien")
                .email("sv@example.com").department("CNTT").status(AccountStatus.ACTIVE).build();
        User user = User.builder().id(1L).username("sv1").email("sv@example.com").fullName("Sinh Vien")
                .passwordHash("hash").role(Role.STUDENT).status(AccountStatus.ACTIVE).student(student).build();
        when(userRepository.findByUsername("sv1")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("sv@example.com", 1L)).thenReturn(false);
        when(studentRepository.existsByEmailAndIdNot("sv@example.com", 2L)).thenReturn(false);

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setFullName("Sinh Vien");
        request.setEmail("sv@example.com");
        request.setDepartment("KTPM");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("sv1");

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> meController.updateCurrentUser(request, authentication));

        assertTrue(ex.getMessage().contains("Khoa/Bộ môn"));
        verify(studentRepository, never()).save(any());
    }

    @Test
    void studentCanSubmitUnchangedDepartmentThroughProfile() {
        Student student = Student.builder().id(2L).studentCode("SV001").fullName("Sinh Vien")
                .email("sv@example.com").department("CNTT").status(AccountStatus.ACTIVE).build();
        User user = User.builder().id(1L).username("sv1").email("sv@example.com").fullName("Sinh Vien")
                .passwordHash("hash").role(Role.STUDENT).status(AccountStatus.ACTIVE).student(student).build();
        when(userRepository.findByUsername("sv1")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("sv@example.com", 1L)).thenReturn(false);
        when(studentRepository.existsByEmailAndIdNot("sv@example.com", 2L)).thenReturn(false);
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setFullName("Sinh Vien");
        request.setEmail("sv@example.com");
        request.setDepartment("  CNTT  ");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("sv1");

        meController.updateCurrentUser(request, authentication);

        assertEquals("CNTT", student.getDepartment());
        verify(studentRepository).save(student);
    }

    @Test
    void studentUpdateRejectsInvalidStatusWithBadRequest() {
        Student student = Student.builder().id(2L).studentCode("SV001").fullName("Sinh Vien")
                .email("sv@example.com").department("CNTT").status(AccountStatus.ACTIVE).build();
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "KHONG_HOP_LE");

        assertThrows(BadRequestException.class, () -> studentController.update(2L, payload));
    }

    @Test
    void lecturerUpdateRejectsInvalidStatusWithBadRequest() {
        Lecturer lecturer = Lecturer.builder().id(3L).lecturerCode("GV001").fullName("Giang Vien")
                .email("gv@example.com").department("CNTT").status(AccountStatus.ACTIVE).build();
        when(lecturerRepository.findById(3L)).thenReturn(Optional.of(lecturer));

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "KHONG_HOP_LE");

        assertThrows(BadRequestException.class, () -> lecturerController.update(3L, payload));
    }

    @Test
    void lecturerCreateGeneratesCodeAndDefaultsStatus() {
        when(lecturerRepository.findAll()).thenReturn(List.of());
        when(lecturerRepository.existsByLecturerCode(anyString())).thenReturn(false);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(lecturerRepository.save(any(Lecturer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Lecturer input = Lecturer.builder().fullName("Giang Vien Moi").email("new@example.com").build();

        lecturerController.create(input);

        ArgumentCaptor<Lecturer> captor = ArgumentCaptor.forClass(Lecturer.class);
        verify(lecturerRepository).save(captor.capture());
        Lecturer saved = captor.getValue();
        assertTrue(saved.getLecturerCode().startsWith("GV"));
        assertEquals(AccountStatus.ACTIVE, saved.getStatus());
    }

    @Test
    void lecturerCreateRejectsDuplicateEmailWithConflict() {
        when(lecturerRepository.existsByLecturerCode(anyString())).thenReturn(false);
        when(userRepository.findByEmail("dup@example.com"))
                .thenReturn(Optional.of(User.builder().id(9L).email("dup@example.com").build()));

        Lecturer input = Lecturer.builder().fullName("Giang Vien").email("dup@example.com").build();

        assertThrows(ConflictException.class, () -> lecturerController.create(input));
        verify(lecturerRepository, never()).save(any());
    }
}

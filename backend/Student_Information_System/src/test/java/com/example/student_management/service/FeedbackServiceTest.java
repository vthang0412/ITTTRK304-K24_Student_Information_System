package com.example.student_management.service;

import com.example.student_management.dto.feedback.FeedbackAssignRequest;
import com.example.student_management.dto.feedback.FeedbackDto;
import com.example.student_management.entity.auth.*;
import com.example.student_management.entity.feedback.Feedback;
import com.example.student_management.entity.feedback.FeedbackStatus;
import com.example.student_management.entity.feedback.FeedbackType;
import com.example.student_management.repository.auth.LecturerRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.repository.feedback.FeedbackMessageRepository;
import com.example.student_management.repository.feedback.FeedbackRepository;
import com.example.student_management.service.feedback.impl.FeedbackServiceImpl;
import com.example.student_management.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private FeedbackMessageRepository feedbackMessageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LecturerRepository lecturerRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private FeedbackServiceImpl feedbackService;

    private User adminUser;
    private User studentUser;
    private User lecturerUser;
    private Student student;
    private Lecturer lecturer;
    private Feedback feedback;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().id(1L).username("admin").fullName("Quản Trị Viên").role(Role.ADMIN).build();
        studentUser = User.builder().id(8L).username("student").fullName("Nguyễn Văn Sinh").role(Role.STUDENT).build();
        student = Student.builder().id(1L).studentCode("SV001").fullName("Nguyễn Văn Sinh").email("student@sis.edu.vn").user(studentUser).build();
        studentUser.setStudent(student);

        lecturerUser = User.builder().id(3L).username("lecturer2").fullName("Lê Thị Hương").role(Role.LECTURER).build();
        lecturer = Lecturer.builder().id(2L).lecturerCode("GV002").fullName("Lê Thị Hương").email("lecturer2@sis.edu.vn").user(lecturerUser).build();
        lecturerUser.setLecturer(lecturer);

        feedback = Feedback.builder()
                .id(2L)
                .title("Thắc mắc về điểm")
                .feedbackType(FeedbackType.GRADE)
                .content("Nội dung phản ánh")
                .status(FeedbackStatus.PENDING)
                .student(student)
                .createdAt(LocalDateTime.now())
                .messages(new ArrayList<>())
                .build();
    }

    @Test
    void testAssignToLecturer() {
        when(feedbackRepository.findById(2L)).thenReturn(Optional.of(feedback));
        when(lecturerRepository.findById(2L)).thenReturn(Optional.of(lecturer));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(i -> i.getArgument(0));

        FeedbackAssignRequest request = FeedbackAssignRequest.builder()
                .lecturerId(2L)
                .adminNote("Nhờ thầy Sơn kiểm tra")
                .build();

        FeedbackDto result = feedbackService.assignToLecturer("admin", 2L, request);

        assertNotNull(result);
        assertEquals(FeedbackStatus.IN_PROGRESS, result.getStatus());
        assertEquals(2L, result.getAssignedLecturerId());
    }
}


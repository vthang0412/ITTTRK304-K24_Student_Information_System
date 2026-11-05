package com.example.student_management.dto.feedback;

import com.example.student_management.entity.feedback.FeedbackStatus;
import com.example.student_management.entity.feedback.FeedbackType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackDto {
    private Long id;
    private String title;
    private FeedbackType feedbackType;
    private String content;
    private FeedbackStatus status;

    // Thông tin sinh viên gửi phản ánh
    private Long studentId;
    private String studentCode;
    private String studentName;
    private String studentEmail;

    // Giảng viên được phân công
    private Long assignedLecturerId;
    private String assignedLecturerCode;
    private String assignedLecturerName;
    private String assignedLecturerEmail;

    // Lớp học phần liên quan (nếu có)
    private Long courseSectionId;
    private String sectionCode;
    private String courseName;

    private String adminNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Lịch sử trao đổi / tin nhắn
    private List<FeedbackMessageDto> messages;
}


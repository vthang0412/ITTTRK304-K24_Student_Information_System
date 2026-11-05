package com.example.student_management.service.feedback;

import com.example.student_management.dto.feedback.*;
import com.example.student_management.entity.feedback.FeedbackStatus;
import com.example.student_management.entity.feedback.FeedbackType;

import java.util.List;

public interface FeedbackService {
    FeedbackDto createFeedback(String username, FeedbackCreateRequest request);

    List<FeedbackDto> getMyFeedbacks(String username);

    List<FeedbackDto> getAssignedFeedbacks(String username);

    List<FeedbackDto> getAllFeedbacks(FeedbackStatus status, FeedbackType type);

    FeedbackDto getFeedbackById(String username, Long id);

    FeedbackDto assignToLecturer(String username, Long feedbackId, FeedbackAssignRequest request);

    FeedbackDto updateStatus(String username, Long feedbackId, FeedbackStatusUpdateRequest request);

    FeedbackMessageDto addMessage(String username, Long feedbackId, FeedbackMessageRequest request);
}


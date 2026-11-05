package com.example.student_management.repository.feedback;

import com.example.student_management.entity.feedback.FeedbackMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackMessageRepository extends JpaRepository<FeedbackMessage, Long> {
    List<FeedbackMessage> findByFeedbackIdOrderByCreatedAtAsc(Long feedbackId);
}


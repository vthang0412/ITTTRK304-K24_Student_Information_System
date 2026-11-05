package com.example.student_management.repository.feedback;

import com.example.student_management.entity.feedback.Feedback;
import com.example.student_management.entity.feedback.FeedbackStatus;
import com.example.student_management.entity.feedback.FeedbackType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<Feedback> findByAssignedLecturerIdOrderByCreatedAtDesc(Long lecturerId);

    List<Feedback> findAllByOrderByCreatedAtDesc();

    @Query("SELECT f FROM Feedback f WHERE " +
           "(:status IS NULL OR f.status = :status) AND " +
           "(:type IS NULL OR f.feedbackType = :type) " +
           "ORDER BY f.createdAt DESC")
    List<Feedback> findWithFilters(@Param("status") FeedbackStatus status, @Param("type") FeedbackType type);
}


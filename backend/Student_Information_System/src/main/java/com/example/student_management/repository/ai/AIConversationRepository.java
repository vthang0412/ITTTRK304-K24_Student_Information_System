package com.example.student_management.repository.ai;

import com.example.student_management.entity.ai.AIConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIConversationRepository extends JpaRepository<AIConversation, Long> {
    List<AIConversation> findByStudentIdOrderByUpdatedAtDesc(Long studentId);
    Optional<AIConversation> findByIdAndStudentId(Long id, Long studentId);
    void deleteByIdAndStudentId(Long id, Long studentId);
}


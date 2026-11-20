package com.example.student_management.repository.ai;

import com.example.student_management.entity.ai.AIMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIMessageRepository extends JpaRepository<AIMessage, Long> {
    List<AIMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    Optional<AIMessage> findByIdAndConversationId(Long id, Long conversationId);
}


package com.example.student_management.repository.ai;

import com.example.student_management.entity.ai.AIMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AIMessageRepository extends JpaRepository<AIMessage, Long> {
    List<AIMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}


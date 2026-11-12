package com.example.student_management.repository.auth;

import com.example.student_management.entity.auth.PasswordResetToken;
import com.example.student_management.entity.auth.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);

    List<PasswordResetToken> findByUser(User user);

    List<PasswordResetToken> findByUserIdAndUsedFalse(Long userId);

    Optional<PasswordResetToken> findFirstByTokenAndUsedFalseOrderByCreatedAtDesc(String token);

    Optional<PasswordResetToken> findFirstByUser_EmailIgnoreCaseAndTokenAndUsedFalseOrderByCreatedAtDesc(String email, String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findFirstByUser_EmailIgnoreCaseOrderByCreatedAtDesc(String email);

    Optional<PasswordResetToken> findFirstByUser_EmailIgnoreCaseAndTokenOrderByCreatedAtDesc(String email, String token);

    Optional<PasswordResetToken> findFirstByUser_EmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc(String email);

    Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);
}

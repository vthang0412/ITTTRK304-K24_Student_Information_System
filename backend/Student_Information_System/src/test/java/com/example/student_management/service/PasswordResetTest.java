package com.example.student_management.service;

import com.example.student_management.dto.auth.ForgotPasswordRequest;
import com.example.student_management.dto.auth.ForgotPasswordResponse;
import com.example.student_management.dto.auth.ResetPasswordRequest;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.PasswordResetToken;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.repository.auth.PasswordResetTokenRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.auth.AuthService;
import com.example.student_management.service.auth.PasswordResetService;
import com.example.student_management.service.email.EmailService;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "otpCooldownSeconds", 60);
        ReflectionTestUtils.setField(authService, "otpMaxRequestsPerHour", 5);
        ReflectionTestUtils.setField(authService, "passwordResetService", passwordResetService);
        sampleUser = User.builder()
                .id(1L)
                .username("student01")
                .email("student@sis.edu.vn")
                .fullName("Nguyễn Văn Sinh")
                .passwordHash("old-hash")
                .role(Role.STUDENT)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void forgotPassword_shouldCreateHashedTokenAndSendEmailWhenUserExists() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("student@sis.edu.vn")
                .build();

        when(userRepository.findByEmail("student@sis.edu.vn")).thenReturn(Optional.of(sampleUser));
        when(passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());
        when(passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class))).thenReturn(0L);
        when(passwordResetTokenRepository.findByUserIdAndUsedFalse(1L)).thenReturn(Collections.emptyList());
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ForgotPasswordResponse response = authService.forgotPassword(request);

        // OTP không được trả về trong response (C-01): chỉ được gửi qua email.
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains("nếu tài khoản tồn tại trong hệ thống"));

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtpEmail(eq("student@sis.edu.vn"), anyString(), otpCaptor.capture());
        String sentOtp = otpCaptor.getValue();
        assertEquals(6, sentOtp.length());
        verify(passwordResetTokenRepository).save(argThat(token ->
                token.getToken().equals(PasswordResetToken.hashOtp(sentOtp)) &&
                !token.isUsed() &&
                token.getAttemptCount() == 0
        ));
    }

    @Test
    void forgotPassword_shouldInvalidatePreviousTokenAndStartNewAttemptCount() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("student@sis.edu.vn")
                .build();
        PasswordResetToken oldToken = PasswordResetToken.builder()
                .id(98L)
                .user(sampleUser)
                .createdAt(LocalDateTime.now().minusSeconds(120))
                .used(false)
                .attemptCount(3)
                .build();

        when(userRepository.findByEmail("student@sis.edu.vn")).thenReturn(Optional.of(sampleUser));
        when(passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(oldToken));
        when(passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn(1L);
        when(passwordResetTokenRepository.findByUserIdAndUsedFalse(1L)).thenReturn(Collections.singletonList(oldToken));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.forgotPassword(request);

        assertTrue(oldToken.isUsed());
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        assertFalse(tokenCaptor.getValue().isUsed());
        assertEquals(0, tokenCaptor.getValue().getAttemptCount());
        verify(passwordResetTokenRepository).saveAll(Collections.singletonList(oldToken));
    }

    @Test
    void forgotPassword_shouldEnforceCooldownRateLimit() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("student@sis.edu.vn")
                .build();

        PasswordResetToken recentToken = PasswordResetToken.builder()
                .id(99L)
                .createdAt(LocalDateTime.now().minusSeconds(20))
                .build();

        when(userRepository.findByEmail("student@sis.edu.vn")).thenReturn(Optional.of(sampleUser));
        when(passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(recentToken));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.forgotPassword(request));
        assertTrue(ex.getMessage().contains("Cooldown") || ex.getMessage().contains("Vui lòng đợi"));
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(any(), any(), any());
    }

    @Test
    void forgotPassword_shouldEnforceHourlyLimit() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("student@sis.edu.vn")
                .build();

        when(userRepository.findByEmail("student@sis.edu.vn")).thenReturn(Optional.of(sampleUser));
        when(passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());
        when(passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class))).thenReturn(5L);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.forgotPassword(request));
        assertTrue(ex.getMessage().contains("tối đa 5 lần"));
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(any(), any(), any());
    }

    @Test
    void forgotPassword_shouldNotExposeEmailExistenceWhenUserNotFound() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("nonexistent@sis.edu.vn")
                .build();

        when(userRepository.findByEmail("nonexistent@sis.edu.vn")).thenReturn(Optional.empty());

        ForgotPasswordResponse response = authService.forgotPassword(request);

        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains("nếu tài khoản tồn tại trong hệ thống"));
        verify(emailService, never()).sendOtpEmail(any(), any(), any());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void resetPassword_shouldVerifyHashedOtpAndUpdatePassword() {
        String rawOtp = "482915";
        String hashedOtp = PasswordResetToken.hashOtp(rawOtp);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(10L)
                .token(hashedOtp)
                .user(sampleUser)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .attemptCount(0)
                .build();

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("student@sis.edu.vn")
                .otp(rawOtp)
                .newPassword("NewPass@123")
                .build();

        when(passwordResetTokenRepository.findFirstByUser_EmailIgnoreCaseOrderByCreatedAtDesc("student@sis.edu.vn"))
                .thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewPass@123")).thenReturn("new-encoded-hash");

        String result = authService.resetPassword(request);

        assertTrue(result.contains("thành công"));
        assertEquals("new-encoded-hash", sampleUser.getPasswordHash());
        assertTrue(resetToken.isUsed());
        verify(userRepository).save(sampleUser);
        verify(passwordResetTokenRepository).save(resetToken);
        verify(emailService).sendPasswordResetSuccessEmail("student@sis.edu.vn", sampleUser.getFullName());
    }

    @Test
    void resetPassword_shouldRejectWrongOtpAndIncrementAttemptCount() {
        String correctOtp = "123456";
        String wrongOtp = "654321";
        String hashedCorrectOtp = PasswordResetToken.hashOtp(correctOtp);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(10L)
                .token(hashedCorrectOtp)
                .user(sampleUser)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .attemptCount(1)
                .build();

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("student@sis.edu.vn")
                .otp(wrongOtp)
                .newPassword("NewPass@123")
                .build();

        when(passwordResetTokenRepository.findFirstByUser_EmailIgnoreCaseOrderByCreatedAtDesc("student@sis.edu.vn"))
                .thenReturn(Optional.of(resetToken));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.resetPassword(request));
        assertTrue(ex.getMessage().contains("không chính xác"));
        assertEquals(2, resetToken.getAttemptCount());
        assertFalse(resetToken.isUsed());
        verify(passwordResetTokenRepository).save(resetToken);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_shouldInvalidateOtpWhenMaxAttemptsExceeded() {
        String correctOtp = "123456";
        String wrongOtp = "999999";
        String hashedCorrectOtp = PasswordResetToken.hashOtp(correctOtp);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(10L)
                .token(hashedCorrectOtp)
                .user(sampleUser)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .attemptCount(4) // lần thử thứ 5 sẽ bị hủy
                .build();

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("student@sis.edu.vn")
                .otp(wrongOtp)
                .newPassword("NewPass@123")
                .build();

        when(passwordResetTokenRepository.findFirstByUser_EmailIgnoreCaseOrderByCreatedAtDesc("student@sis.edu.vn"))
                .thenReturn(Optional.of(resetToken));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.resetPassword(request));
        assertTrue(ex.getMessage().contains("hết số lần thử"));
        assertTrue(resetToken.isUsed());
        verify(passwordResetTokenRepository).save(resetToken);
    }

    @Test
    void resetPassword_shouldPersistExactlyFiveWrongAttempts() {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(10L)
                .token(PasswordResetToken.hashOtp("123456"))
                .user(sampleUser)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .attemptCount(0)
                .build();

        when(passwordResetTokenRepository.findFirstByUser_EmailIgnoreCaseOrderByCreatedAtDesc("student@sis.edu.vn"))
                .thenReturn(Optional.of(resetToken));
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("student@sis.edu.vn")
                .otp("999999")
                .newPassword("NewPass@123")
                .build();

        for (int attempt = 1; attempt <= 5; attempt++) {
            BadRequestException ex = assertThrows(
                    BadRequestException.class, () -> authService.resetPassword(request));
            assertEquals(attempt, resetToken.getAttemptCount());
            assertEquals(attempt == 5, resetToken.isUsed());
            if (attempt < 5) {
                assertTrue(ex.getMessage().contains("còn " + (5 - attempt) + " lần thử"));
            } else {
                assertTrue(ex.getMessage().contains("hết số lần thử"));
            }
        }

        verify(passwordResetTokenRepository, times(5)).save(resetToken);
        verify(userRepository, never()).save(any());

        BadRequestException lockedAttempt = assertThrows(
                BadRequestException.class, () -> authService.resetPassword(request));
        assertTrue(lockedAttempt.getMessage().contains("hết số lần thử"));
        assertEquals(5, resetToken.getAttemptCount());
        verify(passwordResetTokenRepository, times(5)).save(resetToken);
    }

    @Test
    void resetPassword_shouldNotChargeNewOtpForAPreviouslyIssuedCode() {
        PasswordResetToken currentToken = PasswordResetToken.builder()
                .id(12L)
                .token(PasswordResetToken.hashOtp("222222"))
                .user(sampleUser)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .attemptCount(0)
                .build();
        PasswordResetToken oldToken = PasswordResetToken.builder()
                .id(11L)
                .token(PasswordResetToken.hashOtp("111111"))
                .user(sampleUser)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .used(true)
                .attemptCount(0)
                .build();

        when(passwordResetTokenRepository.findFirstByUser_EmailIgnoreCaseOrderByCreatedAtDesc("student@sis.edu.vn"))
                .thenReturn(Optional.of(currentToken));
        when(passwordResetTokenRepository.findFirstByUser_EmailIgnoreCaseAndTokenOrderByCreatedAtDesc(
                "student@sis.edu.vn", PasswordResetToken.hashOtp("111111")))
                .thenReturn(Optional.of(oldToken));
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("student@sis.edu.vn")
                .otp("111111")
                .newPassword("NewPass@123")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.resetPassword(request));

        assertTrue(ex.getMessage().contains("mã OTP mới nhất"));
        assertEquals(0, currentToken.getAttemptCount());
        verify(passwordResetTokenRepository, never()).save(currentToken);
    }

    @Test
    void resetPassword_shouldRejectExpiredOtp() {
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .id(10L)
                .token(PasswordResetToken.hashOtp("123456"))
                .user(sampleUser)
                .expiryDate(LocalDateTime.now().minusMinutes(1))
                .used(false)
                .build();

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("student@sis.edu.vn")
                .otp("123456")
                .newPassword("NewPass@123")
                .build();

        when(passwordResetTokenRepository.findFirstByUser_EmailIgnoreCaseOrderByCreatedAtDesc("student@sis.edu.vn"))
                .thenReturn(Optional.of(expiredToken));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.resetPassword(request));
        assertTrue(ex.getMessage().contains("hết hạn"));
        assertTrue(expiredToken.isUsed());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPasswordLookup_shouldUsePessimisticWriteLock() throws Exception {
        Lock lock = PasswordResetTokenRepository.class
                .getMethod("findFirstByUser_EmailIgnoreCaseOrderByCreatedAtDesc", String.class)
                .getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void resetPasswordTransaction_shouldCommitOutcomeBeforeAuthServiceThrows() throws Exception {
        assertNotNull(PasswordResetService.class.getMethod(
                "resetPassword", String.class, String.class, String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }
}

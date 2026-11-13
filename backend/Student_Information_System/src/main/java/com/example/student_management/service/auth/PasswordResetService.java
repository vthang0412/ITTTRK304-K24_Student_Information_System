package com.example.student_management.service.auth;

import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.PasswordResetToken;
import com.example.student_management.entity.auth.User;
import com.example.student_management.repository.auth.PasswordResetTokenRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.email.EmailService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PasswordResetService {
    private static final int MAX_ATTEMPTS = 5;

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public PasswordResetService(
            PasswordResetTokenRepository passwordResetTokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public Outcome resetPassword(String email, String otp, String newPassword) {
        String inputHash = PasswordResetToken.hashOtp(otp);
        Optional<PasswordResetToken> latestToken =
                passwordResetTokenRepository.findFirstByUser_EmailIgnoreCaseOrderByCreatedAtDesc(email);

        if (latestToken.isEmpty()) {
            return Outcome.failure("Mã OTP không hợp lệ hoặc đã hết hạn. Vui lòng gửi lại yêu cầu.");
        }

        PasswordResetToken resetToken = latestToken.get();
        if (resetToken.isUsed()) {
            if (resetToken.getAttemptCount() >= MAX_ATTEMPTS) {
                return Outcome.failure("Bạn đã hết số lần thử. Vui lòng yêu cầu OTP mới.");
            }
            return Outcome.failure("Mã OTP đã được sử dụng hoặc không còn hợp lệ. Vui lòng yêu cầu mã mới.");
        }

        if (resetToken.getAttemptCount() >= MAX_ATTEMPTS) {
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
            return Outcome.failure("Bạn đã hết số lần thử. Vui lòng yêu cầu OTP mới.");
        }

        if (resetToken.isExpired()) {
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
            return Outcome.failure("Mã OTP đã hết hạn (chỉ có hiệu lực trong 5 phút). Vui lòng gửi lại yêu cầu.");
        }

        if (!matches(resetToken.getToken(), inputHash)) {
            Optional<PasswordResetToken> previouslyIssuedToken =
                    passwordResetTokenRepository.findFirstByUser_EmailIgnoreCaseAndTokenOrderByCreatedAtDesc(
                            email, inputHash);
            if (previouslyIssuedToken.isPresent()
                    && !java.util.Objects.equals(previouslyIssuedToken.get().getId(), resetToken.getId())) {
                return Outcome.failure("Mã OTP không chính xác. Vui lòng sử dụng mã OTP mới nhất.");
            }

            int attempts = resetToken.getAttemptCount() + 1;
            resetToken.setAttemptCount(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                resetToken.setUsed(true);
                passwordResetTokenRepository.save(resetToken);
                return Outcome.failure("Bạn đã hết số lần thử. Vui lòng yêu cầu OTP mới.");
            }

            passwordResetTokenRepository.save(resetToken);
            return Outcome.failure("Mã OTP không chính xác. Bạn còn " + (MAX_ATTEMPTS - attempts) + " lần thử.");
        }

        User user = resetToken.getUser();
        if (user == null || user.getStatus() == AccountStatus.INACTIVE) {
            return Outcome.failure("Tài khoản không hợp lệ hoặc đã bị khóa");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        emailService.sendPasswordResetSuccessEmail(user.getEmail(), user.getFullName());

        return Outcome.success("Mật khẩu đã được đặt lại thành công. Bạn có thể đăng nhập bằng mật khẩu mới.");
    }

    private boolean matches(String storedHash, String inputHash) {
        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.US_ASCII),
                inputHash.getBytes(StandardCharsets.US_ASCII));
    }

    public record Outcome(boolean successful, String message) {
        public static Outcome success(String message) {
            return new Outcome(true, message);
        }

        public static Outcome failure(String message) {
            return new Outcome(false, message);
        }
    }
}

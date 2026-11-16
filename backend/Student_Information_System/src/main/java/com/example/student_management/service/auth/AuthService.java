package com.example.student_management.service.auth;

import com.example.student_management.dto.auth.AuthResponse;
import com.example.student_management.dto.auth.ForgotPasswordRequest;
import com.example.student_management.dto.auth.ForgotPasswordResponse;
import com.example.student_management.dto.auth.ResetPasswordRequest;
import com.example.student_management.repository.auth.PasswordResetTokenRepository;
import com.example.student_management.service.email.EmailService;
import org.springframework.beans.factory.annotation.Value;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

import com.example.student_management.dto.auth.LoginRequest;
import com.example.student_management.dto.auth.RegisterRequest;
import com.example.student_management.entity.auth.*;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.UnauthorizedException;
import com.example.student_management.repository.auth.LecturerRepository;
import com.example.student_management.repository.auth.StudentRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.security.CustomUserDetailsService;
import com.example.student_management.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Value("${app.security.otp.cooldown-seconds:60}")
    private int otpCooldownSeconds;

    @Value("${app.security.otp.max-requests-per-hour:5}")
    private int otpMaxRequestsPerHour;

    public AuthResponse login(LoginRequest request) {
        if (request.getUsername() != null && request.getUsername().matches(".*\\s+.*")) {
            throw new UnauthorizedException("Tên đăng nhập không được chứa khoảng trắng");
        }
        if (request.getEmail() != null && request.getEmail().matches(".*\\s+.*")) {
            throw new UnauthorizedException("Email không được chứa khoảng trắng");
        }
        if (request.getPassword() != null && (request.getPassword().startsWith(" ") || request.getPassword().endsWith(" "))) {
            throw new UnauthorizedException("Mật khẩu không được chứa khoảng trắng ở đầu hoặc cuối");
        }

        String loginIdentifier = (request.getEmail() != null && !request.getEmail().isBlank())
                ? request.getEmail()
                : (request.getUsername() == null ? "" : request.getUsername());

        User user = userRepository.findByEmail(loginIdentifier)
                .or(() -> userRepository.findByUsername(loginIdentifier))
                .orElseThrow(() -> new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác"));

        if (user.getStatus() == AccountStatus.INACTIVE) {
            throw new UnauthorizedException("Tài khoản đã bị ngừng hoạt động. Vui lòng liên hệ quản trị viên.");
        }
        if (user.getStudent() != null && user.getStudent().getStatus() == AccountStatus.INACTIVE) {
            throw new UnauthorizedException("Tài khoản sinh viên đã bị ngừng hoạt động. Vui lòng liên hệ quản trị viên.");
        }
        if (user.getLecturer() != null && user.getLecturer().getStatus() == AccountStatus.INACTIVE) {
            throw new UnauthorizedException("Tài khoản giảng viên đã bị ngừng hoạt động. Vui lòng liên hệ quản trị viên.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), request.getPassword())
            );
        } catch (DisabledException e) {
            throw new UnauthorizedException("Tài khoản đã bị ngừng hoạt động. Vui lòng liên hệ quản trị viên.");
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtUtil.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .userId(user.getId())
                .build();
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getUsername() != null && request.getUsername().matches(".*\\s+.*")) {
            throw new BadRequestException("Tên đăng nhập không được chứa khoảng trắng");
        }
        if (request.getEmail() != null && request.getEmail().matches(".*\\s+.*")) {
            throw new BadRequestException("Email không được chứa khoảng trắng");
        }
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim();
        String fullName = request.getFullName() == null ? "" : request.getFullName().trim();

        if (username.isBlank()) {
            throw new BadRequestException("Username is required");
        }
        if (email.isBlank()) {
            throw new BadRequestException("Email is required");
        }
        if (fullName.isBlank()) {
            throw new BadRequestException("Full name is required");
        }

        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Username already exists");
        }
        if (hasEmailConflict(email, null)) {
            throw new BadRequestException("Email already exists");
        }

        Role role = Role.STUDENT;
        if ("ADMIN".equalsIgnoreCase(request.getRole())) {
            throw new BadRequestException("Only admin can create STUDENT or LECTURER accounts");
        }
        if ("LECTURER".equalsIgnoreCase(request.getRole())) {
            role = Role.LECTURER;
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        user = userRepository.save(user);

        if (role == Role.STUDENT) {
            String studentCode = generateUniqueStudentCode();
            Student student = Student.builder()
                    .studentCode(studentCode)
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .department("CNTT")
                    .status(AccountStatus.ACTIVE)
                    .user(user)
                    .build();
            user.setStudent(student);
            studentRepository.save(student);
        } else if (role == Role.LECTURER) {
            String lecturerCode = generateUniqueLecturerCode();
            Lecturer lecturer = Lecturer.builder()
                    .lecturerCode(lecturerCode)
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .department("CNTT")
                    .status(AccountStatus.ACTIVE)
                    .user(user)
                    .build();
            user.setLecturer(lecturer);
            lecturerRepository.save(lecturer);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtUtil.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .userId(user.getId())
                .build();
    }

    private boolean hasEmailConflict(String email, Long ignoreUserId) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank()) {
            return false;
        }

        if (userRepository.findByEmail(email).isPresent()
                && (ignoreUserId == null || !ignoreUserId.equals(userRepository.findByEmail(email).get().getId()))) {
            return true;
        }

        if (userRepository.findByEmail(email.toLowerCase()).isPresent() && (ignoreUserId == null
                || !ignoreUserId.equals(userRepository.findByEmail(email.toLowerCase()).get().getId()))) {
            return true;
        }

        if (studentRepository.findAll().stream()
                .anyMatch(student -> student.getEmail() != null && student.getEmail().equalsIgnoreCase(normalized)
                        && (student.getUser() == null || !student.getUser().getId().equals(ignoreUserId)))) {
            return true;
        }

        if (lecturerRepository.findAll().stream()
                .anyMatch(lecturer -> lecturer.getEmail() != null && lecturer.getEmail().equalsIgnoreCase(normalized)
                        && (lecturer.getUser() == null || !lecturer.getUser().getId().equals(ignoreUserId)))) {
            return true;
        }

        return false;
    }

    private String generateUniqueStudentCode() {
        String code;
        do {
            int random = (int) (Math.random() * 900000) + 100000;
            code = "SV" + random;
        } while (studentRepository.existsByStudentCode(code));
        return code;
    }

    private String generateUniqueLecturerCode() {
        String code;
        do {
            int random = (int) (Math.random() * 900000) + 100000;
            code = "GV" + random;
        } while (lecturerRepository.existsByLecturerCode(code));
        return code;
    }

        @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        if (email.isBlank()) {
            throw new BadRequestException("Email không được để trống");
        }
        if (email.matches(".*\\s+.*")) {
            throw new BadRequestException("Email không được chứa khoảng trắng");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            // Yêu cầu bảo mật: Không tiết lộ email có tồn tại trong hệ thống hay không để chống dò quét tài khoản
            return ForgotPasswordResponse.builder()
                    .message("Mã OTP đã được gửi đến email nếu tài khoản tồn tại trong hệ thống.")
                    .build();
        }

        User user = userOpt.get();
        if (user.getStatus() == AccountStatus.INACTIVE) {
            return ForgotPasswordResponse.builder()
                    .message("Mã OTP đã được gửi đến email nếu tài khoản tồn tại trong hệ thống.")
                    .build();
        }

        // 1. Chống Spam / Rate limiting: Giới hạn Cooldown giữa các lần gửi
        if (otpCooldownSeconds > 0) {
            Optional<PasswordResetToken> lastTokenOpt = passwordResetTokenRepository
                    .findFirstByUserIdOrderByCreatedAtDesc(user.getId());
            if (lastTokenOpt.isPresent()) {
                LocalDateTime createdAt = lastTokenOpt.get().getCreatedAt();
                if (createdAt != null && createdAt.isAfter(LocalDateTime.now().minusSeconds(otpCooldownSeconds))) {
                    long remainingSeconds = otpCooldownSeconds - java.time.Duration.between(createdAt, LocalDateTime.now()).toSeconds();
                    if (remainingSeconds < 1) remainingSeconds = 1;
                    throw new BadRequestException("Vui lòng đợi " + remainingSeconds + " giây trước khi gửi lại yêu cầu mã OTP mới (Cooldown).");
                }
            }
        }

        // 2. Chống Spam: Giới hạn tối đa số yêu cầu trong 1 giờ
        if (otpMaxRequestsPerHour > 0) {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            long requestsLastHour = passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(user.getId(), oneHourAgo);
            if (requestsLastHour >= otpMaxRequestsPerHour) {
                throw new BadRequestException("Bạn đã vượt quá số lần yêu cầu mã OTP (tối đa " + otpMaxRequestsPerHour + " lần / giờ). Vui lòng thử lại sau 1 giờ.");
            }
        }

        // Hủy các token chưa sử dụng trước đó của người dùng này
        List<PasswordResetToken> oldTokens = passwordResetTokenRepository.findByUserIdAndUsedFalse(user.getId());
        for (PasswordResetToken oldToken : oldTokens) {
            oldToken.setUsed(true);
        }
        passwordResetTokenRepository.saveAll(oldTokens);

        // Sinh mã OTP 6 chữ số ngẫu nhiên: từ 100000 đến 999999
        String otp = String.format("%06d", 100000 + new java.security.SecureRandom().nextInt(900000));

        // Băm mã OTP bằng SHA-256 trước khi lưu vào DB (bảo mật tuyệt đối, không lưu plaintext)
        String hashedOtp = PasswordResetToken.hashOtp(otp);

        // Lưu trữ vào DB với TTL 5 phút, attemptCount = 0
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(hashedOtp)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .attemptCount(0)
                .build();
        passwordResetTokenRepository.save(resetToken);

        // Gửi email thực sự qua EmailService.
        // Tuyệt đối không trả OTP/reset token/email trong response để tránh lộ bí mật
        // và tránh dò quét sự tồn tại của tài khoản.
        emailService.sendOtpEmail(user.getEmail(), user.getFullName(), otp);

        return ForgotPasswordResponse.builder()
                .message("Mã OTP đã được gửi đến email nếu tài khoản tồn tại trong hệ thống. Vui lòng kiểm tra hộp thư (thời hạn 5 phút).")
                .build();
    }

    public String resetPassword(ResetPasswordRequest request) {
        String rawOtp = request.getEffectiveOtp();
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String newPassword = request.getNewPassword();

        if (rawOtp == null || rawOtp.isBlank()) {
            throw new BadRequestException("Mã OTP không được để trống");
        }
        if (email.isBlank()) {
            throw new BadRequestException("Vui lòng nhập email đã nhận mã OTP");
        }
        String cleanOtp = rawOtp.trim();
        if (!cleanOtp.matches("^\\d{6}$")) {
            throw new BadRequestException("Mã OTP phải bao gồm đúng 6 chữ số");
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("Mật khẩu mới không được để trống");
        }
        if (newPassword.length() < 6) {
            throw new BadRequestException("Mật khẩu mới phải có tối thiểu 6 ký tự");
        }
        if (newPassword.startsWith(" ") || newPassword.endsWith(" ")) {
            throw new BadRequestException("Mật khẩu mới không được chứa khoảng trắng ở đầu hoặc cuối");
        }

        PasswordResetService.Outcome outcome =
                passwordResetService.resetPassword(email, cleanOtp, newPassword);
        if (!outcome.successful()) {
            throw new BadRequestException(outcome.message());
        }
        return outcome.message();
    }
}

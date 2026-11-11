package com.example.student_management.service.email.impl;

import com.example.student_management.service.email.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Override
    public void sendOtpEmail(String toEmail, String fullName, String otp) {
        log.info("\n" +
                "========================================================================\n" +
                "[EMAIL SERVICE] GỬI MÃ OTP ĐẶT LẠI MẬT KHẨU\n" +
                "------------------------------------------------------------------------\n" +
                "Người nhận : {} ({})\n" +
                "Tiêu đề    : [SIS] Mã OTP đặt lại mật khẩu: {}\n" +
                "MÃ OTP     : >>> {} <<<\n" +
                "Thời hạn   : 5 phút (TTL - Tối đa 5 lần nhập sai)\n" +
                "========================================================================",
                toEmail, fullName != null ? fullName : "Người dùng", otp, otp);

        if (mailSender != null && fromEmail != null && !fromEmail.isBlank()) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromEmail, "Student Information System");
                helper.setTo(toEmail);
                helper.setSubject("[SIS] Mã OTP xác thực đặt lại mật khẩu: " + otp);

                String htmlContent = "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;\">" +
                        "<h2 style=\"color: #1e3a8a; text-align: center;\">Student Information System</h2>" +
                        "<p>Xin chào <strong>" + (fullName != null ? fullName : "bạn") + "</strong>,</p>" +
                        "<p>Bạn vừa gửi yêu cầu đặt lại mật khẩu cho tài khoản liên kết với email này. Dưới đây là mã xác thực OTP của bạn:</p>" +
                        "<div style=\"text-align: center; margin: 30px 0;\">" +
                        "  <span style=\"display: inline-block; font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #2563eb; background: #eff6ff; padding: 12px 28px; border-radius: 8px; border: 1px dashed #60a5fa;\">" +
                        otp +
                        "  </span>" +
                        "</div>" +
                        "<p style=\"color: #64748b; font-size: 14px;\">• Mã OTP này có hiệu lực trong vòng <strong>5 phút</strong>.<br>• Tối đa 5 lần nhập thử.<br>• Tuyệt đối không chia sẻ mã này cho bất kỳ ai.</p>" +
                        "<hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;\" />" +
                        "<p style=\"font-size: 12px; color: #94a3b8; text-align: center;\">Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email hoặc liên hệ quản trị viên.</p>" +
                        "</div>";

                helper.setText(htmlContent, true);
                mailSender.send(message);
                log.info("Đã gửi email chứa mã OTP thành công tới: {}", toEmail);
            } catch (Exception e) {
                log.warn("Không thể gửi email qua SMTP ({}). Mã OTP vẫn có hiệu lực và được log tại console.", e.getMessage());
            }
        }
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetToken, String resetUrl) {
        sendOtpEmail(toEmail, fullName, resetToken);
    }

    @Override
    public void sendPasswordResetSuccessEmail(String toEmail, String fullName) {
        log.info("\n" +
                "========================================================================\n" +
                "[EMAIL SERVICE] CẢNH BÁO BẢO MẬT: MẬT KHẨU ĐÃ ĐƯỢC THAY ĐỔI\n" +
                "------------------------------------------------------------------------\n" +
                "Người nhận : {} ({})\n" +
                "Thông báo  : Mật khẩu tài khoản của bạn vừa được thay đổi thành công.\n" +
                "Thời gian  : {}\n" +
                "========================================================================",
                toEmail, fullName != null ? fullName : "Người dùng",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")));

        if (mailSender != null && fromEmail != null && !fromEmail.isBlank()) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromEmail, "Student Information System");
                helper.setTo(toEmail);
                helper.setSubject("[SIS] Cảnh báo bảo mật: Mật khẩu của bạn đã được thay đổi thành công");

                String htmlContent = "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;\">" +
                        "<h2 style=\"color: #1e3a8a; text-align: center;\">Student Information System</h2>" +
                        "<p>Xin chào <strong>" + (fullName != null ? fullName : "bạn") + "</strong>,</p>" +
                        "<p>Mật khẩu tài khoản của bạn vừa được cập nhật thành công vào lúc <strong>" +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")) + "</strong>.</p>" +
                        "<div style=\"background: #fef2f2; border: 1px solid #fecaca; border-radius: 6px; padding: 12px 16px; margin: 20px 0; color: #991b1b; font-size: 14px;\">" +
                        "  <strong>Lưu ý bảo mật:</strong> Nếu bạn KHÔNG thực hiện thay đổi này, tài khoản của bạn có thể đã bị xâm nhập trái phép. Vui lòng liên hệ ngay với Ban quản trị hệ thống hoặc Bộ phận hỗ trợ đào tạo để được trợ giúp khẩn cấp." +
                        "</div>" +
                        "<hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;\" />" +
                        "<p style=\"font-size: 12px; color: #94a3b8; text-align: center;\">Email tự động từ Hệ thống Quản lý Đào tạo SIS. Vui lòng không trả lời thư này.</p>" +
                        "</div>";

                helper.setText(htmlContent, true);
                mailSender.send(message);
                log.info("Đã gửi email cảnh báo bảo mật tới: {}", toEmail);
            } catch (Exception e) {
                log.warn("Không thể gửi email cảnh báo bảo mật qua SMTP: {}", e.getMessage());
            }
        }
    }
}

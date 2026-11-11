package com.example.student_management.service.feedback.impl;

import com.example.student_management.dto.feedback.*;
import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.auth.*;
import com.example.student_management.entity.feedback.Feedback;
import com.example.student_management.entity.feedback.FeedbackMessage;
import com.example.student_management.entity.feedback.FeedbackStatus;
import com.example.student_management.entity.feedback.FeedbackType;
import com.example.student_management.entity.notification.Notification;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.auth.LecturerRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.repository.feedback.FeedbackMessageRepository;
import com.example.student_management.repository.feedback.FeedbackRepository;
import com.example.student_management.repository.notification.NotificationRepository;
import com.example.student_management.service.feedback.FeedbackService;
import com.example.student_management.service.notification.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FeedbackServiceImpl implements FeedbackService {
    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackMessageRepository feedbackMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Override
    @Transactional
    public FeedbackDto createFeedback(String username, FeedbackCreateRequest request) {
        User user = getUser(username);
        if (user.getStudent() == null) {
            throw new BadRequestException("Chỉ tài khoản sinh viên mới có thể gửi phản ánh");
        }
        Student student = user.getStudent();
        if (student.getStatus() == AccountStatus.INACTIVE) {
            throw new ForbiddenException("Tài khoản sinh viên đang bị khóa");
        }

        CourseSection section = null;
        if (request.getCourseSectionId() != null) {
            section = courseSectionRepository.findById(request.getCourseSectionId()).orElse(null);
        }

        Lecturer assignedLecturer = null;
        if (request.getLecturerId() != null) {
            assignedLecturer = lecturerRepository.findById(request.getLecturerId()).orElse(null);
        } else if (section != null && section.getLecturer() != null) {
            assignedLecturer = section.getLecturer();
        }

        FeedbackStatus initialStatus = (assignedLecturer != null) ? FeedbackStatus.IN_PROGRESS : FeedbackStatus.PENDING;

        Feedback feedback = Feedback.builder()
                .title(request.getTitle().trim())
                .feedbackType(request.getFeedbackType())
                .content(request.getContent().trim())
                .status(initialStatus)
                .student(student)
                .courseSection(section)
                .assignedLecturer(assignedLecturer)
                .createdAt(LocalDateTime.now())
                .build();

        Feedback saved = feedbackRepository.save(feedback);

        // Lưu tin nhắn khởi tạo đầu tiên vào lịch sử trao đổi
        FeedbackMessage initialMessage = FeedbackMessage.builder()
                .feedback(saved)
                .sender(user)
                .message(saved.getContent())
                .createdAt(LocalDateTime.now())
                .build();
        feedbackMessageRepository.save(initialMessage);

        // Nếu đã được gán trực tiếp cho giảng viên, ghi nhận vào luồng trao đổi
        if (assignedLecturer != null) {
            FeedbackMessage autoAssignMessage = FeedbackMessage.builder()
                    .feedback(saved)
                    .sender(user)
                    .message("Hệ thống: Sinh viên đã chỉ định gửi trực tiếp đến Giảng viên " + assignedLecturer.getFullName() + " tiếp nhận xử lý.")
                    .createdAt(LocalDateTime.now().plusSeconds(1))
                    .build();
            feedbackMessageRepository.save(autoAssignMessage);
        }

        // Tự động thông báo cho Giảng viên được phân công (nếu có tài khoản)
        if (assignedLecturer != null && assignedLecturer.getUser() != null) {
            try {
                notificationService.sendSystemNotification(
                        assignedLecturer.getUser(),
                        "Bạn nhận được phản ánh mới từ sinh viên " + student.getFullName(),
                        "Sinh viên " + student.getFullName() + " (" + student.getStudentCode() + ") đã gửi phản ánh #" + saved.getId() + ": \"" + saved.getTitle() + "\"",
                        "ACADEMIC"
                );
            } catch (Exception ignored) {}
        }

        // Tự động thông báo xác nhận cho Sinh viên
        try {
            notificationService.sendSystemNotification(
                    user,
                    "Gửi phản ánh thành công: #" + saved.getId(),
                    "Phản ánh \"" + saved.getTitle() + "\" của bạn đã được tiếp nhận" +
                            (assignedLecturer != null ? " và chuyển trực tiếp tới Giảng viên " + assignedLecturer.getFullName() + "." : " và đang chờ tiếp nhận."),
                    "ACADEMIC"
            );
        } catch (Exception ignored) {}

        // Gửi thông báo đến Admin
        try {
            Notification notification = Notification.builder()
                    .title("Phản ánh mới từ sinh viên " + student.getFullName())
                    .content("Sinh viên " + student.getFullName() + " (" + student.getStudentCode() + ") đã gửi phản ánh: \"" + saved.getTitle() + "\"" +
                            (assignedLecturer != null ? " (Chỉ định cho GV " + assignedLecturer.getFullName() + ")" : ""))
                    .type("ACADEMIC")
                    .targetRole(Role.ADMIN)
                    .sender(user)
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(notification);
        } catch (Exception ignored) {}

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackDto> getMyFeedbacks(String username) {
        User user = getUser(username);
        if (user.getStudent() == null) {
            return List.of();
        }
        return feedbackRepository.findByStudentIdOrderByCreatedAtDesc(user.getStudent().getId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackDto> getAssignedFeedbacks(String username) {
        User user = getUser(username);
        if (user.getLecturer() == null) {
            return List.of();
        }
        return feedbackRepository.findByAssignedLecturerIdOrderByCreatedAtDesc(user.getLecturer().getId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackDto> getAllFeedbacks(FeedbackStatus status, FeedbackType type) {
        return feedbackRepository.findWithFilters(status, type)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackDto getFeedbackById(String username, Long id) {
        User user = getUser(username);
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phản ánh #" + id));

        validateAccess(user, feedback);
        return toDto(feedback);
    }

    @Override
    @Transactional
    public FeedbackDto assignToLecturer(String username, Long feedbackId, FeedbackAssignRequest request) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phản ánh #" + feedbackId));

        Lecturer lecturer = lecturerRepository.findById(request.getLecturerId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giảng viên #" + request.getLecturerId()));

        User admin = getUser(username);
        feedback.setAssignedLecturer(lecturer);
        if (feedback.getStatus() == FeedbackStatus.PENDING) {
            feedback.setStatus(FeedbackStatus.IN_PROGRESS);
        }
        if (request.getAdminNote() != null && !request.getAdminNote().isBlank()) {
            feedback.setAdminNote(request.getAdminNote().trim());
        }
        feedback.setUpdatedAt(LocalDateTime.now());
        Feedback saved = feedbackRepository.save(feedback);

        // Lưu vào lịch sử trao đổi
        FeedbackMessage assignMessage = FeedbackMessage.builder()
                .feedback(saved)
                .sender(admin)
                .message("Hệ thống: Quản trị viên đã phân công phản ánh này cho giảng viên " + lecturer.getFullName() +
                        (request.getAdminNote() != null && !request.getAdminNote().isBlank() ? " (Ghi chú: " + request.getAdminNote().trim() + ")" : ""))
                .createdAt(LocalDateTime.now())
                .build();
        feedbackMessageRepository.save(assignMessage);

        // Thông báo cho Giảng viên (nếu có tài khoản)
        try {
            if (lecturer.getUser() != null) {
                notificationService.sendSystemNotification(
                        lecturer.getUser(),
                        "Bạn được phân công xử lý phản ánh",
                        "Admin đã phân công cho bạn xử lý phản ánh #" + saved.getId() + ": \"" + saved.getTitle() + "\"",
                        "ACADEMIC"
                );
            }
        } catch (Exception ignored) {}

        // Thông báo cho Sinh viên (nếu có tài khoản)
        try {
            if (saved.getStudent() != null && saved.getStudent().getUser() != null) {
                notificationService.sendSystemNotification(
                        saved.getStudent().getUser(),
                        "Tiến độ phản ánh #" + saved.getId(),
                        "Phản ánh \"" + saved.getTitle() + "\" của bạn đã được chuyển cho Giảng viên " + lecturer.getFullName() + " xử lý.",
                        "ACADEMIC"
                );
            }
        } catch (Exception ignored) {}

        return toDto(saved);
    }

    @Override
    @Transactional
    public FeedbackDto updateStatus(String username, Long feedbackId, FeedbackStatusUpdateRequest request) {
        User user = getUser(username);
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phản ánh #" + feedbackId));

        validateAccess(user, feedback);
        if (user.getRole() == Role.STUDENT) {
            throw new ForbiddenException("Sinh viên không được phép đổi trạng thái phản ánh");
        }

        feedback.setStatus(request.getStatus());
        if (request.getNote() != null && !request.getNote().isBlank()) {
            feedback.setAdminNote(request.getNote().trim());
        }
        feedback.setUpdatedAt(LocalDateTime.now());
        Feedback saved = feedbackRepository.save(feedback);

        // Lưu lịch sử
        FeedbackMessage statusMessage = FeedbackMessage.builder()
                .feedback(saved)
                .sender(user)
                .message("Trạng thái phản ánh được cập nhật thành: " + getStatusDisplayName(request.getStatus()) +
                        (request.getNote() != null && !request.getNote().isBlank() ? " - Ghi chú: " + request.getNote().trim() : ""))
                .createdAt(LocalDateTime.now())
                .build();
        feedbackMessageRepository.save(statusMessage);

        // Thông báo cho Sinh viên
        try {
            if (saved.getStudent() != null && saved.getStudent().getUser() != null) {
                notificationService.sendSystemNotification(
                        saved.getStudent().getUser(),
                        "Cập nhật phản ánh #" + saved.getId(),
                        "Phản ánh \"" + saved.getTitle() + "\" đã được cập nhật trạng thái: " + getStatusDisplayName(request.getStatus()),
                        "ACADEMIC"
                );
            }
        } catch (Exception ignored) {}

        return toDto(saved);
    }

    @Override
    @Transactional
    public FeedbackMessageDto addMessage(String username, Long feedbackId, FeedbackMessageRequest request) {
        User user = getUser(username);
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phản ánh #" + feedbackId));

        validateAccess(user, feedback);

        FeedbackMessage message = FeedbackMessage.builder()
                .feedback(feedback)
                .sender(user)
                .message(request.getMessage().trim())
                .createdAt(LocalDateTime.now())
                .build();
        FeedbackMessage saved = feedbackMessageRepository.save(message);

        // Nếu người phản hồi là Giảng viên hoặc Admin, cập nhật trạng thái thành RESPONDED (nếu chưa giải quyết)
        if ((user.getRole() == Role.LECTURER || user.getRole() == Role.ADMIN)
                && feedback.getStatus() != FeedbackStatus.RESOLVED
                && feedback.getStatus() != FeedbackStatus.REJECTED) {
            feedback.setStatus(FeedbackStatus.RESPONDED);
        }
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);

        // Gửi thông báo đến đối tượng liên quan
        try {
            if (user.getRole() == Role.STUDENT) {
                // Sinh viên gửi tin nhắn -> Thông báo cho Giảng viên phụ trách
                if (feedback.getAssignedLecturer() != null && feedback.getAssignedLecturer().getUser() != null) {
                    notificationService.sendSystemNotification(
                            feedback.getAssignedLecturer().getUser(),
                            "Tin nhắn mới từ sinh viên trong phản ánh #" + feedback.getId(),
                            "Sinh viên " + user.getFullName() + ": " + truncate(request.getMessage(), 60),
                            "ACADEMIC"
                    );
                }
            } else {
                // Giảng viên hoặc Admin gửi tin nhắn -> Thông báo cho Sinh viên
                if (feedback.getStudent() != null && feedback.getStudent().getUser() != null) {
                    notificationService.sendSystemNotification(
                            feedback.getStudent().getUser(),
                            "Phản hồi mới trong phản ánh #" + feedback.getId(),
                            user.getFullName() + " đã trả lời phản ánh của bạn: " + truncate(request.getMessage(), 60),
                            "ACADEMIC"
                    );
                }
            }
        } catch (Exception ignored) {}

        return toMessageDto(saved);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
    }

    private void validateAccess(User user, Feedback feedback) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (user.getRole() == Role.STUDENT) {
            if (user.getStudent() != null && feedback.getStudent().getId().equals(user.getStudent().getId())) {
                return;
            }
        }
        if (user.getRole() == Role.LECTURER) {
            if (user.getLecturer() != null && feedback.getAssignedLecturer() != null
                    && feedback.getAssignedLecturer().getId().equals(user.getLecturer().getId())) {
                return;
            }
        }
        throw new ForbiddenException("Bạn không có quyền truy cập phản ánh này");
    }

    private String getStatusDisplayName(FeedbackStatus status) {
        return switch (status) {
            case PENDING -> "Chờ tiếp nhận";
            case IN_PROGRESS -> "Đang xử lý";
            case RESPONDED -> "Đã phản hồi";
            case RESOLVED -> "Đã giải quyết";
            case REJECTED -> "Từ chối xử lý";
        };
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private FeedbackDto toDto(Feedback feedback) {
        List<FeedbackMessage> messages = feedbackMessageRepository.findByFeedbackIdOrderByCreatedAtAsc(feedback.getId());
        List<FeedbackMessageDto> messageDtos = messages != null
                ? messages.stream().map(this::toMessageDto).collect(Collectors.toList())
                : List.of();

        return FeedbackDto.builder()
                .id(feedback.getId())
                .title(feedback.getTitle())
                .feedbackType(feedback.getFeedbackType())
                .content(feedback.getContent())
                .status(feedback.getStatus())
                .studentId(feedback.getStudent() != null ? feedback.getStudent().getId() : null)
                .studentCode(feedback.getStudent() != null ? feedback.getStudent().getStudentCode() : null)
                .studentName(feedback.getStudent() != null ? feedback.getStudent().getFullName() : null)
                .studentEmail(feedback.getStudent() != null ? feedback.getStudent().getEmail() : null)
                .assignedLecturerId(feedback.getAssignedLecturer() != null ? feedback.getAssignedLecturer().getId() : null)
                .assignedLecturerCode(feedback.getAssignedLecturer() != null ? feedback.getAssignedLecturer().getLecturerCode() : null)
                .assignedLecturerName(feedback.getAssignedLecturer() != null ? feedback.getAssignedLecturer().getFullName() : null)
                .assignedLecturerEmail(feedback.getAssignedLecturer() != null ? feedback.getAssignedLecturer().getEmail() : null)
                .courseSectionId(feedback.getCourseSection() != null ? feedback.getCourseSection().getId() : null)
                .sectionCode(feedback.getCourseSection() != null ? feedback.getCourseSection().getSectionCode() : null)
                .courseName(feedback.getCourseSection() != null && feedback.getCourseSection().getCourse() != null
                        ? feedback.getCourseSection().getCourse().getCourseName() : null)
                .adminNote(feedback.getAdminNote())
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
                .messages(messageDtos)
                .build();
    }

    private FeedbackMessageDto toMessageDto(FeedbackMessage msg) {
        return FeedbackMessageDto.builder()
                .id(msg.getId())
                .feedbackId(msg.getFeedback() != null ? msg.getFeedback().getId() : null)
                .senderId(msg.getSender() != null ? msg.getSender().getId() : null)
                .senderUsername(msg.getSender() != null ? msg.getSender().getUsername() : null)
                .senderFullName(msg.getSender() != null ? msg.getSender().getFullName() : null)
                .senderRole(msg.getSender() != null ? msg.getSender().getRole() : null)
                .message(msg.getMessage())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}

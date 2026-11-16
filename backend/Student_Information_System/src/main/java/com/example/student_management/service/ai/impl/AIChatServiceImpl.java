package com.example.student_management.service.ai.impl;

import com.example.student_management.dto.ai.*;
import com.example.student_management.dto.grade.StudentGradeDto;
import com.example.student_management.entity.ai.AIConversation;
import com.example.student_management.entity.ai.AIMessage;
import com.example.student_management.entity.ai.AIUsageLog;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.ai.AIConversationRepository;
import com.example.student_management.repository.ai.AIMessageRepository;
import com.example.student_management.repository.ai.AIUsageLogRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.ai.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIChatServiceImpl implements AIChatService {

    @Autowired private UserRepository userRepository;
    @Autowired private AIConversationRepository conversationRepository;
    @Autowired private AIMessageRepository messageRepository;
    @Autowired private AIUsageLogRepository usageLogRepository;
    @Autowired private AIProviderService providerService;
    @Autowired private AIChatToolsService toolsService;

    private final AIIntentService intentService = new AIIntentService();

    @Value("${app.ai.rate-limit-per-hour:60}")
    private int rateLimitPerHour;

    @Override
    @Transactional(readOnly = true)
    public List<AIConversationDto> getConversations(String username) {
        Student student = getStudent(username);
        return conversationRepository.findByStudentIdOrderByUpdatedAtDesc(student.getId()).stream()
                .map(this::toConversationDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AIConversationDto createConversation(String username, AICreateConversationRequest request) {
        Student student = getStudent(username);
        String title = (request != null && request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle().trim()
                : "Hội thoại học tập mới";
        AIConversation conversation = AIConversation.builder()
                .student(student)
                .title(title)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return toConversationDto(conversationRepository.save(conversation));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AIMessageDto> getMessages(String username, Long conversationId) {
        Student student = getStudent(username);
        AIConversation conversation = getOwnedConversation(conversationId, student.getId());
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AIChatResponse sendMessage(String username, Long conversationId, AIChatRequest request) {
        User user = getUser(username);
        Student student = user.getStudent();
        if (user.getRole() != Role.STUDENT || student == null) {
            throw new ForbiddenException("Chỉ tài khoản sinh viên mới được sử dụng Trợ lý học tập AI");
        }
        AIConversation conversation = getOwnedConversation(conversationId, student.getId());

        long recentCalls = usageLogRepository.countByStudentIdAndCreatedAtAfter(student.getId(), LocalDateTime.now().minusHours(1));
        if (recentCalls >= rateLimitPerHour) {
            throw new BadRequestException("Bạn đã dùng hết hạn mức " + rateLimitPerHour + " lượt hỏi trong 1 giờ. Vui lòng thử lại sau.");
        }

        String question = request.getMessage() == null ? "" : request.getMessage().trim();
        if (question.isBlank()) throw new BadRequestException("Nội dung câu hỏi không được để trống");
        if (question.length() > 2000) question = question.substring(0, 2000);

        // Save user message
        AIMessage userMessage = messageRepository.save(AIMessage.builder()
                .conversation(conversation)
                .role("USER")
                .content(question)
                .createdAt(LocalDateTime.now())
                .build());

        List<AIMessage> previousMessages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        boolean hasContext = previousMessages.stream().filter(m -> m.getId() != null && !m.getId().equals(userMessage.getId())).anyMatch(m -> "ASSISTANT".equalsIgnoreCase(m.getRole()));
        AIIntentService.Intent intent = intentService.detectIntent(question, hasContext);

        // Build history for provider (role/content), keep recent 14 for prompt window
        List<Map<String, String>> providerHistory = new ArrayList<>();
        for (AIMessage m : previousMessages) {
            String role = "ASSISTANT".equalsIgnoreCase(m.getRole()) ? "assistant" : "user";
            providerHistory.add(Map.of("role", role, "content", safe(m.getContent())));
        }
        String conversationSummary = buildConversationSummary(previousMessages, question);

        // Authorized SIS data fetching based on intent/context
        DataBundle data = fetchDataForIntent(user, student, intent, question, previousMessages);
        String systemContext = buildSystemContext(student, intent, data, conversationSummary, previousMessages);
        String fallback = buildFallbackAnswer(intent, data, question, conversationSummary);

        AIProviderService.AIProviderResult result = providerService.generateChatResponse(
                systemContext,
                providerHistory,
                question,
                fallback,
                data.sources
        );

        AIMessage savedAssistant = messageRepository.save(AIMessage.builder()
                .conversation(conversation)
                .role("ASSISTANT")
                .content(result.getContent())
                .sources(String.join(", ", result.getSources() != null ? result.getSources() : data.sources))
                .createdAt(LocalDateTime.now())
                .build());

        if ("Hội thoại học tập mới".equals(conversation.getTitle())) {
            conversation.setTitle(question.length() > 40 ? question.substring(0, 37) + "..." : question);
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        try {
            usageLogRepository.save(AIUsageLog.builder()
                    .student(student)
                    .featureType("CHAT")
                    .provider(result.getProviderUsed())
                    .model(result.getModelUsed())
                    .status(result.isSuccess() ? "SUCCESS" : "FAILED")
                    .latencyMs(result.getLatencyMs())
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception ignored) {}

        return AIChatResponse.builder()
                .messageId(savedAssistant.getId())
                .conversationId(conversation.getId())
                .role("ASSISTANT")
                .content(savedAssistant.getContent())
                .sources(result.getSources() != null ? result.getSources() : data.sources)
                .createdAt(savedAssistant.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public void deleteConversation(String username, Long conversationId) {
        Student student = getStudent(username);
        AIConversation conversation = getOwnedConversation(conversationId, student.getId());
        conversationRepository.delete(conversation);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAcademicOverview(String username) {
        User user = getUser(username);
        Student student = user.getStudent();
        if (student == null) throw new ForbiddenException("Chỉ tài khoản sinh viên mới được truy cập dữ liệu này");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.putAll(toolsService.getStudentProfile(student));
        List<StudentGradeDto> grades = toolsService.getPublishedGrades(user);
        summary.putAll(toolsService.computeGpaAndCredits(grades));
        List<com.example.student_management.entity.academic.Enrollment> enrollments = toolsService.getAllEnrollments(student);
        Map<String, Object> enr = toolsService.summarizeEnrollments(enrollments);
        @SuppressWarnings("unchecked") List<Map<String, Object>> items = (List<Map<String, Object>>) enr.getOrDefault("items", List.of());
        summary.put("activeEnrollments", items.stream().filter(i -> "ENROLLED".equals(String.valueOf(i.get("status")))).toList());
        summary.put("activeCredits", enr.getOrDefault("activeCredits", 0));
        return summary;
    }

    // ===== context/data helpers =====
    private static class DataBundle {
        Map<String, Object> profile;
        List<StudentGradeDto> grades;
        Map<String, Object> gpa;
        Map<String, Object> enrollments;
        List<Map<String, Object>> semesters;
        List<Map<String, Object>> exams;
        List<String> sources = new ArrayList<>();
    }

    private DataBundle fetchDataForIntent(User user, Student student, AIIntentService.Intent intent, String question, List<AIMessage> history) {
        DataBundle d = new DataBundle();
        boolean needProfile = intent == AIIntentService.Intent.STUDENT_PROFILE || intent == AIIntentService.Intent.GENERAL_ACADEMIC_QUESTION || intent == AIIntentService.Intent.ACADEMIC_PROGRESS || intent == AIIntentService.Intent.FOLLOW_UP_QUESTION;
        boolean needGrades = intent == AIIntentService.Intent.GPA_QUERY || intent == AIIntentService.Intent.CREDIT_QUERY || intent == AIIntentService.Intent.GRADE_QUERY
                || intent == AIIntentService.Intent.ACADEMIC_PROGRESS || intent == AIIntentService.Intent.INCOMPLETE_COURSE_QUERY || intent == AIIntentService.Intent.SEMESTER_QUERY
                || intent == AIIntentService.Intent.FOLLOW_UP_QUESTION || intent == AIIntentService.Intent.GENERAL_ACADEMIC_QUESTION;
        boolean needEnroll = intent == AIIntentService.Intent.ENROLLMENT_QUERY || intent == AIIntentService.Intent.COURSE_QUERY || intent == AIIntentService.Intent.ACADEMIC_PROGRESS
                || intent == AIIntentService.Intent.FOLLOW_UP_QUESTION || intent == AIIntentService.Intent.GENERAL_ACADEMIC_QUESTION;
        boolean needSem = intent == AIIntentService.Intent.SEMESTER_QUERY || intent == AIIntentService.Intent.ACADEMIC_PROGRESS || intent == AIIntentService.Intent.FOLLOW_UP_QUESTION;
        boolean needExam = mentionsExam(question) || mentionsExam(recentHistoryText(history));

        if (intent == AIIntentService.Intent.OUT_OF_SCOPE) {
            d.profile = toolsService.getStudentProfile(student);
            d.sources.add("Student SIS Profile");
            return d;
        }
        // follow-up: try to be inclusive but still bounded
        if (intent == AIIntentService.Intent.FOLLOW_UP_QUESTION) {
            needProfile = needGrades = needEnroll = true;
            needSem = containsAnyNorm(question, "hoc ky", "hoc ki", "semester", "ky") || containsAnyNorm(recentHistoryText(history), "hoc ky", "semester");
        }

        if (needProfile) { d.profile = toolsService.getStudentProfile(student); d.sources.add("Student SIS Profile"); }
        if (needGrades) {
            d.grades = toolsService.getPublishedGrades(user);
            d.gpa = toolsService.computeGpaAndCredits(d.grades);
            d.sources.add("Published Grades & Academic Records");
        }
        if (needEnroll) {
            d.enrollments = toolsService.summarizeEnrollments(toolsService.getAllEnrollments(student));
            d.sources.add("Enrollment Records");
        }
        if (needSem) {
            if (d.grades == null) d.grades = toolsService.getPublishedGrades(user);
            d.semesters = toolsService.getSemesterBreakdown(d.grades);
            if (!d.sources.contains("Published Grades & Academic Records")) d.sources.add("Published Grades & Academic Records");
        }
        if (needExam) {
            d.exams = toolsService.getUpcomingExams(student);
            d.sources.add("Exam Schedule");
        }
        if (d.sources.isEmpty()) d.sources.add("Student SIS Profile");
        return d;
    }

    private String buildSystemContext(Student student, AIIntentService.Intent intent, DataBundle d, String summary, List<AIMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VAI TRÒ ===\nBạn là AI Study Assistant của hệ thống SIS. Chỉ hỗ trợ sinh viên đang đăng nhập dựa trên dữ liệu SIS được cung cấp.\n\n");
        sb.append("=== Ý ĐỊNH NHẬN DIỆN ===\n").append(intent.name()).append("\n\n");
        sb.append("=== NGỮ CẢNH HỘI THOẠI (TÓM TẮT) ===\n").append(summary.isBlank() ? "(Chưa có ngữ cảnh trước)" : summary).append("\n\n");

        sb.append("=== DỮ LIỆU SIS ĐƯỢC PHÉP SỬ DỤNG ===\n");
        if (d.profile != null) {
            sb.append("[Hồ sơ sinh viên]\n");
            d.profile.forEach((k,v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        if (d.gpa != null) {
            sb.append("[Kết quả học tập tổng hợp]\n");
            sb.append("- Tín chỉ tích lũy: ").append(d.gpa.getOrDefault("accumulatedCredits", 0)).append("\n");
            sb.append("- GPA hệ 10: ").append(d.gpa.getOrDefault("gpa10", "0.00")).append("\n");
            sb.append("- GPA hệ 4: ").append(d.gpa.getOrDefault("gpa4", "0.00")).append("\n");
            sb.append("- Số môn đạt: ").append(d.gpa.getOrDefault("passedCount", 0)).append("\n");
            sb.append("- Số môn chưa đạt: ").append(d.gpa.getOrDefault("failedCount", 0)).append("\n");
            appendCourseList(sb, "Môn đã đạt/công bố", (List<Map<String, Object>>) d.gpa.get("passedCourses"), 20);
            appendCourseList(sb, "Môn chưa đạt/cần cải thiện", (List<Map<String, Object>>) d.gpa.get("failedCourses"), 20);
        }
        if (d.semesters != null && !d.semesters.isEmpty()) {
            sb.append("[Tổng hợp theo học kỳ]\n");
            for (Map<String, Object> s : d.semesters) {
                sb.append("- ").append(s.get("semester")).append(": GPA ").append(s.get("gpa10"))
                  .append(", đạt ").append(s.get("passedCount")).append("/").append(s.get("courseCount"))
                  .append(" môn, tín chỉ đạt ").append(s.get("earnedCredits")).append("\n");
            }
        }
        if (d.enrollments != null) {
            sb.append("[Đăng ký/lớp học phần]\n");
            @SuppressWarnings("unchecked") List<Map<String, Object>> items = (List<Map<String, Object>>) d.enrollments.getOrDefault("items", List.of());
            if (items.isEmpty()) sb.append("- Chưa có dữ liệu đăng ký học phần.\n");
            int shown = 0;
            for (Map<String, Object> it : items) {
                if (shown++ >= 25) { sb.append("- ...(đã rút gọn)\n"); break; }
                sb.append("- ").append(it.get("status")).append(": ").append(it.get("sectionCode")).append(" - ")
                  .append(it.get("courseName")).append(" (").append(it.get("credits")).append(" TC, ")
                  .append(it.get("semester")).append("), GV: ").append(it.get("lecturer"))
                  .append(", Lịch: ").append(it.get("schedule")).append("\n");
            }
        }
        if (d.exams != null) {
            sb.append("[Lịch thi sắp tới]\n");
            if (d.exams.isEmpty()) sb.append("- Hiện hệ thống chưa có dữ liệu lịch thi phù hợp.\n");
            for (Map<String, Object> ex : d.exams) {
                sb.append("- ").append(ex.get("courseCode")).append(" - ").append(ex.get("courseName"))
                  .append(" (").append(ex.get("examType")).append(") ngày ").append(ex.get("examDate"))
                  .append(" ").append(ex.getOrDefault("startTime", "")).append("-").append(ex.getOrDefault("endTime", ""))
                  .append(", phòng: ").append(ex.getOrDefault("room", "Chưa có")).append(", trạng thái: ").append(ex.get("status")).append("\n");
            }
        }

        sb.append("\n=== QUY TẮC BẮT BUỘC ===\n");
        sb.append("1. Chỉ dựa trên dữ liệu SIS ở trên. KHÔNG bịa điểm, tín chỉ, học phần, lịch, quy định.\n");
        sb.append("2. Nếu dữ liệu chưa có/không đủ, phải nói rõ: 'Hiện hệ thống chưa có dữ liệu này' hoặc nêu rõ phần còn thiếu.\n");
        sb.append("3. Phân biệt rõ: 'Dữ liệu thực tế' và 'Nhận xét/gợi ý của AI'.\n");
        sb.append("4. Không khẳng định đã thực hiện nghiệp vụ (đăng ký học phần, sửa điểm...).\n");
        sb.append("5. Với câu hỏi tiếp theo (môn đó/kết quả trên/vì sao/phân tích kỹ hơn), phải dựa vào ngữ cảnh hội thoại và dữ liệu đã cung cấp; nếu không xác định được đối tượng tham chiếu, hãy hỏi lại ngắn gọn.\n");
        sb.append("6. Trả lời tiếng Việt tự nhiên, rõ ràng, có cấu trúc (danh sách/bảng khi phù hợp).\n");
        if (intent == AIIntentService.Intent.OUT_OF_SCOPE) {
            sb.append("7. Câu hỏi nằm ngoài phạm vi hỗ trợ học tập/SIS: trả lời lịch sự, từ chối phần ngoài phạm vi và gợi ý câu hỏi học tập phù hợp.\n");
        }
        return sb.toString();
    }

    private void appendCourseList(StringBuilder sb, String title, List<Map<String, Object>> list, int limit) {
        sb.append("- ").append(title).append(": ").append(list == null ? 0 : list.size()).append("\n");
        if (list == null) return;
        int i = 0;
        for (Map<String, Object> c : list) {
            if (i++ >= limit) { sb.append("  + ...(đã rút gọn)\n"); break; }
            sb.append("  + ").append(c.get("courseCode")).append(" - ").append(c.get("courseName"))
              .append(" (").append(c.get("credits")).append(" TC, ").append(c.get("semester"))
              .append("), điểm ").append(c.get("finalScore")).append(", ").append(c.get("classification")).append("\n");
        }
    }

    private String buildFallbackAnswer(AIIntentService.Intent intent, DataBundle d, String question, String summary) {
        String name = d.profile != null ? String.valueOf(d.profile.getOrDefault("fullName", "bạn")) : "bạn";
        if (intent == AIIntentService.Intent.OUT_OF_SCOPE) {
            return "Mình là trợ lý học tập AI của hệ thống SIS nên chỉ hỗ trợ các câu hỏi về học tập và dữ liệu học vụ của bạn (GPA, tín chỉ, điểm, đăng ký học phần, lịch học/lịch thi khi có dữ liệu...). "
                    + "Bạn có thể hỏi ví dụ: \"Tôi đã tích lũy bao nhiêu tín chỉ?\" hoặc \"Phân tích kết quả học tập của tôi\".";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Chào **").append(name).append("**,\n\n");
        sb.append("**Dữ liệu thực tế (SIS):**\n");
        if (d.profile != null) {
            sb.append("- Mã SV: **").append(d.profile.getOrDefault("studentCode", "N/A")).append("**");
            sb.append(" | Khoa: **").append(d.profile.getOrDefault("department", "Chưa cập nhật")).append("**\n");
        }
        if (d.gpa != null && (intent == AIIntentService.Intent.GPA_QUERY || intent == AIIntentService.Intent.CREDIT_QUERY || intent == AIIntentService.Intent.ACADEMIC_PROGRESS || intent == AIIntentService.Intent.GRADE_QUERY || intent == AIIntentService.Intent.INCOMPLETE_COURSE_QUERY || intent == AIIntentService.Intent.SEMESTER_QUERY || intent == AIIntentService.Intent.FOLLOW_UP_QUESTION || intent == AIIntentService.Intent.GENERAL_ACADEMIC_QUESTION)) {
            sb.append("- GPA hệ 10: **").append(d.gpa.getOrDefault("gpa10", "0.00")).append("** | GPA hệ 4: **").append(d.gpa.getOrDefault("gpa4", "0.00")).append("**\n");
            sb.append("- Tín chỉ tích lũy: **").append(d.gpa.getOrDefault("accumulatedCredits", 0)).append(" TC**\n");
            sb.append("- Môn đạt: **").append(d.gpa.getOrDefault("passedCount", 0)).append("** | Môn chưa đạt: **").append(d.gpa.getOrDefault("failedCount", 0)).append("**\n");
        }
        if (intent == AIIntentService.Intent.CREDIT_QUERY && d.gpa != null) {
            sb.append("\n**Nhận xét của AI:** Bạn đã tích lũy **").append(d.gpa.getOrDefault("accumulatedCredits", 0)).append(" tín chỉ** theo dữ liệu điểm đã công bố.\n");
        }
        if ((intent == AIIntentService.Intent.GPA_QUERY || intent == AIIntentService.Intent.GRADE_QUERY) && d.gpa != null) {
            appendCourseList(sb, "Danh sách môn đã có điểm", mergeLists((List<Map<String, Object>>) d.gpa.get("passedCourses"), (List<Map<String, Object>>) d.gpa.get("failedCourses")), 15);
        }
        if (intent == AIIntentService.Intent.INCOMPLETE_COURSE_QUERY && d.gpa != null) {
            List<Map<String, Object>> failed = (List<Map<String, Object>>) d.gpa.getOrDefault("failedCourses", List.of());
            if (failed.isEmpty()) sb.append("\n- Hiện hệ thống chưa ghi nhận môn chưa đạt theo dữ liệu điểm công bố.\n");
            else appendCourseList(sb, "Môn chưa đạt/cần cải thiện", failed, 20);
        }
        if (intent == AIIntentService.Intent.SEMESTER_QUERY && d.semesters != null) {
            sb.append("\n**Theo học kỳ:**\n");
            if (d.semesters.isEmpty()) sb.append("- Hiện hệ thống chưa có dữ liệu điểm theo học kỳ.\n");
            for (Map<String, Object> s : d.semesters) {
                sb.append("- ").append(s.get("semester")).append(": GPA **").append(s.get("gpa10")).append("**, đạt ")
                  .append(s.get("passedCount")).append("/").append(s.get("courseCount")).append(" môn, tín chỉ đạt ")
                  .append(s.get("earnedCredits")).append("\n");
            }
        }
        if ((intent == AIIntentService.Intent.ENROLLMENT_QUERY || intent == AIIntentService.Intent.COURSE_QUERY) && d.enrollments != null) {
            @SuppressWarnings("unchecked") List<Map<String, Object>> items = (List<Map<String, Object>>) d.enrollments.getOrDefault("items", List.of());
            List<Map<String, Object>> active = items.stream().filter(i -> "ENROLLED".equals(String.valueOf(i.get("status")))).toList();
            sb.append("\n**Lớp học phần đang học (ENROLLED): ").append(active.size()).append("**\n");
            if (active.isEmpty()) sb.append("- Hiện bạn chưa có lớp học phần ENROLLED trong hệ thống.\n");
            for (int i = 0; i < Math.min(active.size(), 20); i++) {
                Map<String, Object> it = active.get(i);
                sb.append("- ").append(it.get("sectionCode")).append(" - ").append(it.get("courseName"))
                  .append(" (").append(it.get("credits")).append(" TC), lịch: ").append(it.get("schedule")).append("\n");
            }
        }
        if (d.exams != null && (mentionsExam(question) || intent == AIIntentService.Intent.FOLLOW_UP_QUESTION)) {
            sb.append("\n**Lịch thi:**\n");
            if (d.exams.isEmpty()) sb.append("- Hiện hệ thống chưa có dữ liệu lịch thi phù hợp.\n");
            for (Map<String, Object> ex : d.exams) {
                sb.append("- ").append(ex.get("courseCode")).append(" - ").append(ex.get("courseName"))
                  .append(" | ").append(ex.get("examDate")).append(" ").append(ex.getOrDefault("startTime", ""))
                  .append(" | phòng ").append(ex.getOrDefault("room", "Chưa có")).append("\n");
            }
        }
        if (!summary.isBlank() && intent == AIIntentService.Intent.FOLLOW_UP_QUESTION) {
            sb.append("\n**Ngữ cảnh hội thoại:** ").append(summary.length() > 300 ? summary.substring(0, 300) + "..." : summary).append("\n");
            sb.append("\n*Nếu bạn muốn, hãy nói rõ đối tượng bạn đang hỏi (môn/học kỳ/kết quả nào) để mình phân tích chính xác hơn.*\n");
        }
        if (intent == AIIntentService.Intent.GENERAL_ACADEMIC_QUESTION || intent == AIIntentService.Intent.STUDENT_PROFILE || intent == AIIntentService.Intent.ACADEMIC_PROGRESS) {
            sb.append("\nBạn có thể hỏi tiếp: \"Môn nào tôi chưa đạt?\", \"Tôi đang đăng ký môn nào?\", \"Điểm theo từng học kỳ ra sao?\" hoặc \"Môn nào điểm thấp nhất?\".");
        }
        return sb.toString();
    }

    private String buildConversationSummary(List<AIMessage> messages, String latestQuestion) {
        if (messages == null || messages.isEmpty()) return "";
        int max = messages.size();
        int start = Math.max(0, max - 12);
        List<String> points = new ArrayList<>();
        for (int i = start; i < max; i++) {
            AIMessage m = messages.get(i);
            String c = safe(m.getContent()).replaceAll("\\s+", " ").trim();
            if (c.length() > 120) c = c.substring(0, 117) + "...";
            if ("USER".equalsIgnoreCase(m.getRole())) points.add("SV hỏi: " + c);
            else points.add("AI trả lời: " + c);
        }
        String joined = String.join("\n", points);
        if (joined.length() > 1500) joined = joined.substring(joined.length() - 1500);
        return joined;
    }

    private String recentHistoryText(List<AIMessage> history) {
        if (history == null) return "";
        int start = Math.max(0, history.size() - 6);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < history.size(); i++) sb.append(history.get(i).getContent()).append(' ');
        return sb.toString();
    }

    private boolean mentionsExam(String q) { return containsAnyNorm(q, "lich thi", "thi mon", "phong thi", "ngay thi", "ky thi"); }

    private boolean containsAnyNorm(String q, String... keys) {
        String n = norm(q);
        for (String k : keys) if (n.contains(norm(k))) return true;
        return false;
    }

    private String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.replace('đ', 'd').replaceAll("\\s+", " ").trim();
        return n;
    }

    private List<Map<String, Object>> mergeLists(List<Map<String, Object>> a, List<Map<String, Object>> b) {
        List<Map<String, Object>> r = new ArrayList<>();
        if (a != null) r.addAll(a); if (b != null) r.addAll(b); return r;
    }

    private String safe(String s) { return s == null ? "" : s; }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản: " + username));
    }

    private Student getStudent(String username) {
        User user = getUser(username);
        if (user.getRole() != Role.STUDENT || user.getStudent() == null) {
            throw new ForbiddenException("Chỉ tài khoản sinh viên mới có quyền sử dụng chức năng này");
        }
        return user.getStudent();
    }

    private AIConversation getOwnedConversation(Long conversationId, Long studentId) {
        return conversationRepository.findByIdAndStudentId(conversationId, studentId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hội thoại hoặc bạn không có quyền truy cập"));
    }

    private AIConversationDto toConversationDto(AIConversation c) {
        List<AIMessage> msgs = c.getMessages();
        int count = msgs != null ? msgs.size() : 0;
        String lastMsg = (msgs != null && !msgs.isEmpty()) ? msgs.get(msgs.size() - 1).getContent() : null;
        if (lastMsg != null && lastMsg.length() > 60) lastMsg = lastMsg.substring(0, 57) + "...";
        return AIConversationDto.builder()
                .id(c.getId())
                .studentId(c.getStudent().getId())
                .title(c.getTitle())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getCreatedAt())
                .messageCount(count)
                .lastMessage(lastMsg)
                .build();
    }

    private AIMessageDto toMessageDto(AIMessage m) {
        List<String> sourcesList = (m.getSources() != null && !m.getSources().isBlank())
                ? Arrays.stream(m.getSources().split(",")).map(String::trim).filter(s -> !s.isBlank()).distinct().collect(Collectors.toList())
                : List.of("Student SIS Profile");
        return AIMessageDto.builder()
                .id(m.getId())
                .conversationId(m.getConversation().getId())
                .role(m.getRole())
                .content(m.getContent())
                .sources(sourcesList)
                .createdAt(m.getCreatedAt())
                .build();
    }
}

package com.example.student_management.service.ai;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Quiz tương tác: câu hỏi/đáp án do backend quản lý, chấm điểm xác định. */
@Service
public class AIQuizService {

    private static final long SESSION_TTL_MINUTES = 30;
    private static final int MAX_SESSIONS = 500;
    private static final Pattern ANSWER_PAIR = Pattern.compile("(\\d{1,2})\\s*[:.)-]?\\s*([abcd])\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNT = Pattern.compile("(\\d{1,2})");

    private record Question(String text, List<String> options, int correctIndex, String explanation) { }

    private static class QuizSession {
        final String id;
        final String topic;
        final String studentKey;
        final Long conversationId;
        final List<Question> questions;
        final Instant createdAt;

        QuizSession(String id, String topic, String studentKey, Long conversationId,
                    List<Question> questions, Instant createdAt) {
            this.id = id;
            this.topic = topic;
            this.studentKey = studentKey;
            this.conversationId = conversationId;
            this.questions = questions;
            this.createdAt = createdAt;
        }
    }

    private record QuizKey(String studentKey, Long conversationId) { }

    private final Map<QuizKey, QuizSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<Question>> banks = buildBanks();

    public boolean hasActiveQuiz(String studentKey, Long conversationId) {
        if (studentKey == null || conversationId == null) return false;
        cleanup();
        return sessions.containsKey(new QuizKey(studentKey, conversationId));
    }

    public String start(String studentKey, Long conversationId, String topic, String questionText, int requestedCount) {
        if (studentKey == null || conversationId == null) {
            return "Không xác định được phiên học; vui lòng mở lại hội thoại và thử lại.";
        }
        String resolvedTopic = resolveTopic(topic, questionText);
        List<Question> bank = banks.get(resolvedTopic);
        if (bank == null) {
            return "Mình hiện có ngân hàng câu hỏi xác định cho **Java Core**, **SQL** và **Spring Boot Security/JWT**. "
                    + "Vui lòng chọn một trong ba chủ đề này để mình tạo quiz và chấm điểm chính xác.";
        }
        int count = Math.min(Math.max(requestedCount > 0 ? requestedCount : 5, 1), bank.size());
        List<Question> picked = new ArrayList<>(bank.subList(0, count));
        Collections.shuffle(picked);
        String id = UUID.randomUUID().toString().substring(0, 8);
        sessions.put(new QuizKey(studentKey, conversationId),
                new QuizSession(id, resolvedTopic, studentKey, conversationId, picked, Instant.now()));
        cleanup();

        StringBuilder sb = new StringBuilder();
        sb.append("### Quiz: ").append(resolvedTopic).append(" (ID: `").append(id).append("`)\n\n");
        sb.append("Mình đã tạo ").append(count).append(" câu hỏi trắc nghiệm. **Chưa hiển thị đáp án đúng.**\n\n");
        for (int i = 0; i < picked.size(); i++) {
            Question q = picked.get(i);
            sb.append("**Câu ").append(i + 1).append(":** ").append(q.text()).append("\n");
            for (int j = 0; j < q.options().size(); j++) {
                sb.append("  ").append((char) ('A' + j)).append(". ").append(q.options().get(j)).append("\n");
            }
            sb.append("\n");
        }
        if (requestedCount > bank.size()) {
            sb.append("_Lưu ý: ngân hàng chủ đề này hiện có ").append(bank.size())
                    .append(" câu, nên mình tạo tối đa ").append(bank.size()).append(" câu._\n\n");
        }
        sb.append("Hãy trả lời theo dạng `1. A, 2. C, 3. B, ...`. Mình sẽ chấm điểm và giải thích từng câu sau khi bạn gửi đáp án.");
        return sb.toString();
    }

    public String grade(String studentKey, Long conversationId, String answerText) {
        if (studentKey == null || conversationId == null) return "Không xác định được phiên làm bài.";
        QuizKey key = new QuizKey(studentKey, conversationId);
        QuizSession session = sessions.get(key);
        if (session == null) {
            return "Mình không tìm thấy quiz đang mở trong hội thoại này. Hãy tạo quiz mới trước khi gửi đáp án.";
        }
        if (Instant.now().isAfter(session.createdAt.plus(Duration.ofMinutes(SESSION_TTL_MINUTES)))) {
            sessions.remove(key);
            return "Phiên quiz đã hết hạn sau 30 phút. Hãy tạo quiz mới để tiếp tục.";
        }
        Map<Integer, String> answers = parseAnswers(answerText);
        if (answers.isEmpty()) {
            return "Mình chưa đọc được đáp án. Hãy gửi theo dạng `1. A, 2. C, 3. B, ...` cho quiz ID `" + session.id + "`.";
        }

        sessions.remove(key);
        int correct = 0;
        List<String> weakTopics = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("### Kết quả quiz ").append(session.topic).append(" (ID: `").append(session.id).append("`)\n\n");
        for (int i = 0; i < session.questions.size(); i++) {
            Question q = session.questions.get(i);
            String answer = answers.get(i + 1);
            boolean right = answer != null && answer.charAt(0) - 'A' == q.correctIndex();
            if (right) correct++; else weakTopics.add("Câu " + (i + 1) + " - " + q.text());
            sb.append("**Câu ").append(i + 1).append(":** ").append(right ? "Đúng" : "Sai")
                    .append(" | Bạn chọn: ").append(answer == null ? "chưa trả lời" : answer)
                    .append(" | Đáp án đúng: ").append((char) ('A' + q.correctIndex())).append("\n");
            sb.append("_Giải thích:_ ").append(q.explanation()).append("\n\n");
        }
        double score = Math.round((correct * 100.0 / session.questions.size()) * 10.0) / 10.0;
        sb.append("**Tổng kết:** ").append(correct).append("/").append(session.questions.size())
                .append(" câu đúng, điểm ").append(score).append("/10.\n");
        if (weakTopics.isEmpty()) {
            sb.append("**Chủ đề cần ôn lại:** không có câu sai trong quiz này; bạn có thể yêu cầu mình tạo quiz ở mức khó hơn.\n");
        } else {
            sb.append("**Chủ đề cần ôn lại:**\n");
            for (String weak : weakTopics) sb.append("- ").append(weak).append("\n");
        }
        sb.append("\n_Đây là kết quả của riêng quiz ID `").append(session.id)
                .append("` trong hội thoại này; không phải điểm học vụ SIS._");
        return sb.toString();
    }

    public String abandon(String studentKey, Long conversationId) {
        if (studentKey != null && conversationId != null) {
            sessions.remove(new QuizKey(studentKey, conversationId));
        }
        return "Đã hủy quiz đang mở trong hội thoại này.";
    }

    private Map<Integer, String> parseAnswers(String text) {
        Map<Integer, String> result = new LinkedHashMap<>();
        if (text == null) return result;
        Matcher matcher = ANSWER_PAIR.matcher(text);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index >= 1 && index <= 20) result.put(index, matcher.group(2).toUpperCase(Locale.ROOT));
        }
        return result;
    }

    private String resolveTopic(String topic, String questionText) {
        String combined = ((topic == null ? "" : topic) + " " + (questionText == null ? "" : questionText))
                .toLowerCase(Locale.ROOT);
        if (combined.contains("security") || combined.contains("jwt") || combined.contains("spring")) {
            return "Spring Boot Security và JWT";
        }
        if (combined.contains("java")) return "Java Core";
        if (combined.contains("sql") || combined.contains("database") || combined.contains("co so du lieu")
                || combined.contains("cơ sở dữ liệu")) return "SQL";
        return null;
    }

    private int requestedCount(String text) {
        if (text == null) return 5;
        Matcher matcher = COUNT.matcher(text);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value >= 1 && value <= 30) return value;
        }
        return 5;
    }

    public int parseRequestedCount(String text) { return requestedCount(text); }

    private void cleanup() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(e -> e.getValue().createdAt.isBefore(now.minus(Duration.ofMinutes(SESSION_TTL_MINUTES))));
        while (sessions.size() > MAX_SESSIONS) {
            String oldestKey = null;
            Instant oldest = Instant.MAX;
            for (Map.Entry<QuizKey, QuizSession> e : sessions.entrySet()) {
                if (e.getValue().createdAt.isBefore(oldest)) {
                    oldest = e.getValue().createdAt;
                    oldestKey = e.getKey().studentKey() + "|" + e.getKey().conversationId();
                }
            }
            if (oldestKey == null) break;
            for (QuizKey k : new ArrayList<>(sessions.keySet())) {
                if ((k.studentKey() + "|" + k.conversationId()).equals(oldestKey)) {
                    sessions.remove(k);
                    break;
                }
            }
        }
    }

    private Map<String, List<Question>> buildBanks() {
        Map<String, List<Question>> banks = new LinkedHashMap<>();
        List<Question> java = new ArrayList<>();
        java.add(new Question("Từ khóa nào dùng để khai báo một lớp là abstract trong Java?",
                List.of("abstract", "final", "static", "interface"), 0,
                "abstract dùng cho lớp không thể khởi tạo trực tiếp và có thể chứa phương thức trừu tượng."));
        java.add(new Question("Đặc tính đóng gói (encapsulation) trong Java được thể hiện chủ yếu bằng cách nào?",
                List.of("Dùng private field và public getter/setter", "Dùng static method", "Dùng extends", "Dùng instanceof"), 0,
                "Đóng gói là ẩn trạng thái bên trong và chỉ cho phép truy cập qua phương thức công khai."));
        java.add(new Question("Interface trong Java (trước Java 8) thường chứa gì?",
                List.of("Phương thức abstract", "Trường dữ liệu thường", "Constructor", "Thân phương thức đầy đủ"), 0,
                "Interface truyền thống định nghĩa hợp đồng phương thức; lớp hiện thực phải implement."));
        java.add(new Question("Cấu trúc nào trong Java cho phép lưu nhiều phần tử cùng kiểu và truy cập theo chỉ số?",
                List.of("ArrayList", "TreeSet", "HashMap", "LinkedList chỉ dùng làm queue"), 0,
                "ArrayList triển khai List, truy cập ngẫu nhiên qua get(index) hiệu quả."));
        java.add(new Question("Phương thức nào của String trả về độ dài chuỗi?",
                List.of("length()", "size()", "count()", "getLength()"), 0,
                "String có length(); size() là của Collection."));
        java.add(new Question("Từ khóa nào ngăn một lớp bị kế thừa?",
                List.of("final", "abstract", "transient", "volatile"), 0,
                "final class không thể được extends."));
        java.add(new Question("Ngoại lệ nào là checked exception trong Java?",
                List.of("IOException", "NullPointerException", "IllegalArgumentException", "ClassCastException"), 0,
                "IOException phải được khai báo hoặc xử lý; các ngoại lệ còn lại là unchecked."));
        java.add(new Question("Câu lệnh nào dùng để tạo luồng mới bằng cách triển khai Runnable?",
                List.of("new Thread(runnable).start()", "new Thread(runnable).run()", "runnable.start()", "Thread.create(runnable)"), 0,
                "Phải gọi start() để JVM tạo luồng mới; gọi run() chỉ thực thi trên luồng hiện tại."));
        java.add(new Question("HashMap lưu dữ liệu theo cặp gì?",
                List.of("key-value", "index-value", "value-only", "key-key"), 0,
                "HashMap ánh xạ key tới value và cho phép tra cứu theo key."));
        java.add(new Question("Generic trong Java chủ yếu giải quyết vấn đề gì?",
                List.of("An toàn kiểu khi biên dịch", "Tăng tốc độ I/O", "Ẩn phương thức", "Thay thế exception"), 0,
                "Generic giúp trình biên dịch kiểm tra kiểu và giảm ép kiểu không an toàn."));
        java.add(new Question("Phép toán == khi so sánh hai đối tượng String trong Java thường so sánh điều gì?",
                List.of("Tham chiếu", "Nội dung ký tự", "Độ dài", "Mã băm"), 0,
                "== so sánh tham chiếu; so sánh nội dung phải dùng equals()."));
        java.add(new Question("Từ khóa nào dùng để chờ điều kiện và giải phóng khóa trên đối tượng?",
                List.of("wait()", "sleep()", "yield()", "join()"), 0,
                "wait() giải phóng monitor của đối tượng; sleep() thì không."));
        banks.put("Java Core", java);

        List<Question> sql = new ArrayList<>();
        sql.add(new Question("Lệnh SQL nào dùng để lọc bản ghi trước khi nhóm?",
                List.of("WHERE", "HAVING", "ORDER BY", "GROUP BY"), 0,
                "WHERE lọc từng dòng trước GROUP BY; HAVING lọc nhóm sau khi nhóm."));
        sql.add(new Question("JOIN nào trả về các dòng có giá trị khớp ở cả hai bảng?",
                List.of("INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL OUTER JOIN"), 0,
                "INNER JOIN chỉ giữ dòng có khóa khớp ở cả hai bảng."));
        sql.add(new Question("Khóa ngoại (foreign key) dùng để làm gì?",
                List.of("Tham chiếu tới khóa chính của bảng khác", "Tăng tốc sắp xếp", "Mã hóa dữ liệu", "Tạo chỉ mục toàn văn"), 0,
                "Foreign key bảo đảm toàn vẹn tham chiếu giữa các bảng."));
        sql.add(new Question("Hàm SQL nào đếm số dòng (bao gồm cả NULL riêng lẻ)?",
                List.of("COUNT(*)", "SUM(*)", "AVG(*)", "MAX(*)"), 0,
                "COUNT(*) đếm số dòng; COUNT(cột) bỏ qua giá trị NULL."));
        sql.add(new Question("Mệnh đề nào sắp xếp kết quả theo cột?",
                List.of("ORDER BY", "GROUP BY", "WHERE", "LIMIT"), 0,
                "ORDER BY sắp xếp; LIMIT chỉ giới hạn số dòng trả về."));
        sql.add(new Question("Chỉ mục (index) trong CSDL giúp ích chủ yếu cho việc gì?",
                List.of("Tăng tốc truy vấn tìm kiếm", "Giảm dung lượng lưu trữ", "Bỏ ràng buộc khóa", "Tự động sao lưu"), 0,
                "Index tạo cấu trúc tra cứu nhanh nhưng tốn thêm dung lượng và làm chậm ghi."));
        banks.put("SQL", sql);

        List<Question> security = new ArrayList<>();
        security.add(new Question("JWT thường gồm bao nhiêu phần được phân tách bằng dấu chấm?",
                List.of("3", "2", "4", "5"), 0,
                "JWT gồm header.payload.signature."));
        security.add(new Question("Trong Spring Security, annotation nào kiểm tra vai trò trước khi gọi phương thức?",
                List.of("@PreAuthorize", "@Transactional", "@Autowired", "@GetMapping"), 0,
                "@PreAuthorize hoạt động cùng method security để kiểm tra biểu thức như hasRole('STUDENT')."));
        security.add(new Question("Stateless session trong Spring Security thường dùng cơ chế nào để xác thực mỗi request?",
                List.of("Token (ví dụ JWT) trong header Authorization", "HttpSession bắt buộc", "Cookie phiên mặc định", "Basic auth cho mọi request"), 0,
                "Với stateless, mỗi request mang token tự chứa thông tin xác thực."));
        security.add(new Question("Payload của JWT có nên chứa mật khẩu người dùng không?",
                List.of("Không, vì payload chỉ được mã hóa dạng base64 và có thể đọc được", "Có, để tiện xác thực", "Chỉ khi dùng HTTPS", "Chỉ khi token ngắn"), 0,
                "Payload JWT không phải là nơi lưu secret; chỉ nên chứa claim cần thiết như subject/role."));
        security.add(new Question("Mục đích chính của CSRF protection là gì?",
                List.of("Ngăn request trái phép từ trang web khác", "Mã hóa mật khẩu", "Tăng tốc API", "Kiểm tra định dạng email"), 0,
                "CSRF protection ngăn kẻ tấn công lợi dụng session/credentials của nạn nhân để gửi request."));
        banks.put("Spring Boot Security và JWT", security);
        return banks;
    }
}

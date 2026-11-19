# Báo cáo nâng cấp Đợt 4 – AI Tutor và định tuyến ý định kết hợp

## 1. Phạm vi thực hiện

Đợt 4 mở rộng trợ lý học tập AI bằng chế độ **AI Tutor**, hỗ trợ giải thích kiến thức, tạo bài tập, review code, quiz, kiểm tra đáp án và tài liệu ôn tập.

Điểm quan trọng của Đợt 4 là tách hai loại nhu cầu:

- **Tutor thuần**: câu hỏi kiến thức/phương pháp học, không dùng dữ liệu học vụ cá nhân.
- **SIS + Tutor**: câu hỏi vừa yêu cầu dữ liệu thật của sinh viên vừa cần giải thích/ôn tập, ví dụ “Giải thích điểm GPA của tôi”.

Các nguyên tắc được giữ nguyên:

- Không tạo chatbot thứ hai; tái dùng `StudentAIChat` và các API chat hiện có.
- Không thêm endpoint mới cho AI Tutor.
- Không nhận `studentId` từ client; danh tính lấy từ JWT.
- Không trộn dữ liệu điểm/tín chỉ thật vào ví dụ giả định của tutor.
- Không bịa nguồn tài liệu hoặc trích dẫn quy chế khi không có văn bản gốc.
- Không thêm dependency mới và không chuyển sang streaming API.

## 2. Backend

### 2.1 `AITutorService`

File: `backend/Student_Information_System/src/main/java/com/example/student_management/service/ai/AITutorService.java`

Service mới này chịu trách nhiệm lập kế hoạch trả lời tutor thuần bằng rule-based logic:

- `TutorTask`: `EXPLANATION`, `EXERCISE`, `CODE_REVIEW`, `QUIZ`, `ANSWER_EVALUATION`, `STUDY_MATERIAL`, `FOLLOW_UP`, `GENERAL`.
- `Level`: `BASIC`, `INTERMEDIATE`, `ADVANCED`.
- `TutorPlan`: chứa task, level, topic và instructions.
- `plan(question, conversationSummary)`: chuẩn hóa tiếng Việt không dấu, nhận diện task, mức độ và chủ đề.
- `isTutorQuestion(question)`: nhận diện các cụm như giải thích, khái niệm, bài tập, review code, debug, quiz, đáp án, tài liệu, SQL, Java, Python, thuật toán, cơ sở dữ liệu.
- `mentionsSisData(question)`: nhận diện cụm dữ liệu học vụ như “của tôi”, GPA, điểm, tín chỉ, học kỳ, môn học, đăng ký, lịch học, lịch thi, tiến độ.
- `buildFallback(plan, question, conversationSummary)`: tạo fallback theo từng task, luôn khẳng định đây là nội dung tham khảo, không phải dữ liệu điểm/học vụ cá nhân.

Instructions gửi cho provider luôn yêu cầu: trả lời tiếng Việt, có cấu trúc, nêu định nghĩa/cơ chế, ví dụ, lỗi thường gặp; không bịa dữ liệu SIS và không khẳng định đã thực hiện nghiệp vụ.

### 2.2 `AIIntentService`

File: `backend/Student_Information_System/src/main/java/com/example/student_management/service/ai/AIIntentService.java`

Service intent được mở rộng với các intent mới:

- `COMBINED_SIS_TUTOR`
- `TUTOR_EXPLANATION`
- `TUTOR_EXERCISE`
- `TUTOR_CODE_REVIEW`
- `TUTOR_QUIZ`
- `TUTOR_ANSWER_EVALUATION`
- `TUTOR_STUDY_MATERIAL`
- `TUTOR_FOLLOW_UP`

Bổ sung:

- `IntentAnalysis`: chứa `Intent` và `AITutorService.TutorPlan`.
- `analyze(question, hasConversationContext)`: trả intent kèm tutor plan nếu là câu hỏi tutor.
- `detectSisIntent(...)`: giữ nguyên khả năng nhận diện GPA, tín chỉ, điểm, môn học, học kỳ, đăng ký, tiến độ và follow-up.
- `isTutorIntent(intent)`: dùng để quyết định vai trò tutor trong system context.

Thứ tự nhận diện:

1. Câu hỏi ngoài phạm vi: thời tiết, giá vàng, chính trị, tin tức, giải trí, hack, mật khẩu, API key.
2. Vừa là tutor vừa nhắc dữ liệu SIS → `COMBINED_SIS_TUTOR`.
3. Tutor thuần → map `TutorTask` sang intent tutor tương ứng.
4. Follow-up có ngữ cảnh → `FOLLOW_UP_QUESTION`.
5. Còn lại → intent SIS cũ hoặc `GENERAL_ACADEMIC_QUESTION`.

### 2.3 `AIChatServiceImpl`

File: `backend/Student_Information_System/src/main/java/com/example/student_management/service/ai/impl/AIChatServiceImpl.java`

Thay đổi chính:

- `DataBundle` bổ sung `tutorPlan` và `tutorInstructions`.
- `fetchDataForIntent`:
  - Tutor thuần trả sớm, chỉ gắn nguồn `AI Tutor Knowledge`, không gọi `getStudentProfile`, `getPublishedGrades`, `getAllEnrollments` hay tool SIS khác.
  - `COMBINED_SIS_TUTOR` vẫn giữ tutor instructions, sau đó dùng `detectSisIntent(question, true)` để lấy đúng dữ liệu SIS cần thiết.
- `buildSystemContext`:
  - Vai trò tutor/SIS được phân biệt rõ.
  - Với tutor thuần, thêm quy tắc bắt buộc: chỉ dùng kiến thức nền tảng và ngữ cảnh hội thoại tutor, không dùng dữ liệu điểm/tín chỉ/lịch học.
  - Với câu hỏi kết hợp, thêm quy tắc dùng dữ liệu SIS cho phần học vụ và kiến thức nền tảng cho phần giải thích.
- `buildFallbackAnswer`:
  - Tutor thuần dùng `tutorService.buildFallback(...)`.
  - Câu hỏi kết hợp nối thêm phần tutor fallback sau phần dữ liệu SIS thật.
- `buildTutorConversationSummary`: tóm tắt tối đa 4 tin nhắn gần nhất, giới hạn 700 ký tự, chỉ dùng cho tutor và không đính kèm dữ liệu cá nhân.
- Giữ nguyên các quy tắc cũ: không bịa điểm/tín chỉ/học phần/lịch, nói rõ khi thiếu dữ liệu, phân biệt “dữ liệu thực tế” và “nhận xét AI”, không khẳng định đã đăng ký/sửa điểm.

### 2.4 Nguồn dữ liệu và an toàn

- Tutor thuần: nguồn là `AI Tutor Knowledge` hoặc nội dung do provider tạo từ kiến thức nền tảng.
- Câu hỏi kết hợp: nguồn gồm `AI Tutor Knowledge` và các nguồn SIS tương ứng như `Student SIS Profile`, `Published Grades & Academic Records`, `Enrollment Records`, `Exam Schedule`.
- Không có dữ liệu SIS nào được fetch cho tutor thuần.
- Không có API nào nhận `studentId` từ request; mọi dữ liệu cá nhân đều đi qua `username` trong JWT và kiểm tra ownership của conversation.

## 3. Frontend

Không bổ sung component chat mới. Các điều chỉnh nhỏ nằm trong khung chat hiện có:

- `frontend/src/components/chat/ChatMessages.jsx`: thêm gợi ý câu hỏi tutor và follow-up theo ngữ cảnh.
- `frontend/src/components/chat/ChatInput.jsx`: cập nhật placeholder/gợi ý người dùng có thể hỏi giải thích, bài tập, quiz hoặc review code.
- Render nội dung AI bằng React elements an toàn, không dùng `dangerouslySetInnerHTML`.

## 4. Test backend

Các test được thêm/cập nhật:

- `AITutorServiceTest`
  - `plansTutorTasksWithoutSisData`: nhận diện đúng explanation, exercise, code review, quiz, answer evaluation, study material.
  - `distinguishesTutorAndSisQuestions`: phân biệt câu tutor thuần và câu có dữ liệu SIS.
  - `fallbackDoesNotMentionPersonalSisNumbers`: fallback tutor không chứa mã SV hay GPA cá nhân.
- `AIIntentServiceTest`
  - `detectsPureTutorIntents`
  - `detectsCombinedSisTutorIntent`
  - `keepsSisIntents`
  - `analysisAttachesTutorPlanForTutorIntents`
- `AIChatServicePhase4Test`
  - `pureTutorQuestionDoesNotFetchSisData`: verify không gọi tool profile/grades/enrollments và context chứa hướng dẫn tutor.
  - `combinedQuestionKeepsSisDataAndTutorInstructions`: verify vẫn lấy dữ liệu điểm, context có cả tutor instructions và dữ liệu SIS được phép dùng.

Lệnh kiểm tra:

- `.\gradlew.bat test --no-daemon --tests "*AITutorServiceTest" --tests "*AIIntentServiceTest" --tests "*AIChatServicePhase4Test"` — passed.
- `.\gradlew.bat test --no-daemon` — `BUILD SUCCESSFUL`.

## 5. Giới hạn đã biết

- Phân loại tutor là rule-based theo từ khóa tiếng Việt đã chuẩn hóa, không phải mô hình phân loại riêng.
- Tutor thuần dùng kiến thức nền tảng; không tự tra cứu tài liệu bên ngoài và không tạo trích dẫn nếu provider không trả nguồn thật.
- Câu hỏi kết hợp chỉ dùng dữ liệu SIS đã được hệ thống lấy theo intent; không tự suy diễn point/credit từ nội dung tự do.
- Không có streaming nên chưa có stop-generation button.
- `prompt_AI.md` không có trong workspace khi viết lại báo cáo này, nên một số chi tiết yêu cầu gốc không thể đối chiếu trực tiếp; nội dung bám theo implementation và test hiện có.

## 6. Kết luận

Đợt 4 đã biến trợ lý SIS từ chatbot tra cứu học vụ sang trợ lý học tập hai chế độ: tutor thuần không chạm dữ liệu cá nhân và chế độ kết hợp vận dụng dữ liệu SIS thật để giải thích kết quả học tập. Việc tách intent và kiểm soát luồng fetch dữ liệu giúp giảm rủi ro lộ PII trong các câu hỏi học kiến thức. Các test chuyên biệt cho tutor, intent và chat orchestration đều passed.

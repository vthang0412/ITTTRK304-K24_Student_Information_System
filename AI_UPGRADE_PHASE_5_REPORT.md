# Báo cáo hoàn thiện Đợt 5 – Giao diện, báo cáo và tối ưu AI

## 1. Phạm vi thực hiện

Đợt 5 tập trung hoàn thiện trải nghiệm người dùng cho các tính năng AI đã xây dựng ở các đợt trước, bổ sung luồng phản hồi, đổi tên hội thoại, báo cáo học tập/export Excel và các trang quản lý AI dành riêng cho sinh viên.

Các nguyên tắc được giữ nguyên:

- Không tạo chatbot thứ hai; tái dùng khung chat nổi `StudentAIChat` hiện có.
- Không nhận `studentId` từ client; mọi API AI lấy danh tính từ JWT.
- Chỉ dùng dữ liệu SIS thật, có kiểm tra quyền `STUDENT` và ownership.
- Không dùng `dangerouslySetInnerHTML`; nội dung AI được render bằng React elements.
- Không thêm dependency mới; dùng recharts và Apache POI có sẵn.
- Không có streaming API nên stop-generation button không áp dụng.
- Không có PDF library trong dự án; export báo cáo dùng Excel `.xlsx`.

## 2. Backend

### 2.1 Migration và schema

Thêm `V17__ai_chat_feedback_and_indexes.sql`:

- `ai_messages.feedback VARCHAR(20) NULL`
- Index `idx_ai_log_student_created (student_id, created_at)` cho `ai_usage_logs`
- Index `idx_ai_conv_student_updated (student_id, updated_at)` cho `ai_conversations`

Cập nhật `FlywayMySqlIntegrationTest` để kỳ vọng phiên bản Flyway là `17`.

### 2.2 Chat feedback và đổi tên hội thoại

Thêm DTO:

- `AIRenameConversationRequest`
- `AIMessageFeedbackRequest`

Cập nhật:

- `AIMessage`, `AIMessageDto`: trường `feedback`
- `AIMessageRepository`: `findByIdAndConversationId`
- `AIChatService`: `renameConversation`, `setMessageFeedback`, `getAcademicReport`
- `AIChatServiceImpl`:
  - Đổi tên có kiểm tra quyền sở hữu hội thoại.
  - Feedback chỉ áp dụng cho tin nhắn `ASSISTANT`, giá trị chuẩn hoá `HELPFUL`/`UNHELPFUL`.
- `AIChatController`:
  - `PUT /api/student/ai/chat/conversations/{id}/title`
  - `PUT /api/student/ai/chat/messages/{id}/feedback`

### 2.3 Báo cáo học tập và export

Thêm:

- `GET /api/student/ai/report`
- `GET /api/student/ai/report/excel`

Báo cáo được tạo trực tiếp từ `AIChatToolsService` và dữ liệu SIS thật:

- GPA hệ 10, GPA hệ 4
- Tín chỉ tích lũy, tín chỉ đang học
- Số môn đạt/chưa đạt
- Kết quả theo học kỳ
- Học phần điểm thấp
- Mục tiêu cải thiện
- Học phần đang theo học
- Nguồn: `Academic Records`, `Enrollments`

Excel export dùng Apache POI `XSSFWorkbook`, content type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, filename `AI_Academic_Report.xlsx`. Cột xếp loại của học phần điểm thấp dùng key thực tế `classification`.

### 2.4 Provider

Chuyển Gemini API key từ query parameter sang header `x-goog-api-key`, tránh lộ key trong URL/log.

### 2.5 Test backend

Thêm/cập nhật:

- `AIChatServicePhase5Test`
- `AIChatServicePhase4Test`
- `AITutorServiceTest`
- `AIIntentServiceTest`
- `FlywayMySqlIntegrationTest`

Lệnh kiểm tra:

- `.\gradlew.bat test --no-daemon` — `BUILD SUCCESSFUL`

## 3. Frontend

### 3.1 API wrappers

Cập nhật `frontend/src/api/index.js`:

- `aiChatApi.renameConversation`
- `aiChatApi.sendFeedback`
- `aiChatApi.getReport`
- `aiChatApi.downloadReportExcel`
- `aiInsightsApi.getInsights`, `refreshInsights`
- `aiCourseAdvisorApi.getRecommendations`, `recommend`
- `aiStudyPlanApi.getAll`, `getDetail`, `create`, `update`, `remove`, `updateItem`

### 3.2 AI Chat

Các cải tiến đã áp dụng:

- Đổi tên hội thoại inline trong lịch sử.
- Gửi feedback `HELPFUL`/`UNHELPFUL` cho tin nhắn assistant.
- Copy câu trả lời và copy từng code block.
- Render Markdown an toàn bằng React: heading, danh sách, bảng, links an toàn, inline code, code block.
- Gợi ý câu hỏi ban đầu và gợi ý theo ngữ cảnh sau câu trả lời.
- Thinking indicator trong lúc chờ AI.
- Timestamp với tooltip ngày giờ đầy đủ.
- Smart auto-scroll: chỉ tự cuộn khi người dùng đang ở gần cuối hoặc vừa gửi tin nhắn.
- Chặn gửi trùng khi đang `sending`; xoá tin nhắn tạm khi gửi thất bại để retry không tạo bản trùng.
- Sự kiện `sis-ai:open-chat` để các trang AI mở widget chat.

CSS bổ sung trong `chat.css` cho các thành phần trên và responsive dưới 480px.

### 3.3 Trang AI mới

Thêm các trang:

- `frontend/src/pages/student/AIDashboard.jsx`
  - Tổng quan GPA/tín chỉ, xu hướng học kỳ, nhận xét AI, kế hoạch gần đây, hoạt động chat, truy cập nhanh.
- `frontend/src/pages/student/AIInsights.jsx`
  - Biểu đồ GPA/tín chỉ, nhận xét, khuyến nghị, bảng học phần điểm thấp, làm mới phân tích.
- `frontend/src/pages/student/AICourseAdvisor.jsx`
  - Form tuỳ chỉnh định hướng/tín chỉ/ghi chú, danh sách môn nên học và chưa phù hợp kèm điều kiện tiên quyết/lớp học phần.
- `frontend/src/pages/student/AIStudyPlanner.jsx`
  - Tạo/xoá kế hoạch, đổi trạng thái kế hoạch, xem nhiệm vụ, đánh dấu hoàn thành/bỏ qua.
- `frontend/src/pages/student/AIReports.jsx`
  - Xem báo cáo học tập và export Excel.
- `frontend/src/pages/student/ai-pages.css`
  - Style dùng chung cho các trang AI, theo design token hiện có.

Đăng ký route trong `App.jsx`:

- `/student/ai-dashboard`
- `/student/ai-insights`
- `/student/ai-course-advisor`
- `/student/ai-study-planner`
- `/student/ai-reports`

Thêm nhóm menu `Không gian AI` trong `Layout.jsx` cho role `STUDENT`.

### 3.4 Kiểm tra frontend

Lệnh kiểm tra:

- `npm run lint` — không có lỗi; chỉ có warning (phần lớn có sẵn trong codebase).
- `npm run build` — build thành công; có warning kích thước chunk lớn.

## 4. Giới hạn đã biết

- Không export PDF do dự án không có dependency PDF; dùng Excel thay thế.
- Không có stop-generation button vì backend chưa có streaming.
- Biểu đồ chỉ dùng cho dữ liệu có sẵn; nội dung AI dạng text vẫn render dưới dạng Markdown an toàn.
- `prompt_AI.md` không có trong workspace khi thực hiện, nên một số chi tiết yêu cầu gốc không thể đối chiếu trực tiếp; triển khai bám theo mô tả Đợt 5 đã biết và cấu trúc dự án hiện có.
- Full backend test chạy thành công nhưng cần MySQL/Flyway integration environment riêng nếu muốn kiểm tra migration trên database thật.

## 5. Kết luận

Đợt 5 đã hoàn thiện luồng tương tác AI từ chat đến các trang quản lý/báo cáo, bổ sung phản hồi người dùng, export báo cáo và tối ưu hiệu năng truy vấn. Toàn bộ backend tests và frontend build đều passed; hệ thống sẵn sàng cho kiểm thử tay trên môi trường có dữ liệu sinh viên thật.

# Báo cáo nâng cấp Đợt 3 – Phân tích học tập, tư vấn môn học và kế hoạch học tập AI

## 1. Phạm vi thực hiện

Đợt 3 bổ sung ba nhóm tính năng AI cho sinh viên:

- **AI Learning Insights**: phân tích GPA, tín chỉ, xu hướng theo học kỳ, học phần điểm thấp và đề xuất cải thiện.
- **AI Course Advisor**: tư vấn môn học cho học kỳ tiếp theo dựa trên điều kiện tiên quyết, lớp học phần, chỗ trống và xung đột lịch.
- **AI Study Planner**: tạo và quản lý kế hoạch học tập cá nhân theo tuần, kèm nhiệm vụ và lời khuyên từ AI.

 Đợt này tập trung vào backend/API. Giao diện các trang AI được hoàn thiện ở Đợt 5.

Các nguyên tắc được giữ nguyên:

- Không nhận `studentId` từ client; danh tính được lấy từ `Authentication.getName()` qua JWT.
- Chỉ truy xuất dữ liệu SIS của chính sinh viên đang đăng nhập.
- Mọi controller/service đều kiểm tra role `STUDENT` và sự tồn tại của hồ sơ sinh viên.
- Dữ liệu phân tích là dữ liệu thật từ SIS; phần nhận xét AI có fallback nội bộ nếu provider ngoài không khả dụng.
- Có giới hạn tần suất theo `app.ai.rate-limit-per-hour` (mặc định 60 lượt/giờ) và ghi log vào `ai_usage_logs`.

## 2. Backend

### 2.1 Schema và hạ tầng dùng chung

Không thêm migration mới trong Đợt 3. Các bảng cần thiết đã có trong `V6__create_ai_assistant_tables.sql`:

- `ai_study_plans`
- `ai_study_plan_items`
- `ai_usage_logs`

Các bảng này dùng khóa ngoại tới `students` và `courses`, bảo đảm xóa dữ liệu theo sinh viên và không lưu nhiệm vụ mồ côi.

### 2.2 AI Learning Insights

Files chính:

- `AIInsightsController.java`
- `AIInsightsService.java`, `AIInsightsServiceImpl.java`
- `AIInsightsDto.java`, `AISemesterInsightDto.java`, `AIRecommendationDto.java`

Endpoints:

- `GET /api/student/ai/insights`
- `POST /api/student/ai/insights/refresh`

Logic đã triển khai:

- Lấy điểm đã công bố qua `GradebookService.getStudentPublishedGrades(username)` và danh sách đăng ký qua `EnrollmentRepository`.
- Tính GPA hệ 10, GPA hệ 4, tín chỉ tích lũy, tín chỉ đang học, số môn đạt/chưa đạt và xếp loại.
- Ngưỡng xử lý: đạt `>= 4.00`, điểm thấp `< 6.50`, điểm cao `>= 8.50`, xu hướng thay đổi `>= 0.20`.
- Nhóm điểm theo học kỳ, tính GPA từng kỳ và xác định xu hướng `UP`, `DOWN`, `STABLE`, `FIRST`.
- Sinh đề xuất theo dữ liệu thật: học lại môn chưa đạt, cải thiện môn điểm thấp, cảnh báo xu hướng giảm, ghi nhận thế mạnh và mục tiêu GPA.
- Cache kết quả theo `studentId` bằng `ConcurrentHashMap`; `refresh` xóa cache và kiểm tra rate limit.
- Gọi `AIProviderService.generateExternalAnalysis` để tạo nhận xét ngắn; nếu thất bại dùng fallback tiếng Việt được dựng từ số liệu thật.
- Ghi log `featureType = INSIGHTS` kèm provider, model, latency và trạng thái.

### 2.3 AI Course Advisor

Files chính:

- `AICourseAdvisorController.java`
- `AICourseAdvisorService.java`, `AICourseAdvisorServiceImpl.java`
- `CourseAdvisorRequestDto.java`, `CourseAdvisorResponseDto.java`, `CourseRecommendationItemDto.java`, `CourseSectionOptionDto.java`

Endpoints:

- `GET /api/student/ai/course-advisor`
- `POST /api/student/ai/course-advisor/recommend`

Logic đã triển khai:

- Phân tích điểm công bố để xác định môn đã đạt, môn chưa đạt và tín chỉ tích lũy.
- Loại bỏ môn đã hoàn thành, môn đang học, lớp `COMPLETED`/`CANCELLED`.
- Dùng ma trận `PREREQUISITES` cho các môn CNTT trọng yếu như `INT3401`, `INT3303`, `INT3405`, `INT3507`.
- Phân loại đề xuất theo trạng thái: `RECOMMENDED`, `MUST_RETAKE`, `PREREQUISITE_NOT_MET`, `SCHEDULE_CONFLICT`, `SECTIONS_FULL`, `ALREADY_COMPLETED`, `CURRENTLY_ENROLLED`.
- Với mỗi lớp học phần, tính sức chứa, số sinh viên đã đăng ký, chỗ trống, giảng viên, lịch học và trạng thái mở lớp.
- Dùng chung `ScheduleParser` với nghiệp vụ đăng ký để phát hiện xung đột lịch giữa lớp đề xuất và các lớp đang học.
- Ưu tiên `HIGH` cho môn phải học lại và môn khớp định hướng: mobile, AI, web, bảo mật, phần mềm.
- Giới hạn khối lượng đăng ký khuyến nghị là `MAX_CREDITS_PER_SEMESTER = 24`.
- Có cache theo sinh viên, rate limit cho request cá nhân hóa và log `featureType = ADVISOR`.
- Luôn trả `disclaimer`: hệ thống chỉ tư vấn, không tự động đăng ký hoặc hủy học phần.

### 2.4 AI Study Planner

Files chính:

- `AIStudyPlanController.java`
- `AIStudyPlanService.java`, `AIStudyPlanServiceImpl.java`
- `CreateStudyPlanRequestDto.java`, `UpdateStudyPlanRequestDto.java`, `UpdateStudyPlanItemRequestDto.java`, `StudyPlanDto.java`, `StudyPlanItemDto.java`

Endpoints:

- `POST /api/student/ai/study-plans`
- `GET /api/student/ai/study-plans`
- `GET /api/student/ai/study-plans/{id}`
- `PUT /api/student/ai/study-plans/{id}`
- `DELETE /api/student/ai/study-plans/{id}`
- `PATCH /api/student/ai/study-plans/{id}/items/{itemId}`

Logic đã triển khai:

- Kiểm tra rate limit trước khi tạo kế hoạch.
- Chuẩn hóa ngày bắt đầu/kết thúc; từ chối `endDate < startDate`.
- Môn trọng tâm lấy từ `focusCourseIds`, sau đó đến các môn đang theo học, cuối cùng fallback 3 môn đầu tiên.
- Hỗ trợ hai chế độ tạo nhiệm vụ:
  - Nhiệm vụ thủ công từ `manualItems`.
  - Tự động phân bổ theo từng ngày, luân phiên môn học với 7 mẫu nhiệm vụ và thời lượng mặc định 90 phút hoặc `targetHoursPerDay * 60`.
- Trạng thái kế hoạch: `ACTIVE`, `COMPLETED`, `ARCHIVED`.
- Trạng thái nhiệm vụ: `PENDING`, `COMPLETED`, `SKIPPED`.
- Tính `totalTasks`, `completedTasks`, `completionRate` và `totalStudyMinutes`.
- Tự động chuyển kế hoạch sang `COMPLETED` khi mọi nhiệm vụ đều hoàn thành.
- Mọi thao tác chi tiết dùng `findByIdAndStudentId`, trả `NotFoundException` nếu kế hoạch không thuộc sinh viên hiện tại.
- Gọi AI tạo lời khuyên ngắn; fallback nhắc dùng Pomodoro nếu provider ngoài không có kết quả.
- Ghi log `featureType = PLANNER`.

### 2.5 Test backend

Các test được thêm/cập nhật:

- `AIInsightsServiceTest`
  - Tính đúng GPA, tín chỉ, xu hướng và môn điểm thấp.
  - Xử lý trường hợp chưa có điểm công bố.
  - Từ chối tài khoản không phải `STUDENT`.
- `AICourseAdvisorServiceTest`
  - Môn đủ điều kiện thì đề xuất.
  - Môn thiếu tiên quyết thì không đề xuất.
  - Môn từng rớt được ưu tiên `MUST_RETAKE`.
  - Xung đột lịch chuyển sang `SCHEDULE_CONFLICT`.
  - Định hướng nghề nghiệp nâng priority.
  - Từ chối role không phải `STUDENT`.
- `AIStudyPlanServiceTest`
  - Tạo kế hoạch và tự sinh nhiệm vụ.
  - Từ chối ngày kết thúc trước ngày bắt đầu.
  - Kiểm tra ownership khi xem chi tiết.
  - Cập nhật trạng thái nhiệm vụ và completion rate.
  - Tự hoàn thành kế hoạch khi tất cả nhiệm vụ xong.
  - Xóa kế hoạch và từ chối role không phải `STUDENT`.

Lệnh kiểm tra:

- `.\gradlew.bat test --no-daemon` — `BUILD SUCCESSFUL`

## 3. Frontend

Đợt 3 không mở route hoặc trang AI mới. Frontend chỉ tiêu thụ các API này sau khi `frontend/src/api/index.js` được bổ sung các wrapper `aiInsightsApi`, `aiCourseAdvisorApi` và `aiStudyPlanApi` ở Đợt 5.

Việc render dữ liệu AI được thực hiện an toàn bằng React elements, không dùng `dangerouslySetInnerHTML`.

## 4. Giới hạn đã biết

- Nhận xét AI là văn bản ngắn gợi ý; số liệu quyết định vẫn đến từ SIS, không phải từ mô hình ngôn ngữ.
- Cache insights/advisor là in-memory theo `studentId`; restart backend sẽ mất cache.
- Ma trận điều kiện tiên quyết là cấu hình nội bộ cho chương trình CNTT; cần cập nhật khi chương trình đào tạo thay đổi.
- ScheduleParser xử lý lịch theo định dạng hiện có của hệ thống; lịch tự do không parse được sẽ không tạo cảnh báo xung đột.
- `prompt_AI.md` không có trong workspace khi viết lại báo cáo này, nên một số diễn đạt gốc không thể đối chiếu trực tiếp; nội dung bám theo implementation và test hiện có.

## 5. Kết luận

Đợt 3 đã hoàn thiện bộ ba dịch vụ AI hỗ trợ học tập: phân tích tiến độ, tư vấn học phần và lập kế hoạch học tập. Tất cả luồng đều lấy dữ liệu thật từ SIS, kiểm tra quyền sinh viên, có fallback khi provider không khả dụng và có log sử dụng. Backend test chạy thành công, tạo nền tảng để Đợt 5 kết nối giao diện và báo cáo trực quan.

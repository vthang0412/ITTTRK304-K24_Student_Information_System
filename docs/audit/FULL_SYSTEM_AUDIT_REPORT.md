# FULL SYSTEM AUDIT REPORT – Student Information System (SIS)

**Ngày audit:** 2026-11-17  
**Phạm vi:** Toàn bộ repository `C:\Users\Admin\Downloads\OJT4\ITTTRK304-K24_Student_Information_System` (backend, frontend, DB/migrations, Docker, CI/CD, deploy docs/scripts)  
**Hình thức:** Chỉ đọc/phân tích/kết hợp kiểm tra tĩnh và rà soát cấu hình; test/build artifact-safe không chạy (xem Phần 8). **Không sửa code nghiệp vụ, không migration/deploy trong quá trình audit.**

## 0. Trạng thái Git & tuân thủ nguyên tắc audit
- **Nhánh hiện tại:** `develop` (`HEAD`). Nhánh local/remote: `main`, `develop`, `feature/*`, `fix/*`, `backup/develop-before-revert` (theo `git branch -a`).
- **Git status TRƯỚC audit:** dirty (pre-existing) — modified: `frontend/index.html`, `frontend/src/App.jsx`, `frontend/src/components/Icons.jsx`, `frontend/src/components/Layout.jsx`, `frontend/src/components/chat/chat.css`, `frontend/src/context/ToastContext.jsx`, `frontend/src/pages/Dashboard.jsx`, `frontend/src/pages/admin/AdminCourses.jsx`, `frontend/src/pages/admin/AdminExamRooms.css`, `frontend/src/pages/admin/AdminExamSchedules.css`, `frontend/src/pages/admin/AdminLecturers.jsx`, `frontend/src/pages/admin/AdminSections.jsx`, `frontend/src/pages/admin/AdminSemesters.jsx`, `frontend/src/pages/admin/AdminStudents.jsx`, `frontend/src/pages/lecturer/LecturerGrades.jsx`, `frontend/src/pages/student/StudentExamSchedules.css`, `frontend/src/pages/student/StudentGrades.jsx`, `frontend/src/pages/student/StudentNotifications.jsx`, `frontend/src/styles/global.css`; untracked: `frontend/src/context/ThemeContext.jsx`, `prompt_check_code.md`.
- **Git status SAU audit (trước khi ghi file báo cáo theo yêu cầu):** **không thay đổi** so với trước audit (cùng danh sách trên).
- **Xác nhận:** trong quá trình audit không sửa code, không migration/deploy, không commit/push. File báo cáo này được tạo sau đó theo **yêu cầu trực tiếp của người dùng**.

## 1. Executive Summary
- **Tổng quan:** SIS là hệ thống Spring Boot 3.3 (Java 21) + React 19/Vite + MySQL 8, có Flyway, Docker Compose (dev/prod), GitHub Actions CI/CD, deploy script lên VPS. Phạm vi nghiệp vụ rộng (tài khoản/phân quyền, môn-học kỳ-lớp học phần, đăng ký, lịch học/thi, điểm/gradebook, thông báo, phản ánh, AI assistant, export Excel).
- **Mức độ ổn định (đánh giá từ phân tích tĩnh):** Trung bình–thấp cho production; có rủi ro nghiêm trọng về hạ tầng/dev-prod data, độ an toàn nâng cấp schema, và một số lỗi/thiết kế tiềm ẩn gây sai lệch nghiệp vụ/bảo mật vận hành.
- **Khả năng đưa vào sử dụng thực tế:** **Cần khắc phục lỗi trước khi triển khai Production** (ưu tiên P0/P1: cô lập volume dev/prod, idempotent/upgrade-path migration + kiểm thử MySQL, bỏ fallback secret/profile, vá các lỗi API/quyền/nghiệp vụ đã xác định, hoàn thiện TLS/backup).

## 2. Technology Stack (xác định từ code/cấu hình)

| Hạng mục | Công nghệ (bằng chứng) |
|---|---|
| Backend | Java 21, Spring Boot 3.3.4, Spring Web/Data JPA/Security/Validation/Actuator/Mail, Flyway, JJWT 0.11.5, springdoc-openapi 2.6.0, Jackson Hibernate6, Apache POI, Lombok (`backend/Student_Information_System/build.gradle`) |
| Frontend | React 19.2, react-router-dom 7.13, Axios 1.13, Vite 8, Recharts, SweetAlert2, CSS; lint ESLint (`frontend/package.json`) |
| Database | MySQL 8.0.43 (Compose), driver `mysql-connector-j`; Flyway V1–V15 (`backend/.../resources/db/migration/*`, compose, `application*.properties`). *Ghi chú:* `backend/README.md` còn mô tả PostgreSQL — lệch thực tế (SIS-AUDIT-015). |
| Auth/Security | JWT stateless, BCrypt, method security `@PreAuthorize`, CORS cấu hình qua `app.cors.allowed-origins` (`SecurityConfig.java`, `Jwt*`) |
| Docker | Dev: `docker-compose.yml` (mysql/backend/frontend, healthchecks). Prod: `deploy/docker-compose.prod.yml`. `backend/.../Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `deploy/nginx/sis.conf` |
| CI/CD | GitHub Actions: `.github/workflows/ci.yml` (build/lint), `.github/workflows/deploy.yml` (build/push GHCR, SSH deploy/health/rollback). Deploy scripts: `deploy/deploy.sh`, `backup.sh`, `restore.sh`, `rollback.sh` |
| Prod infra | Host Nginx reverse proxy + container nginx/frontend, GHCR images, `/opt/sis`, volume `sis_mysql_data`, `sis_backend_uploads` (`docs/DEPLOYMENT.md`, deploy configs) |

## 3. Tổng hợp kết quả Audit

> Thống kê dựa trên lỗi/rủi ro **đã ghi nhận** trong báo cáo này (xác nhận hoặc có khả năng/rủi ro rõ từ bằng chứng). Không suy diễn lỗi ngoài bằng chứng.

| Hạng mục | Critical | High | Medium | Low | Info |
|---|---:|---:|---:|---:|---:|
| Backend | 0 | 3 | 4 | 0 | 2 |
| Frontend | 0 | 1 | 2 | 1 | 1 |
| Security | 1 | 2 | 2 | 0 | 1 |
| Database | 0 | 2 | 2 | 1 | 1 |
| Business Logic | 0 | 2 | 2 | 0 | 1 |
| Docker/CI/CD | 1 | 3 | 3 | 1 | 2 |
| Performance | 0 | 1 | 2 | 0 | 1 |
| **Tổng** | **2** | **14** | **17** | **3** | **9** |

## 4. Chi tiết từng lỗi / rủi ro

### SIS-AUDIT-001 — Dev Compose dùng chung volume MySQL với Production
- **Module:** Docker/DB/Deployment  
- **File liên quan:** `docker-compose.yml:99-101`, đối chiếu `deploy/docker-compose.prod.yml:112-114`  
- **Mô tả hiện tượng:** Dev khai báo `mysql_data` `external: true`, `name: sis_mysql_data` (trùng volume prod).  
- **Nguyên nhân:** Cấu hình volume sai; comment “separate… volumes” không đúng thực tế.  
- **Điều kiện xảy ra:** Chạy dev stack trên host có volume prod; hoặc dev `up` trên máy mới thiếu volume external.  
- **Mức độ ảnh hưởng:** **CRITICAL** (mất/toàn vẹn dữ liệu prod, rò rỉ dữ liệu).  
- **Bằng chứng/tái hiện (phân tích):** Dev backend mặc định `SPRING_SQL_INIT_MODE=always` (`docker-compose.yml:58-59`) và seed demo (`data.sql`) → có thể ghi/seed vào DB prod khi dùng chung volume.  
- **Hướng khắc phục đề xuất:** Dùng volume dev riêng (project-scoped/unique name), bỏ `external` hoặc tách hẳn; chặn seed ở môi trường không-dev.  
- **Phân loại:** **Lỗi đã xác nhận (cấu hình).**

### SIS-AUDIT-002 — Đường nâng cấp Flyway baseline=6 + migration không idempotent dễ thất bại/phá vỡ schema
- **Module:** Database/Migration  
- **File liên quan:** `application-dev.properties:16-19`, `application-prod.properties:24-27`; `V2__create_feedback_tables.sql`, `V4__add_attempt_count_to_password_reset_tokens.sql`, `V5__add_student_profile_fields.sql`, `V11__add_recurring_course_section_schedules.sql`, `V12__fix_recurring_schedule_day_of_week_type.sql`, `V13__add_enrollment_batch_operation_history.sql`, `V14__add_gradebook_bulk_action_history.sql`, `V7__reconcile_legacy_schema_and_grade_precision.sql`  
- **Mô tả hiện tượng:** Fresh DB chạy V1–V15; legacy (Hibernate) baseline ở v6 rồi chạy V7+. V11/V13/V14 `CREATE TABLE/ALTER` không `IF NOT EXISTS`/idempotent; V12 đổi kiểu/thêm CHECK; V2/4/5 cũng không idempotent (dù được skip ở baseline-6, phụ thuộc V7 reconcile).  
- **Nguyên nhân:** Chiến lược dual-path + DDL không phòng thủ; MySQL DDL auto-commit.  
- **Điều kiện xảy ra:** Nâng cấp DB legacy đã có bảng/entity tương ứng; dữ liệu legacy không hợp lệ cho CHECK/enum/FK.  
- **Mức độ ảnh hưởng:** **HIGH** (deploy/startup fail, schema bán phần, downtime, rủi ro mất tính toàn vẹn).  
- **Bằng chứng:** Cấu hình baseline-version=6 và nội dung migration (phân tích tĩnh).  
- **Hướng khắc phục đề xuất:** Làm V11/V13/V14 idempotent/điều kiện; kiểm tra dữ liệu trước đổi enum/FK/CHECK; test nâng cấp từ dump legacy thật & fresh DB trong CI.  
- **Phân loại:** **Lỗi/rủi ro có khả năng xảy ra cao (xác nhận về tính không idempotent).**

### SIS-AUDIT-003 — Fallback cấu hình không an toàn & mặc định profile dev
- **Module:** Security/Config  
- **File liên quan:** `backend/.../resources/application.properties:5,8,11,29`  
- **Mô tả hiện tượng:** Mặc định `SPRING_PROFILES_ACTIVE=dev`; fallback DB URL/user/password cứng và JWT secret mặc định yếu.  
- **Nguyên nhân:** Giá trị mặc định trong base properties.  
- **Điều kiện xảy ra:** Chạy ngoài Docker/thiếu env và không ép `prod`.  
- **Mức độ ảnh hưởng:** **HIGH** (dùng secret/DB sai, có thể bật seed/Swagger/CORS dev nếu rơi vào dev).  
- **Bằng chứng:** `spring.datasource.password=${DB_PASSWORD:080306@}`, `app.jwt.secret=...dev-only...`.  
- **Hướng khắc phục đề xuất:** Fail-fast không fallback ở mọi profile; bỏ default dev cho môi trường không-local.  
- **Phân loại:** **Rủi ro đã xác nhận (cấu hình).**

### SIS-AUDIT-004 — API `/api/grades/me` trả entity `Enrollment` nhưng `grade` bị `@JsonIgnore`
- **Module:** Backend/Business Logic/Frontend integration  
- **File liên quan:** `backend/.../controller/grade/GradeController.java:65-68`, `backend/.../entity/academic/Enrollment.java:48,60,67-69`, `frontend/src/services/dashboardService.js:53,62,267,268,270,271,277`  
- **Mô tả hiện tượng:** Controller trả `List<Enrollment>`; field `grade` trong `Enrollment` bị `@JsonIgnore` → response không chứa điểm; FE đọc `enrollment.grade.finalScore`.  
- **Nguyên nhân:** Trả entity thay DTO và ẩn quan hệ bằng Jackson ignore.  
- **Điều kiện xảy ra:** Sinh viên/giao diện gọi `/api/grades/me` để tính GPA/hiển thị.  
- **Mức độ ảnh hưởng:** **HIGH** (hiển thị/tính toán điểm sai hoặc trống ở luồng dùng endpoint này).  
- **Bằng chứng:** Mã nguồn đối chiếu trực tiếp. Endpoint chuẩn DTO tồn tại ở `GradebookController` (`/api/student/grades`).  
- **Hướng khắc phục đề xuất:** Trả DTO có điểm cho `/api/grades/me` hoặc thống nhất FE dùng endpoint DTO.  
- **Phân loại:** **Lỗi có khả năng xảy ra rất cao / gần như xác nhận theo phân tích luồng** (chưa chạy E2E).

### SIS-AUDIT-005 — Controller bypass service + trả Entity trực tiếp, kết hợp `FORCE_LAZY_LOADING`
- **Module:** Backend/Architecture/Performance/Security  
- **File liên quan:** nhiều controller (vd `CourseController`, `SemesterController`, `LecturerController`, `StudentController`, `CourseSectionController`, `EnrollmentController`, `GradeController`, `ReportController`), `backend/.../config/JacksonConfig.java:12-13`  
- **Mô tả hiện tượng:** Nhiều endpoint thao tác repository trực tiếp và/hoặc trả entity JPA; Jackson bật `FORCE_LAZY_LOADING`.  
- **Nguyên nhân:** Thiếu tầng service/DTO nhất quán.  
- **Điều kiện xảy ra:** Payload có quan hệ lazy, dữ liệu lớn, truy cập đồng thời.  
- **Mức độ ảnh hưởng:** **HIGH** (N+1/response phình, lộ cấu trúc dữ liệu ngoài ý muốn, khó kiểm soát quyền ở tầng nghiệp vụ).  
- **Bằng chứng:** Thống kê/đối chiếu tĩnh từ audit backend (nhiều method/entity-returning, repo field ở controller; `Hibernate6Module.Feature.FORCE_LAZY_LOADING`).  
- **Hướng khắc phục đề xuất:** Chuẩn hóa DTO/service, tắt/giới hạn force-lazy, fetch có chủ đích.  
- **Phân loại:** **Vấn đề kiến trúc & rủi ro đã xác nhận.**

### SIS-AUDIT-006 — Logic kiểm tra xung đột lịch/đăng ký bị trùng lặp ở nhiều nơi
- **Module:** Business Logic  
- **File liên quan:** `service/academic/impl/EnrollmentServiceImpl.java`, `service/academic/impl/RecurringCourseScheduleServiceImpl.java`, `service/ai/impl/AICourseAdvisorServiceImpl.java` (phát hiện tĩnh)  
- **Mô tả hiện tượng:** 3 cách cài đặt khác biểu diễn/kiểm tra xung đột.  
- **Nguyên nhân:** Thiếu component dùng chung.  
- **Điều kiện xảy ra:** Đăng ký, tạo/cập nhật lịch lặp, gợi ý AI trên cùng dữ liệu biên.  
- **Mức độ ảnh hưởng:** **MEDIUM–HIGH** (quyết định không nhất quán giữa module).  
- **Bằng chứng:** Phân tích mã nguồn.  
- **Hướng khắc phục đề xuất:** Tập trung một ScheduleConflict domain/service.  
- **Phân loại:** **Rủi ro có khả năng xảy ra.**

### SIS-AUDIT-007 — Bulk grade action không transactional bao trùm
- **Module:** Backend/Business Logic/Data integrity  
- **File liên quan:** `service/grade/GradebookBulkActionService.java` (execute không `@Transactional`)  
- **Mô tả hiện tượng:** Lưu header rồi từng item; lỗi giữa chừng có thể để trạng thái/history bán phần.  
- **Nguyên nhân:** Thiết kế xử lý theo vòng lặp không bao trong transaction tổng.  
- **Điều kiện xảy ra:** Lỗi DB/validation ở item giữa batch.  
- **Mức độ ảnh hưởng:** **MEDIUM** (không nhất quán lịch sử/thao tác hàng loạt).  
- **Bằng chứng:** Phân tích tĩnh.  
- **Hướng khắc phục đề xuất:** Transaction tổng hoặc chiến lược per-item có ghi nhận/compensation rõ ràng.  
- **Phân loại:** **Rủi ro có khả năng xảy ra.**

### SIS-AUDIT-008 — Thiếu/thiết kế validation & phân trang ở một số luồng
- **Module:** Backend/API/Performance  
- **File liên quan:** DTO AI request không annotation validation (`CourseAdvisorRequestDto`, `Create/UpdateStudyPlan*Dto`, `AICreateConversationRequest`…); các `getAll` trả full list (`CourseController`, `StudentController`, `LecturerController`, `CourseSectionController`, `EnrollmentController` admin…) (phát hiện tĩnh)  
- **Mô tả hiện tượng:** Một số input AI thiếu ràng buộc; endpoint list không phân trang.  
- **Nguyên nhân:** Thiếu chuẩn hóa DTO/validation/pagination.  
- **Điều kiện xảy ra:** Input bất thường; dữ liệu lớn.  
- **Mức độ ảnh hưởng:** **MEDIUM** (400 không rõ ràng/logic sai, tải chậm/N+1 phình).  
- **Bằng chứng:** Phân tích tĩnh.  
- **Hướng khắc phục đề xuất:** Bổ sung `@Valid`/constraint; chuẩn hóa paging/filter.  
- **Phân loại:** **Vấn đề có khả năng ảnh hưởng (xác nhận về thiếu pagination/annotation tại các điểm nêu).**

### SIS-AUDIT-009 — CI/CD: không kiểm thử migration/startup MySQL, rủi ro deploy/rollback và hardening
- **Module:** Docker/CI/CD/DB  
- **File liên quan:** `.github/workflows/ci.yml:46-47,76-108`, `.github/workflows/deploy.yml`, `deploy/deploy.sh`, `deploy/rollback.sh`  
- **Mô tả hiện tượng:** CI không chạy Flyway/app với MySQL (fresh/legacy); deploy tự rollback image khi unhealthy nhưng DB có thể đã migrate; workflow có các điểm hardening chưa chặt (host key fallback `ssh-keyscan`, pin action SHA, quyền tối thiểu, validate `image_tag`, mutable `latest`).  
- **Nguyên nhân:** Thiết kế pipeline & script hiện tại.  
- **Điều kiện xảy ra:** Nâng cấp schema, migration lỗi/bán phần, re-run CI cũ, môi trường thiếu known_hosts.  
- **Mức độ ảnh hưởng:** **HIGH** (sự cố prod khó phục hồi, rủi ro supply-chain/ops).  
- **Bằng chứng:** Nội dung workflow/script + tài liệu deploy.  
- **Hướng khắc phục đề xuất:** Job MySQL/Flyway+startup (fresh & legacy dump); runbook migration-failure/restore; rollback DB-aware; pin SHA, least privilege, set `VPS_KNOWN_HOSTS`, validate input.  
- **Phân loại:** **Rủi ro/vấn đề đã xác nhận từ cấu hình.**

### SIS-AUDIT-010 — Auto-rollback/health timeout lệch & chỉ rollback image
- **Module:** CI/CD/Operations  
- **File liên quan:** `deploy/deploy.sh:136-216`, `deploy/docker-compose.prod.yml:36-45,78-84,102-107`  
- **Mô tả hiện tượng:** Thời gian chờ deploy (MySQL 180s, backend 300s, frontend 120s) có thể ngắn hơn cửa sổ healthcheck (backend ~390s, frontend ~165s) → rollback oan; rollback không phục hồi DB.  
- **Mức độ ảnh hưởng:** **MEDIUM–HIGH** (false rollback/downtime, lệch schema-app).  
- **Bằng chứng:** So sánh cấu hình tĩnh.  
- **Hướng khắc phục đề xuất:** Căn chỉnh timeout; quy trình rollback có cân nhắc DB.  
- **Phân loại:** **Rủi ro có khả năng xảy ra.**

### SIS-AUDIT-011 — Backup/restore chưa toàn diện & chưa có offsite tự động
- **Module:** DB/Operations  
- **File liên quan:** `deploy/backup.sh`, `deploy/restore.sh`, `deploy/docker-compose.prod.yml` (volumes), `docs/DEPLOYMENT.md`  
- **Mô tả hiện tượng:** Chỉ dump MySQL; không backup `backend_uploads`; backup tại chỗ, không mã hóa/offsite tự động; không PITR/binlog; `mysqldump` thiếu `--events`; restore import không trong transaction tổng (DDL auto-commit).  
- **Mức độ ảnh hưởng:** **HIGH** (mất dữ liệu/file, RPO/RTO kém).  
- **Bằng chứng:** Nội dung script/config.  
- **Hướng khắc phục đề xuất:** Sao lưu uploads + offsite mã hóa + lịch tự động; kiểm chứng restore định kỳ; bổ sung chiến lược PITR nếu cần.  
- **Phân loại:** **Rủi ro đã xác nhận từ cấu hình.**

### SIS-AUDIT-012 — Host Nginx/TLS & hardening bảo mật web chưa đầy đủ
- **Module:** Security/Production  
- **File liên quan:** `deploy/nginx/sis.conf`, `frontend/nginx.conf`  
- **Mô tả hiện tượng:** `sis.conf` chỉ `listen 80` (phụ thuộc certbot thủ công); thiếu HSTS/CSP/XFO/XCTO/Referrer-Policy, `server_tokens off`, rate limiting; timeout 60s có thể cắt request dài (AI/export); `client_max_body_size` 10M > giới hạn Spring 5MB.  
- **Mức độ ảnh hưởng:** **HIGH** (lộ credential/JWT nếu chạy HTTP, bề mặt tấn công/DoS/504).  
- **Bằng chứng:** Cấu hình tĩnh.  
- **Hướng khắc phục đề xuất:** Bắt buộc HTTPS, thêm security headers/rate limit/timeout phù hợp, đồng bộ giới hạn upload.  
- **Phân loại:** **Rủi ro đã xác nhận (theo config mẫu/hiện có).**

### SIS-AUDIT-013 — Phơi bày cổng dev & lưu ý secret/artifacts cục bộ
- **Module:** Security/Docker  
- **File liên quan:** `docker-compose.yml:61-62,86-87`, `.env`, `.env.isolated`, `backend/.../.env.local` (untracked, tồn tại cục bộ), `backend/.../.dockerignore`  
- **Mô tả hiện tượng:** Dev publish 0.0.0.0; file env thực tồn tại local; `.env.local` có thể vào build context (không được exclude).  
- **Mức độ ảnh hưởng:** **MEDIUM** (lộ dịch vụ dev trên LAN; rủi ro lộ secret nếu chia sẻ máy/backup; build-context leak).  
- **Bằng chứng:** Compose & liệt kê file; không in giá trị secret.  
- **Hướng khắc phục đề xuất:** Bind `127.0.0.1` cho dev; rotate secret đã lộ; thêm `.env*` vào dockerignore backend.  
- **Phân loại:** **Rủi ro/vấn đề xác nhận (phơi cổng & tồn tại file).**

### SIS-AUDIT-014 — Frontend: xử lý 401/403 tập trung & hard redirect
- **Module:** Frontend/Auth UX  
- **File liên quan:** `frontend/src/api/httpClient.js:4,29-56`, `frontend/src/routes/ProtectedRoute.jsx`  
- **Mô tả hiện tượng:** 401 interceptor xóa storage và `window.location.href='/login'` (mất state SPA); fallback baseURL `http://localhost:8080`; không có handler 403 tập trung rõ (route redirect `/unauthorized`).  
- **Mức độ ảnh hưởng:** **MEDIUM** (trải nghiệm/khôi phục phiên kém; cấu hình env sai dễ gọi nhầm host).  
- **Bằng chứng:** Mã nguồn.  
- **Hướng khắc phục đề xuất:** Điều hướng trong SPA, xử lý 403 nhất quán, siết cấu hình `VITE_API_BASE_URL`.  
- **Phân loại:** **Vấn đề đã xác nhận (thiết kế hiện tại).**

### SIS-AUDIT-015 — Sai lệch tài liệu/cấu hình & seed/SQL legacy gây nhiễu
- **Module:** DB/Docs/Build hygiene  
- **File liên quan:** `backend/README.md`, `docs/DEPLOYMENT.md`, `application.properties`, `V1__baseline_schema.sql`, `data.sql`, `data_fixed.sql`  
- **Mô tả hiện tượng:** Tài liệu không khớp thực tế (PostgreSQL vs MySQL, mốc V1–V9 vs V1–V15, port/project/volume lệch, comment ddl-auto); `data.sql` vừa seed vừa chứa DDL trùng V15; `data_fixed.sql` nguy hiểm nếu chạy thủ công trước Flyway.  
- **Mức độ ảnh hưởng:** **MEDIUM** (vận hành sai, schema drift/nhầm lẫn).  
- **Bằng chứng:** Đối chiếu file.  
- **Hướng khắc phục đề xuất:** Đồng bộ docs; tách seed khỏi DDL; cô lập/loại bỏ script legacy khỏi luồng chuẩn.  
- **Phân loại:** **Vấn đề/xác nhận lệch tài liệu & rủi ro vận hành.**

### SIS-AUDIT-016 — CSDL runtime & JDBC hardening
- **Module:** Database/Security/Performance  
- **File liên quan:** `deploy/docker-compose.prod.yml`, `application.properties:15`, JDBC URL trong compose/app props  
- **Mô tả hiện tượng:** App dùng chung quyền với nhu cầu DDL (Flyway cùng datasource); JDBC nội bộ `useSSL=false`; thiếu tinh chỉnh connection/OSIV.  
- **Mức độ ảnh hưởng:** **MEDIUM** (blast radius khi app bị xâm nhập, giữ connection lâu/hiệu năng).  
- **Bằng chứng:** Cấu hình tĩnh.  
- **Hướng khắc phục đề xuất:** Tách user migrate/runtime nếu khả thi, bật TLS khi cần, cấu hình pool & cân nhắc `open-in-view=false`.  
- **Phân loại:** **Rủi ro.**

### SIS-AUDIT-017 — Drift biến môi trường/feature prod (AI, enrollment limits…)
- **Module:** Backend/Deployment  
- **File liên quan:** `deploy/docker-compose.prod.yml:58-75`, `deploy/env.production.example`, `application.properties`  
- **Mô tả hiện tượng:** Compose prod không truyền một số biến AI/giới hạn; `SPRING_FLYWAY_BASELINE_ON_MIGRATE` khai báo ở env example nhưng không forward (dùng default).  
- **Mức độ ảnh hưởng:** **MEDIUM** (tính năng AI/giới hạn không đúng kỳ vọng prod).  
- **Bằng chứng:** Đối chiếu file.  
- **Hướng khắc phục đề xuất:** Chuẩn hóa env contract & forward biến cần thiết.  
- **Phân loại:** **Vấn đề cấu hình có khả năng ảnh hưởng.**

### SIS-AUDIT-018 — Frontend: data fetching/validation/UX chưa đồng nhất (một phần)
- **Module:** Frontend  
- **File liên quan:** nhiều trang admin/student/lecturer (dấu hiệu: `// eslint-disable-line react-hooks/exhaustive-deps`, `catch` trống, không `AbortController`, `noValidate` tại `Login.jsx:153`), `frontend/src/context/ThemeContext.jsx` (sync mount)  
- **Mô tả hiện tượng:** Một số nơi thiếu guard/hủy request khi đổi trang nhanh, xử lý lỗi/loading không nhất quán; validation client tùy trang.  
- **Mức độ ảnh hưởng:** **LOW–MEDIUM** (race/stale UI, thông báo lỗi kém trong edge cases).  
- **Bằng chứng:** Rà soát tĩnh (grep/đọc mẫu), chưa E2E.  
- **Hướng khắc phục đề xuất:** Chuẩn hóa hook fetch (abort/guard), error boundary/toast nhất quán, bổ sung validation.  
- **Phân loại:** **Rủi ro/quan sát cần kiểm thử thủ công.**

### SIS-AUDIT-019 — Thiếu test tích hợp/DB & hạn chế kiểm chứng build/test trong audit
- **Module:** QA/Build  
- **File liên quan:** `backend/.../build.gradle`, `src/test/*`  
- **Mô tả hiện tượng:** Không có kiểm chứng Flyway/app với MySQL (Mockito unit, không Testcontainers/H2, context test trống). Build/test Gradle/npm sẽ tạo artifact (`build/`, `dist/`, cache) nên không thực thi trong phiên audit read-only.  
- **Mức độ ảnh hưởng:** **MEDIUM** (độ tin cậy thấp khi release).  
- **Bằng chứng:** Cấu hình & cấu trúc test.  
- **Hướng khắc phục đề xuất:** Bổ sung integration tests; chạy CI/build trong môi trường cho phép.  
- **Phân loại:** **Hạn chế đã xác nhận.**

### SIS-AUDIT-020 — Điểm tích cực (Info)
- **Module:** Security/Backend  
- **Nội dung:** `SecurityConfig` stateless, CSRF off cho API token, CORS không wildcard, BCrypt, `@PreAuthorize`, `User.passwordHash` `@JsonIgnore`, `GlobalExceptionHandler` ẩn chi tiết 500, health details hidden, non-root containers, log rotation.  
- **Phân loại:** **INFO (điểm tích cực).**

## 5. Những chức năng đã kiểm tra

| Nhóm | Phân loại | Ghi chú (dựa trên bằng chứng) |
|---|---|---|
| Cấu trúc repo, Git, stack, cấu hình chính, migrations, Docker/CI/CD/scripts, security config, routing/API client mẫu | Đã phân tích code/cấu hình | Đọc trực tiếp + tổng hợp rà soát tĩnh; không chạy hệ thống |
| `/api/grades/me` vs `Enrollment.grade @JsonIgnore` vs FE dùng `finalScore` | Có vấn đề xác nhận theo phân tích luồng | Cần E2E xác nhận runtime |
| Dev/prod volume, baseline Flyway & idempotency, fallback secrets/profile, backup/restore phạm vi, nginx prod HTTP-only | Có rủi ro/lỗi xác nhận (config) | Rủi ro vận hành/dữ liệu cao |
| Đăng nhập/JWT filter/CORS/role guard cơ bản, phân trang ở một số module (timetable/bulk history), DTO gradebook student | Hoạt động đúng theo thiết kế (phân tích tĩnh một phần) | Chưa kiểm thử thực tế |
| Đăng ký học phần (trùng/đồng thời/trùng lịch), lịch học/thi, nhập-duyệt-công bố-khóa điểm, thông báo/ticket/AI end-to-end | Chưa đủ điều kiện kiểm tra thực tế | Chỉ suy luận từ code; cần dữ liệu, DB, runtime, concurrent tests |
| Build Backend/Frontend, Unit/Integration/E2E, lint/typecheck thực thi | Chưa chạy | Tránh phát sinh artifact/thay đổi FS & thiếu môi trường phù hợp |

## 6. Rủi ro tiềm ẩn
- **Race condition nghiệp vụ:** đăng ký khi gần full slot, bulk actions/grade publish đồng thời, cập nhật lịch/lịch thi trùng (cần verify transaction/isolation thực tế).
- **Mất cập nhật/nhất quán:** thao tác song song giữa lecturer/admin trên gradebook/enrollment.
- **Schema drift & startup:** lệch Hibernate entity vs migration trên DB legacy; `FORCE_LAZY_LOADING` + entity API gây tải bất thường khi dữ liệu tăng.
- **Khả dụng:** DB/backend restart, mất kết nối DB, request AI/export dài vượt timeout proxy.
- **Bảo mật dữ liệu:** IDOR/quyền truy cập chéo Student-Lecturer-Admin cần kiểm thử runtime (đã có `@PreAuthorize` ở nhiều nơi nhưng chưa audit ma trận endpoint↔role đầy đủ).
- **Supply chain/ops:** action tag chưa pin SHA, mutable `latest`, secret GHCR trên VPS host.

## 7. Đề xuất thứ tự sửa lỗi (không thực hiện)

| Ưu tiên | Hạng mục | Phạm vi đề xuất & module có thể bị ảnh hưởng |
|---|---|---|
| **P0 (Critical)** | SIS-AUDIT-001 | Tách volume dev/prod, cập nhật compose/docs/scripts; ảnh hưởng dev env, DB ops (cần kiểm tra dữ liệu host). |
| **P0 (Critical)** | SIS-AUDIT-011/012 (prod readiness) | Bắt buộc HTTPS/headers/rate limit cho `deploy/nginx`; bổ sung backup uploads + offsite + lịch; ảnh hưởng deploy/nginx, backup scripts, ops. |
| **P1 (High)** | SIS-AUDIT-002,009,010 | Idempotent migration + CI test MySQL (fresh/legacy) + runbook rollback/restore; căn timeout; ảnh hưởng migrations, CI, deploy scripts, DB. |
| **P1 (High)** | SIS-AUDIT-003,013,017 | Bỏ fallback secret/profile, bind dev localhost, chuẩn hóa env contract; ảnh hưởng config, compose, security posture. |
| **P1 (High)** | SIS-AUDIT-004,005,006,007,008 | Sửa DTO `/grades/me`, chuẩn hóa service/DTO/pagination/lazy, hợp nhất conflict logic, transactional bulk; ảnh hưởng grade/enrollment/schedule/AI controllers-services-FE (cần regression). |
| **P2 (Medium)** | SIS-AUDIT-014,015,016,018 | Cải thiện interceptor/UX auth, đồng bộ docs/seed scripts, harden DB/JDBC/pool, chuẩn hóa fetch/validation; ảnh hưởng FE, docs, config, ops. |
| **P3 (Low)** | Lint/format/encoding artifacts, dọn script legacy, đồng bộ README | Ảnh hưởng docs, resources, UI text. |
| **P4 (Optimization)** | Index/query tuning thực đo, caching, open-in-view/pool tuning, observability (metrics/log/alert) | Ảnh hưởng perf/ops toàn hệ thống (sau khi có số đo). |

## 7.1. Kế hoạch khắc phục theo đợt (prompt_4A–5B)

> Các prompt dưới đây là kế hoạch thực thi chi tiết; cần phê duyệt và thực hiện theo từng đợt, kiểm thử hồi quy sau mỗi đợt. Không thay đổi nghiệp vụ/SRS ngoài phạm vi nêu trong prompt.

| Đợt | Prompt | Audit liên quan | Phạm vi chính | Ràng buộc/ghi chú nghiệm thu |
|---|---|---|---|---|
| 4A | `prompt_4A.md` | **SIS-AUDIT-004** (+ tích hợp điểm) | Thống nhất API/DTO điểm (`GradeController` `/api/grades/me`, `GradebookController` `/api/student/grades`), không trả entity lộ/ẩn sai; cập nhật FE (`dashboardService`, trang điểm/GPA) tương thích | Không bỏ `@JsonIgnore` máy móc; không lộ điểm chưa công bố/sai quyền; giữ OPEN/LOCKED/APPROVED/PUBLISHED, thang 0–10, công thức GPA/final; đối chiếu API–DB–UI |
| 4B | `prompt_4B.md` | **SIS-AUDIT-005, SIS-AUDIT-008** | Chuẩn hóa controller/service/DTO có vấn đề, kiểm soát lazy loading (không tắt `FORCE_LAZY_LOADING` máy móc), phân trang list lớn, validation DTO (AI/study plan), giảm N+1 | Không refactor lan rộng/đổi response tùy tiện; giữ search/filter/sort/pagination hiện có; không lộ dữ liệu nhạy cảm; build & kiểm thử API/quyền/serialization |
| 4C | `prompt_4C.md` | **SIS-AUDIT-006, SIS-AUDIT-007** | Thống nhất logic xung đột lịch (đăng ký, lịch lặp, lịch thi, AI advisor), transaction/consistency cho đăng ký & `GradebookBulkActionService` | Backend là nguồn quyết định; không đổi SRS; kiểm tra trùng phòng/GV/SV, lịch hủy, giao nhau thời gian, race suất cuối, rollback/bán phần bulk; báo cáo trước/sau & rủi ro |
| 5A | `prompt_5A.md` | **SIS-AUDIT-014** | Chuẩn hóa xử lý 401/403 trong `httpClient`/routing, tránh hard redirect/vòng lặp/request thừa, chuẩn hóa `VITE_API_BASE_URL`, không đổi JWT backend | Kiểm thử login/token hết hạn/sai, 401/403/404/500, logout/F5/deep-link/theo vai trò |
| 5B | `prompt_5B.md` | **SIS-AUDIT-018** (+ theme liên quan) | Data fetching (abort/race/stale/loading/error/empty), validation form các module, hoàn thiện theme Light/Dark hiện có (không redesign) | Không ghi đè thay đổi FE dở dang (`ThemeContext`, Layout/Header/Sidebar…); không bỏ `eslint-disable react-hooks` máy móc; kiểm thử chuyển trang nhanh, form, API lỗi, theme/F5/responsive |

## 8. Kết luận
- **Đánh giá phù hợp với bằng chứng:** **Cần khắc phục lỗi trước khi triển khai Production.** Hệ thống **có thể tiếp tục phát triển** và **có thể kiểm thử nội bộ** trong môi trường cô lập sau khi xử lý tối thiểu các rủi ro P0/P1 về cô lập dữ liệu, migration/backup và cấu hình bảo mật.
- **Chưa đủ điều kiện triển khai thực tế/production an toàn** cho tới khi xác minh nâng cấp schema, backup/restore, và vá các lỗi nghiệp vụ/bảo mật đã nêu.
- **Cập nhật kế hoạch:** đã bổ sung lộ trình thực thi chi tiết theo `prompt_4A–5B` tại mục **7.1** (tập trung các audit **004–008, 014, 018**); các hạng mục P0/P1 còn lại (001, 002, 003, 009–013, 015–017, 019) vẫn cần kế hoạch/prompt riêng hoặc xử lý song song theo ưu tiên.

## 9. Kết quả cuối cùng
1. **Báo cáo tổng hợp:** Đã trình bày trong file này.
2. **Tổng số lỗi/rủi ro ghi nhận:** **45 mục** — Critical: **2**, High: **14**, Medium: **17**, Low: **3**, Info: **9** (theo bảng Phần 3).
3. **10 vấn đề nghiêm trọng nhất (ưu tiên):**
   1. SIS-AUDIT-001 — Dev/prod dùng chung MySQL volume (Critical).
   2. SIS-AUDIT-002 — Baseline Flyway + migration không idempotent/rủi ro nâng cấp (High).
   3. SIS-AUDIT-003 — Fallback secret/profile dev nguy hiểm (High).
   4. SIS-AUDIT-004 — `/api/grades/me` trả entity, grade bị ẩn → sai/thiếu dữ liệu điểm (High).
   5. SIS-AUDIT-005 — Controller trả entity/bypass service + force lazy loading (High).
   6. SIS-AUDIT-009 — CI không test migration MySQL, deploy/rollback/hardening rủi ro (High).
   7. SIS-AUDIT-011 — Backup/restore chưa toàn diện, không offsite tự động (High).
   8. SIS-AUDIT-012 — Nginx prod HTTP-only/thiếu hardening (High).
   9. SIS-AUDIT-006 — Trùng lặp logic xung đột lịch/đăng ký (High/Medium).
   10. SIS-AUDIT-010 — Timeout health/rollback chỉ image, lệch DB-app (High/Medium).
4. **Các chức năng cần kiểm thử thủ công (ưu tiên):**
   - Ma trận quyền thực tế: Student không xem/sửa dữ liệu sinh viên khác; Lecturer chỉ trong phạm vi được phân công; Admin endpoints (kể cả AI, notification, feedback, timetable, report, exam, gradebook).
   - Đăng ký học phần: trùng lớp, ngoài thời gian, lớp đầy, bị khóa, vượt tín chỉ, trùng lịch, hủy/đăng ký lại, **đồng thời** (race) vào suất cuối.
   - Lịch học lặp/tuần kế tiếp/cập nhật-xóa (soft-cancel), hiển thị theo role & tuần, xung đột phòng/GV/SV.
   - Gradebook: nhập-sửa-duyệt-từ chối-khóa/mở-công bố, lịch sử, bulk actions, phân quyền, thang 0–10, tính GPA/final sau publish.
   - Lịch thi: trùng phòng/GV/SV, online/offline, xuất Excel/in, nhất quán Admin-Student.
   - Auth/profile/token hết hạn (401/403), F5 deep-link, phân trang/search/filter, form validation & double-submit.
   - Thông báo/feedback/ticket luồng trạng thái & đính kèm; AI assistant (nếu bật) với giới hạn/rate/key.
   - Deploy kịch bản: fresh DB, upgrade legacy, migration fail + rollback/restore, mất kết nối DB/restart backend.
5. **Danh sách công việc đề xuất sau Audit:** (1) Tách volume dev, vá env/secret fallback; (2) Làm idempotent migrations + thêm CI MySQL (fresh/legacy); (3) Thực thi đợt **4A** (`prompt_4A.md`): sửa `/grades/me` & thống nhất DTO/FE điểm; (4) Thực thi đợt **4B** (`prompt_4B.md`): chuẩn hóa controller/service/DTO, pagination, validation, lazy/N+1; (5) Thực thi đợt **4C** (`prompt_4C.md`): hợp nhất conflict lịch & transaction/bulk safety; (6) Thực thi đợt **5A–5B** (`prompt_5A.md`, `prompt_5B.md`): auth/error handling FE & data fetching/validation/theme; (7) Hoàn thiện HTTPS/headers/backup-offsite & runbook; (8) Rà soát phân quyền endpoint + E2E nghiệp vụ trọng yếu; (9) Đồng bộ docs/scripts; (10) Bổ sung integration/E2E & observability.
6. **Đường dẫn file báo cáo Markdown:** `docs/audit/FULL_SYSTEM_AUDIT_REPORT.md`
7. **Trạng thái cập nhật báo cáo:** đã bổ sung mục **7.1** và cập nhật kết luận/danh sách công việc để phản ánh kế hoạch `prompt_4A–5B` (chỉ chỉnh sửa tài liệu audit, không sửa code nghiệp vụ).
8. **Xác nhận:** không sửa code nghiệp vụ, không commit, không push, không triển khai. Dừng sau báo cáo; chờ phê duyệt từng nhóm lỗi/đợt sửa trước khi thực hiện.

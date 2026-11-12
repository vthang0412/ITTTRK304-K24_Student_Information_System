# Lịch học định kỳ theo tuần

## Dành cho ADMIN

1. Mở **Quản lý lớp học phần** và chọn **Xem / thiết lập lịch** trên lớp cần cập nhật.
2. Chọn ngày bắt đầu, ngày kết thúc và thêm một dòng cho mỗi buổi cố định trong tuần.
3. Với từng buổi, chọn thứ, khoảng tiết, giờ bắt đầu/kết thúc thực tế và phòng học.
4. Chọn **Lưu lịch học**. Hệ thống kiểm tra xung đột trước khi lưu và kiểm tra lại trong giao dịch ghi dữ liệu.

Khoảng ngày được tính bao gồm ngày bắt đầu và ngày kết thúc; hệ thống chỉ tạo buổi vào đúng thứ được chọn. Mỗi dòng là một quy tắc lặp, không phải một bản ghi cho từng tuần. Mặc định giao diện gợi ý buổi học 2 giờ; ADMIN có thể điều chỉnh giờ kết thúc, ví dụ thành 3 giờ. Số tiết và giờ thực tế đều được lưu, còn xung đột được tính theo giờ thực tế.

Xung đột chỉ xảy ra khi cùng thứ có ít nhất một ngày học thực tế nằm trong phần giao của hai khoảng ngày và khung giờ thực tế giao nhau. Hệ thống kiểm tra phòng, giảng viên và sinh viên có đăng ký đã được duyệt. Các lịch legacy dạng văn bản cũng được xét khi kiểm tra.

Khi chỉnh sửa lịch, quy tắc cũ được lưu lại đến ngày trước khi lịch mới có hiệu lực. Khi xóa, các buổi từ ngày mai trở đi bị hủy; những buổi đã qua không bị xóa khỏi dữ liệu. Thời khóa biểu chỉ hiển thị những buổi nằm trong tuần đang xem và thuộc lớp mà sinh viên đã được duyệt đăng ký.

## API

- `GET /api/course-sections/{sectionId}/recurring-schedules` — xem các quy tắc đang hoạt động.
- `POST /api/course-sections/{sectionId}/recurring-schedules/check` — kiểm tra dữ liệu và xung đột trước khi lưu; trả HTTP `204` nếu hợp lệ.
- `PUT /api/course-sections/{sectionId}/recurring-schedules` — thay toàn bộ các buổi định kỳ đang hoạt động bằng cấu hình mới; lưu theo transaction.
- `DELETE /api/course-sections/{sectionId}/recurring-schedules` — hủy các buổi từ ngày kế tiếp, giữ lịch sử.
- `GET /api/timetable/admin` — lịch toàn trường theo tuần, chỉ dành cho ADMIN; hỗ trợ `semesterId`, `sectionId`, `lecturerId`, `classroom`, `weekStart`, `page` và `size`.
- `GET /api/timetable` — thời khóa biểu cá nhân theo tuần, chỉ dành cho STUDENT; student ID luôn được xác định từ JWT.

Các API quản lý này yêu cầu quyền `ADMIN`. `PUT` nhận `startDate`, `endDate` và mảng `sessions`; mỗi session gồm `dayOfWeek` (Thứ Hai = 1, Chủ Nhật = 7), `startPeriod`, `endPeriod`, `startTime`, `endTime` và `classroom`.

Migration `V11__add_recurring_course_section_schedules.sql` tạo bảng quy tắc định kỳ và thêm mốc hủy mềm cho lịch legacy. Migration V12 chuyển `day_of_week` sang `INT` và kiểm tra miền 1–7; không xóa hoặc backfill dữ liệu lịch hiện tại.

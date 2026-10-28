-- ============================================================
-- DỮ LIỆU MẪU HỆ THỐNG THÔNG TIN SINH VIÊN
-- Tất cả tài khoản demo có mật khẩu: 123456
-- File được lưu chuẩn UTF-8 (không BOM) và ép kết nối utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS sis_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sis_db;

-- Users
CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(255) NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME(6),
  updated_at DATETIME(6)
);

-- Students
CREATE TABLE IF NOT EXISTS students (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_code VARCHAR(255) NOT NULL UNIQUE,
  full_name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  department VARCHAR(255),
  status VARCHAR(20) NOT NULL,
  user_id BIGINT UNIQUE,
  CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Lecturers
CREATE TABLE IF NOT EXISTS lecturers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lecturer_code VARCHAR(255) NOT NULL UNIQUE,
  full_name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  department VARCHAR(255),
  status VARCHAR(20) NOT NULL,
  user_id BIGINT UNIQUE,
  CONSTRAINT fk_lecturer_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Courses
CREATE TABLE IF NOT EXISTS courses (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_code VARCHAR(255) NOT NULL UNIQUE,
  course_name VARCHAR(255) NOT NULL,
  credits INT NOT NULL,
  description VARCHAR(1000),
  status VARCHAR(20) NOT NULL
);

-- Semesters
CREATE TABLE IF NOT EXISTS semesters (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  semester_code VARCHAR(255) NOT NULL UNIQUE,
  semester_name VARCHAR(255) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  registration_start DATETIME(6),
  registration_end DATETIME(6),
  status VARCHAR(20) NOT NULL
);

-- Course Sections
CREATE TABLE IF NOT EXISTS course_sections (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  section_code VARCHAR(255) NOT NULL UNIQUE,
  course_id BIGINT NOT NULL,
  semester_id BIGINT NOT NULL,
  lecturer_id BIGINT,
  capacity INT NOT NULL,
  schedule VARCHAR(500),
  status VARCHAR(20) NOT NULL,
  CONSTRAINT fk_section_course FOREIGN KEY (course_id) REFERENCES courses(id),
  CONSTRAINT fk_section_semester FOREIGN KEY (semester_id) REFERENCES semesters(id),
  CONSTRAINT fk_section_lecturer FOREIGN KEY (lecturer_id) REFERENCES lecturers(id)
);

-- Enrollments
CREATE TABLE IF NOT EXISTS enrollments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  section_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  registered_at DATETIME(6),
  withdrawn_at DATETIME(6),
  CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES students(id),
  CONSTRAINT fk_enrollment_section FOREIGN KEY (section_id) REFERENCES course_sections(id),
  UNIQUE KEY uk_student_section (student_id, section_id)
);

-- Grades
CREATE TABLE IF NOT EXISTS grades (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  enrollment_id BIGINT NOT NULL UNIQUE,
  component_score DECIMAL(4,2),
  final_score DECIMAL(4,2),
  updated_by VARCHAR(255),
  updated_at DATETIME(6),
  CONSTRAINT fk_grade_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id)
);

-- Gradebooks
CREATE TABLE IF NOT EXISTS gradebooks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  section_id BIGINT NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL,
  locked_at DATETIME(6),
  locked_by VARCHAR(255),
  CONSTRAINT fk_gradebook_section FOREIGN KEY (section_id) REFERENCES course_sections(id)
);

-- Bổ sung trạng thái mới cho các CSDL đã được tạo từ phiên bản trước.
ALTER TABLE gradebooks MODIFY COLUMN status ENUM('OPEN', 'LOCKED', 'APPROVED', 'PUBLISHED') NOT NULL;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE grade_audit_logs;
TRUNCATE TABLE grade_component_scores;
TRUNCATE TABLE grade_components;
TRUNCATE TABLE grades;
TRUNCATE TABLE gradebooks;
TRUNCATE TABLE enrollments;
TRUNCATE TABLE course_sections;
TRUNCATE TABLE semesters;
TRUNCATE TABLE courses;
TRUNCATE TABLE lecturers;
TRUNCATE TABLE students;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- TÀI KHOẢN NGƯỜI DÙNG
-- ------------------------------------------------------------
INSERT INTO users (username, email, full_name, password_hash, role, status, created_at, updated_at) VALUES
('admin', 'admin@sis.edu.vn', 'Quản Trị Viên', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'ADMIN', 'ACTIVE', NOW(), NOW()),
('lecturer', 'lecturer@sis.edu.vn', 'Trần Văn Giảng', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'LECTURER', 'ACTIVE', NOW(), NOW()),
('lecturer2', 'lecturer2@sis.edu.vn', 'Lê Thị Hương', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'LECTURER', 'ACTIVE', NOW(), NOW()),
('lecturer3', 'lecturer3@sis.edu.vn', 'Nguyễn Hoàng Nam', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'LECTURER', 'ACTIVE', NOW(), NOW()),
('lecturer4', 'lecturer4@sis.edu.vn', 'Võ Thị Lan', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'LECTURER', 'ACTIVE', NOW(), NOW()),
('lecturer5', 'lecturer5@sis.edu.vn', 'Nguyễn Thị Bích Ngọc', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'LECTURER', 'ACTIVE', NOW(), NOW()),
('lecturer6', 'lecturer6@sis.edu.vn', 'Phan Văn Tài', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'LECTURER', 'ACTIVE', NOW(), NOW()),
('student', 'student@sis.edu.vn', 'Nguyễn Văn Sinh', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'STUDENT', 'ACTIVE', NOW(), NOW()),
('student2', 'student2@sis.edu.vn', 'Phạm Minh Tuấn', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'STUDENT', 'ACTIVE', NOW(), NOW()),
('student3', 'student3@sis.edu.vn', 'Hoàng Thị Mai', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'STUDENT', 'ACTIVE', NOW(), NOW()),
('student4', 'student4@sis.edu.vn', 'Đỗ Quang Huy', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'STUDENT', 'ACTIVE', NOW(), NOW()),
('student5', 'student5@sis.edu.vn', 'Bùi Thị Ngọc', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'STUDENT', 'ACTIVE', NOW(), NOW()),
('student6', 'student6@sis.edu.vn', 'Vũ Đức Anh', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'STUDENT', 'ACTIVE', NOW(), NOW()),
('student7', 'student7@sis.edu.vn', 'Ngô Thị Lan', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'STUDENT', 'ACTIVE', NOW(), NOW()),
('student8', 'student8@sis.edu.vn', 'Trịnh Văn Long', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'STUDENT', 'ACTIVE', NOW(), NOW()),
('student9', 'student9@sis.edu.vn', 'Lý Thị Hồng', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'STUDENT', 'ACTIVE', NOW(), NOW()),
('student10', 'student10@sis.edu.vn', 'Đặng Minh Khoa', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'STUDENT', 'ACTIVE', NOW(), NOW()),
('student11', 'student11@sis.edu.vn', 'Trần Quốc Bảo', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'STUDENT', 'ACTIVE', NOW(), NOW()),
('student12', 'student12@sis.edu.vn', 'Phan Thị Thu Hà', '$2a$10$IJcu1QMBRnYSR4w1nGfpyO.EHrQcxn0JZQkfNie1BKlUzC5CTOtd2', 'STUDENT', 'ACTIVE', NOW(), NOW());

-- ------------------------------------------------------------
-- SINH VIÊN
-- ------------------------------------------------------------
INSERT INTO students (student_code, full_name, email, department, status, user_id) VALUES
('SV001', 'Nguyễn Văn Sinh', 'student@sis.edu.vn', 'CNTT', 'ACTIVE', (SELECT id FROM users WHERE username='student')),
('SV002', 'Phạm Minh Tuấn', 'student2@sis.edu.vn', 'CNTT', 'ACTIVE', (SELECT id FROM users WHERE username='student2')),
('SV003', 'Hoàng Thị Mai', 'student3@sis.edu.vn', 'KTPM', 'ACTIVE', (SELECT id FROM users WHERE username='student3')),
('SV004', 'Đỗ Quang Huy', 'student4@sis.edu.vn', 'HTTT', 'ACTIVE', (SELECT id FROM users WHERE username='student4')),
('SV005', 'Bùi Thị Ngọc', 'student5@sis.edu.vn', 'TTNT', 'ACTIVE', (SELECT id FROM users WHERE username='student5')),
('SV006', 'Vũ Đức Anh', 'student6@sis.edu.vn', 'CNTT', 'ACTIVE', (SELECT id FROM users WHERE username='student6')),
('SV007', 'Ngô Thị Lan', 'student7@sis.edu.vn', 'KTPM', 'ACTIVE', (SELECT id FROM users WHERE username='student7')),
('SV008', 'Trịnh Văn Long', 'student8@sis.edu.vn', 'HTTT', 'ACTIVE', (SELECT id FROM users WHERE username='student8')),
('SV009', 'Lý Thị Hồng', 'student9@sis.edu.vn', 'TTNT', 'ACTIVE', (SELECT id FROM users WHERE username='student9')),
('SV010', 'Đặng Minh Khoa', 'student10@sis.edu.vn', 'CNTT', 'ACTIVE', (SELECT id FROM users WHERE username='student10')),
('SV011', 'Trần Quốc Bảo', 'student11@sis.edu.vn', 'CNTT', 'ACTIVE', (SELECT id FROM users WHERE username='student11')),
('SV012', 'Phan Thị Thu Hà', 'student12@sis.edu.vn', 'KTPM', 'ACTIVE', (SELECT id FROM users WHERE username='student12'));

-- ------------------------------------------------------------
-- GIẢNG VIÊN
-- ------------------------------------------------------------
INSERT INTO lecturers (lecturer_code, full_name, email, department, status, user_id) VALUES
('GV001', 'Trần Văn Giảng', 'lecturer@sis.edu.vn', 'CNTT', 'ACTIVE', (SELECT id FROM users WHERE username='lecturer')),
('GV002', 'Lê Thị Hương', 'lecturer2@sis.edu.vn', 'KTPM', 'ACTIVE', (SELECT id FROM users WHERE username='lecturer2')),
('GV003', 'Nguyễn Hoàng Nam', 'lecturer3@sis.edu.vn', 'HTTT', 'ACTIVE', (SELECT id FROM users WHERE username='lecturer3')),
('GV004', 'Võ Thị Lan', 'lecturer4@sis.edu.vn', 'TTNT', 'ACTIVE', (SELECT id FROM users WHERE username='lecturer4')),
('GV005', 'Nguyễn Thị Bích Ngọc', 'lecturer5@sis.edu.vn', 'CNTT', 'ACTIVE', (SELECT id FROM users WHERE username='lecturer5')),
('GV006', 'Phan Văn Tài', 'lecturer6@sis.edu.vn', 'TTNT', 'ACTIVE', (SELECT id FROM users WHERE username='lecturer6'));

-- ------------------------------------------------------------
-- MÔN HỌC
-- ------------------------------------------------------------
INSERT INTO courses (course_code, course_name, credits, description, status) VALUES
('INT2204', 'Lập trình hướng đối tượng', 3, 'Ngôn ngữ Java, OOP cơ bản và nâng cao', 'ACTIVE'),
('INT3306', 'Cơ sở dữ liệu', 3, 'Thiết kế, mô hình hóa và truy vấn cơ sở dữ liệu', 'ACTIVE'),
('INT3401', 'Cấu trúc dữ liệu và giải thuật', 3, 'Cấu trúc dữ liệu cơ bản, thuật toán và độ phức tạp', 'ACTIVE'),
('INT3507', 'Công nghệ phần mềm', 3, 'Quy trình phát triển, yêu cầu, thiết kế và kiểm thử', 'ACTIVE'),
('MAT1013', 'Đại số tuyến tính', 3, 'Ma trận, định thức và không gian vector', 'ACTIVE'),
('INT3102', 'Mạng máy tính', 3, 'Mô hình OSI, TCP/IP và các giao thức mạng', 'ACTIVE'),
('INT3202', 'Hệ điều hành', 3, 'Quản lý tiến trình, bộ nhớ và hệ thống tệp', 'ACTIVE'),
('INT3301', 'Ngôn ngữ lập trình', 3, 'Nguyên lý và kỹ thuật của ngôn ngữ lập trình', 'INACTIVE'),
('INT3405', 'Trí tuệ nhân tạo', 3, 'Tìm kiếm, học máy và ứng dụng trí tuệ nhân tạo', 'ACTIVE'),
('INT3501', 'Phân tích thiết kế hệ thống', 3, 'Phương pháp phân tích và thiết kế hệ thống thông tin', 'ACTIVE'),
('INT3303', 'Lập trình web', 3, 'HTML, CSS, JavaScript, React và phát triển ứng dụng web', 'ACTIVE'),
('INT3304', 'Phát triển ứng dụng di động', 3, 'Xây dựng ứng dụng di động đa nền tảng', 'ACTIVE'),
('INT3305', 'An toàn thông tin', 3, 'Mã hóa, xác thực và bảo mật hệ thống thông tin', 'ACTIVE');

-- ------------------------------------------------------------
-- HỌC KỲ
-- ------------------------------------------------------------
INSERT INTO semesters (semester_code, semester_name, start_date, end_date, registration_start, registration_end, status) VALUES
('HK1-2025-2026', 'Học kỳ 1 năm 2025-2026', DATE_SUB(CURDATE(), INTERVAL 420 DAY), DATE_SUB(CURDATE(), INTERVAL 300 DAY), DATE_SUB(NOW(), INTERVAL 450 DAY), DATE_SUB(NOW(), INTERVAL 420 DAY), 'COMPLETED'),
('HK2-2025-2026', 'Học kỳ 2 năm 2025-2026', DATE_SUB(CURDATE(), INTERVAL 290 DAY), DATE_SUB(CURDATE(), INTERVAL 170 DAY), DATE_SUB(NOW(), INTERVAL 320 DAY), DATE_SUB(NOW(), INTERVAL 290 DAY), 'COMPLETED'),
('HK3-2025-2026', 'Học kỳ hè năm 2025-2026', DATE_SUB(CURDATE(), INTERVAL 160 DAY), DATE_SUB(CURDATE(), INTERVAL 140 DAY), DATE_SUB(NOW(), INTERVAL 180 DAY), DATE_SUB(NOW(), INTERVAL 160 DAY), 'COMPLETED'),
('HK1-2026-2027', 'Học kỳ 1 năm 2026-2027', DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_ADD(CURDATE(), INTERVAL 120 DAY), DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 90 DAY), 'REGISTRATION_OPEN'),
('HK2-2026-2027', 'Học kỳ 2 năm 2026-2027', DATE_ADD(CURDATE(), INTERVAL 140 DAY), DATE_ADD(CURDATE(), INTERVAL 270 DAY), DATE_ADD(NOW(), INTERVAL 120 DAY), DATE_ADD(NOW(), INTERVAL 140 DAY), 'PLANNED');

-- ------------------------------------------------------------
-- LỚP HỌC PHẦN
-- ------------------------------------------------------------
INSERT INTO course_sections (section_code, course_id, semester_id, lecturer_id, capacity, schedule, status) VALUES
('INT2204-01', (SELECT id FROM courses WHERE course_code='INT2204'), (SELECT id FROM semesters WHERE semester_code='HK1-2025-2026'), (SELECT id FROM lecturers WHERE lecturer_code='GV001'), 40, 'Thứ 2, 07:30 - 09:30, Phòng A2-301', 'COMPLETED'),
('INT3306-01', (SELECT id FROM courses WHERE course_code='INT3306'), (SELECT id FROM semesters WHERE semester_code='HK1-2025-2026'), (SELECT id FROM lecturers WHERE lecturer_code='GV002'), 40, 'Thứ 4, 09:00 - 11:00, Phòng B1-205', 'COMPLETED'),
('INT3401-01', (SELECT id FROM courses WHERE course_code='INT3401'), (SELECT id FROM semesters WHERE semester_code='HK1-2025-2026'), (SELECT id FROM lecturers WHERE lecturer_code='GV003'), 35, 'Thứ 3, 13:00 - 15:00, Phòng A1-102', 'COMPLETED'),
('MAT1013-01', (SELECT id FROM courses WHERE course_code='MAT1013'), (SELECT id FROM semesters WHERE semester_code='HK1-2025-2026'), (SELECT id FROM lecturers WHERE lecturer_code='GV004'), 45, 'Thứ 6, 09:00 - 11:00, Phòng A3-201', 'COMPLETED'),
('INT3102-01', (SELECT id FROM courses WHERE course_code='INT3102'), (SELECT id FROM semesters WHERE semester_code='HK1-2025-2026'), (SELECT id FROM lecturers WHERE lecturer_code='GV001'), 40, 'Thứ 5, 07:30 - 09:30, Phòng C1-105', 'COMPLETED'),
('INT3305-01', (SELECT id FROM courses WHERE course_code='INT3305'), (SELECT id FROM semesters WHERE semester_code='HK1-2025-2026'), (SELECT id FROM lecturers WHERE lecturer_code='GV005'), 35, 'Thứ 7, 07:30 - 09:30, Phòng C2-102', 'COMPLETED'),
('INT2204-02', (SELECT id FROM courses WHERE course_code='INT2204'), (SELECT id FROM semesters WHERE semester_code='HK2-2025-2026'), (SELECT id FROM lecturers WHERE lecturer_code='GV001'), 40, 'Thứ 2, 09:00 - 11:00, Phòng A2-302', 'COMPLETED'),
('INT3306-02', (SELECT id FROM courses WHERE course_code='INT3306'), (SELECT id FROM semesters WHERE semester_code='HK2-2025-2026'), (SELECT id FROM lecturers WHERE lecturer_code='GV002'), 40, 'Thứ 4, 13:00 - 15:00, Phòng B1-206', 'COMPLETED'),
('INT3507-01', (SELECT id FROM courses WHERE course_code='INT3507'), (SELECT id FROM semesters WHERE semester_code='HK2-2025-2026'), (SELECT id FROM lecturers WHERE lecturer_code='GV003'), 35, 'Thứ 3, 07:30 - 09:30, Phòng B2-104', 'COMPLETED'),
('INT3405-01', (SELECT id FROM courses WHERE course_code='INT3405'), (SELECT id FROM semesters WHERE semester_code='HK2-2025-2026'), (SELECT id FROM lecturers WHERE lecturer_code='GV004'), 35, 'Thứ 6, 13:00 - 15:00, Phòng C2-201', 'COMPLETED'),
('INT3202-01', (SELECT id FROM courses WHERE course_code='INT3202'), (SELECT id FROM semesters WHERE semester_code='HK2-2025-2026'), (SELECT id FROM lecturers WHERE lecturer_code='GV002'), 40, 'Thứ 5, 09:00 - 11:00, Phòng A1-203', 'COMPLETED'),
('INT3303-01', (SELECT id FROM courses WHERE course_code='INT3303'), (SELECT id FROM semesters WHERE semester_code='HK3-2025-2026'), (SELECT id FROM lecturers WHERE lecturer_code='GV005'), 35, 'Thứ 2, 07:30 - 09:30, Phòng C1-201', 'COMPLETED'),
('INT3304-01', (SELECT id FROM courses WHERE course_code='INT3304'), (SELECT id FROM semesters WHERE semester_code='HK3-2025-2026'), (SELECT id FROM lecturers WHERE lecturer_code='GV006'), 30, 'Thứ 4, 07:30 - 09:30, Phòng C1-202', 'COMPLETED'),
('INT2204-03', (SELECT id FROM courses WHERE course_code='INT2204'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV001'), 40, 'Thứ 2, 07:30 - 09:30, Phòng A2-301', 'OPEN'),
('INT3306-03', (SELECT id FROM courses WHERE course_code='INT3306'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV002'), 40, 'Thứ 4, 09:00 - 11:00, Phòng B1-205', 'OPEN'),
('INT3401-02', (SELECT id FROM courses WHERE course_code='INT3401'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV003'), 35, 'Thứ 3, 13:00 - 15:00, Phòng A1-102', 'OPEN'),
('INT3507-02', (SELECT id FROM courses WHERE course_code='INT3507'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV004'), 35, 'Thứ 5, 07:30 - 09:30, Phòng B2-104', 'OPEN'),
('MAT1013-02', (SELECT id FROM courses WHERE course_code='MAT1013'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV001'), 45, 'Thứ 6, 09:00 - 11:00, Phòng A3-201', 'OPEN'),
('INT3102-02', (SELECT id FROM courses WHERE course_code='INT3102'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV002'), 40, 'Thứ 5, 13:00 - 15:00, Phòng C1-105', 'OPEN'),
('INT3405-02', (SELECT id FROM courses WHERE course_code='INT3405'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV003'), 35, 'Thứ 6, 07:30 - 09:30, Phòng C2-201', 'OPEN'),
('INT3301-01', (SELECT id FROM courses WHERE course_code='INT3301'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV004'), 30, 'Thứ 7, 09:00 - 11:00, Phòng A1-105', 'OPEN'),
('INT3303-02', (SELECT id FROM courses WHERE course_code='INT3303'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV005'), 40, 'Thứ 3, 07:30 - 09:30, Phòng C1-201', 'OPEN'),
('INT3304-02', (SELECT id FROM courses WHERE course_code='INT3304'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV006'), 30, 'Thứ 5, 09:00 - 11:00, Phòng C1-202', 'OPEN'),
('INT3305-02', (SELECT id FROM courses WHERE course_code='INT3305'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV005'), 35, 'Thứ 7, 13:00 - 15:00, Phòng C2-102', 'OPEN'),
('INT3202-02', (SELECT id FROM courses WHERE course_code='INT3202'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV002'), 40, 'Thứ 4, 07:30 - 09:30, Phòng A1-203', 'DRAFT'),
('INT3501-01', (SELECT id FROM courses WHERE course_code='INT3501'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV003'), 35, 'Thứ 3, 09:00 - 11:00, Phòng B2-202', 'CLOSED'),
('INT3501-02', (SELECT id FROM courses WHERE course_code='INT3501'), (SELECT id FROM semesters WHERE semester_code='HK1-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV004'), 35, 'Thứ 2, 13:00 - 15:00, Phòng B2-203', 'CANCELLED'),
('INT2204-04', (SELECT id FROM courses WHERE course_code='INT2204'), (SELECT id FROM semesters WHERE semester_code='HK2-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV001'), 40, 'Thứ 2, 07:30 - 09:30, Phòng A2-301', 'DRAFT'),
('INT3306-04', (SELECT id FROM courses WHERE course_code='INT3306'), (SELECT id FROM semesters WHERE semester_code='HK2-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV002'), 40, 'Thứ 4, 09:00 - 11:00, Phòng B1-205', 'DRAFT'),
('INT3401-03', (SELECT id FROM courses WHERE course_code='INT3401'), (SELECT id FROM semesters WHERE semester_code='HK2-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV003'), 35, 'Thứ 3, 13:00 - 15:00, Phòng A1-102', 'DRAFT'),
('INT3303-03', (SELECT id FROM courses WHERE course_code='INT3303'), (SELECT id FROM semesters WHERE semester_code='HK2-2026-2027'), (SELECT id FROM lecturers WHERE lecturer_code='GV005'), 40, 'Thứ 3, 07:30 - 09:30, Phòng C1-201', 'DRAFT');

-- ------------------------------------------------------------
-- ĐĂNG KÝ HỌC PHẦN
-- ------------------------------------------------------------
INSERT INTO enrollments (student_id, section_id, status, registered_at, withdrawn_at) VALUES
((SELECT id FROM students WHERE student_code='SV001'), (SELECT id FROM course_sections WHERE section_code='INT2204-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 430 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV001'), (SELECT id FROM course_sections WHERE section_code='INT3306-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 430 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV002'), (SELECT id FROM course_sections WHERE section_code='INT2204-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 429 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV002'), (SELECT id FROM course_sections WHERE section_code='INT3401-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 429 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV003'), (SELECT id FROM course_sections WHERE section_code='INT3306-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 428 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV003'), (SELECT id FROM course_sections WHERE section_code='MAT1013-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 428 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV004'), (SELECT id FROM course_sections WHERE section_code='INT3102-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 427 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV005'), (SELECT id FROM course_sections WHERE section_code='INT2204-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 427 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV006'), (SELECT id FROM course_sections WHERE section_code='INT3306-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 426 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV007'), (SELECT id FROM course_sections WHERE section_code='INT3401-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 426 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV008'), (SELECT id FROM course_sections WHERE section_code='MAT1013-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 425 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV009'), (SELECT id FROM course_sections WHERE section_code='INT3102-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 425 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV010'), (SELECT id FROM course_sections WHERE section_code='INT2204-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 424 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV001'), (SELECT id FROM course_sections WHERE section_code='INT3305-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 424 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV006'), (SELECT id FROM course_sections WHERE section_code='INT3305-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 423 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV011'), (SELECT id FROM course_sections WHERE section_code='INT3305-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 423 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV001'), (SELECT id FROM course_sections WHERE section_code='INT2204-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 300 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV001'), (SELECT id FROM course_sections WHERE section_code='INT3507-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 300 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV002'), (SELECT id FROM course_sections WHERE section_code='INT3306-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 299 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV002'), (SELECT id FROM course_sections WHERE section_code='INT3405-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 299 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV003'), (SELECT id FROM course_sections WHERE section_code='INT3202-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 298 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV004'), (SELECT id FROM course_sections WHERE section_code='INT3306-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 298 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV005'), (SELECT id FROM course_sections WHERE section_code='INT3507-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 297 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV006'), (SELECT id FROM course_sections WHERE section_code='INT3405-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 297 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV007'), (SELECT id FROM course_sections WHERE section_code='INT2204-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 296 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV008'), (SELECT id FROM course_sections WHERE section_code='INT3202-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 296 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV009'), (SELECT id FROM course_sections WHERE section_code='INT3507-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 295 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV010'), (SELECT id FROM course_sections WHERE section_code='INT3405-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 295 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV001'), (SELECT id FROM course_sections WHERE section_code='INT3303-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 175 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV002'), (SELECT id FROM course_sections WHERE section_code='INT3303-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 175 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV003'), (SELECT id FROM course_sections WHERE section_code='INT3304-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 174 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV004'), (SELECT id FROM course_sections WHERE section_code='INT3304-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 174 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV011'), (SELECT id FROM course_sections WHERE section_code='INT3303-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 173 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV012'), (SELECT id FROM course_sections WHERE section_code='INT3304-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 173 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV001'), (SELECT id FROM course_sections WHERE section_code='INT2204-03'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 10 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV001'), (SELECT id FROM course_sections WHERE section_code='INT3306-03'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 10 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV001'), (SELECT id FROM course_sections WHERE section_code='INT3305-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 10 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV002'), (SELECT id FROM course_sections WHERE section_code='INT2204-03'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 9 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV002'), (SELECT id FROM course_sections WHERE section_code='INT3401-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 9 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV002'), (SELECT id FROM course_sections WHERE section_code='INT3102-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 9 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV003'), (SELECT id FROM course_sections WHERE section_code='INT3306-03'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 8 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV003'), (SELECT id FROM course_sections WHERE section_code='MAT1013-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 8 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV004'), (SELECT id FROM course_sections WHERE section_code='INT3507-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 7 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV004'), (SELECT id FROM course_sections WHERE section_code='INT3405-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 7 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV005'), (SELECT id FROM course_sections WHERE section_code='INT2204-03'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 6 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV005'), (SELECT id FROM course_sections WHERE section_code='INT3301-01'), 'WITHDRAWN', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
((SELECT id FROM students WHERE student_code='SV006'), (SELECT id FROM course_sections WHERE section_code='INT3306-03'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 6 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV006'), (SELECT id FROM course_sections WHERE section_code='INT3102-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 5 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV007'), (SELECT id FROM course_sections WHERE section_code='INT3401-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 5 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV007'), (SELECT id FROM course_sections WHERE section_code='INT3501-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 5 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV008'), (SELECT id FROM course_sections WHERE section_code='INT3507-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 4 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV008'), (SELECT id FROM course_sections WHERE section_code='INT3405-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 4 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV009'), (SELECT id FROM course_sections WHERE section_code='INT2204-03'), 'WITHDRAWN', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
((SELECT id FROM students WHERE student_code='SV009'), (SELECT id FROM course_sections WHERE section_code='INT3301-01'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 3 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV010'), (SELECT id FROM course_sections WHERE section_code='INT3306-03'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV010'), (SELECT id FROM course_sections WHERE section_code='INT3401-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV010'), (SELECT id FROM course_sections WHERE section_code='INT3303-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV011'), (SELECT id FROM course_sections WHERE section_code='INT3303-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV011'), (SELECT id FROM course_sections WHERE section_code='INT3304-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV011'), (SELECT id FROM course_sections WHERE section_code='INT3305-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV012'), (SELECT id FROM course_sections WHERE section_code='INT3303-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
((SELECT id FROM students WHERE student_code='SV012'), (SELECT id FROM course_sections WHERE section_code='INT3305-02'), 'ENROLLED', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

-- ------------------------------------------------------------
-- ĐIỂM SỐ
-- ------------------------------------------------------------
INSERT INTO grades (enrollment_id, component_score, final_score, updated_by, updated_at) VALUES
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV001' AND cs.section_code='INT2204-01'), 8.00, 8.50, 'lecturer', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV001' AND cs.section_code='INT3306-01'), 9.00, 9.20, 'lecturer2', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV002' AND cs.section_code='INT2204-01'), 7.50, 8.00, 'lecturer', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV002' AND cs.section_code='INT3401-01'), 8.50, 9.00, 'lecturer3', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV003' AND cs.section_code='INT3306-01'), 7.00, 7.50, 'lecturer2', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV003' AND cs.section_code='MAT1013-01'), 8.00, 8.30, 'lecturer4', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV004' AND cs.section_code='INT3102-01'), 6.50, 7.00, 'lecturer', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV005' AND cs.section_code='INT2204-01'), 9.50, 9.60, 'lecturer', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV006' AND cs.section_code='INT3306-01'), 5.50, 6.00, 'lecturer2', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV007' AND cs.section_code='INT3401-01'), 8.00, 8.20, 'lecturer3', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV008' AND cs.section_code='MAT1013-01'), 6.00, 6.50, 'lecturer4', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV009' AND cs.section_code='INT3102-01'), 7.50, 8.00, 'lecturer', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV010' AND cs.section_code='INT2204-01'), 8.50, 8.80, 'lecturer', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV001' AND cs.section_code='INT3305-01'), 8.80, 9.00, 'lecturer5', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV006' AND cs.section_code='INT3305-01'), 6.80, 7.20, 'lecturer5', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV011' AND cs.section_code='INT3305-01'), 9.20, 9.40, 'lecturer5', DATE_SUB(NOW(), INTERVAL 310 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV001' AND cs.section_code='INT2204-02'), 9.00, 9.10, 'lecturer', DATE_SUB(NOW(), INTERVAL 180 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV001' AND cs.section_code='INT3507-01'), 8.00, 8.40, 'lecturer3', DATE_SUB(NOW(), INTERVAL 180 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV002' AND cs.section_code='INT3306-02'), 7.50, 7.80, 'lecturer2', DATE_SUB(NOW(), INTERVAL 180 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV002' AND cs.section_code='INT3405-01'), 8.00, 8.30, 'lecturer4', DATE_SUB(NOW(), INTERVAL 180 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV003' AND cs.section_code='INT3202-01'), 6.50, 7.20, 'lecturer2', DATE_SUB(NOW(), INTERVAL 180 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV004' AND cs.section_code='INT3306-02'), 9.00, 9.00, 'lecturer2', DATE_SUB(NOW(), INTERVAL 180 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV005' AND cs.section_code='INT3507-01'), 8.50, 8.70, 'lecturer3', DATE_SUB(NOW(), INTERVAL 180 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV006' AND cs.section_code='INT3405-01'), 7.00, 7.50, 'lecturer4', DATE_SUB(NOW(), INTERVAL 180 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV007' AND cs.section_code='INT2204-02'), 8.00, 8.10, 'lecturer', DATE_SUB(NOW(), INTERVAL 180 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV008' AND cs.section_code='INT3202-01'), 5.00, 5.50, 'lecturer2', DATE_SUB(NOW(), INTERVAL 180 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV009' AND cs.section_code='INT3507-01'), 9.50, 9.70, 'lecturer3', DATE_SUB(NOW(), INTERVAL 180 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV010' AND cs.section_code='INT3405-01'), 6.00, 6.50, 'lecturer4', DATE_SUB(NOW(), INTERVAL 180 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV001' AND cs.section_code='INT3303-01'), 8.50, 8.80, 'lecturer5', DATE_SUB(NOW(), INTERVAL 145 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV002' AND cs.section_code='INT3303-01'), 7.80, 8.10, 'lecturer5', DATE_SUB(NOW(), INTERVAL 145 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV003' AND cs.section_code='INT3304-01'), 8.00, 8.30, 'lecturer6', DATE_SUB(NOW(), INTERVAL 145 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV004' AND cs.section_code='INT3304-01'), 7.00, 7.40, 'lecturer6', DATE_SUB(NOW(), INTERVAL 145 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV011' AND cs.section_code='INT3303-01'), 9.00, 9.20, 'lecturer5', DATE_SUB(NOW(), INTERVAL 145 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV012' AND cs.section_code='INT3304-01'), 8.20, 8.50, 'lecturer6', DATE_SUB(NOW(), INTERVAL 145 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV001' AND cs.section_code='INT2204-03'), 8.50, NULL, 'lecturer', DATE_SUB(NOW(), INTERVAL 1 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV002' AND cs.section_code='INT2204-03'), 7.00, NULL, 'lecturer', DATE_SUB(NOW(), INTERVAL 1 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV002' AND cs.section_code='INT3401-02'), 9.00, NULL, 'lecturer3', DATE_SUB(NOW(), INTERVAL 1 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV003' AND cs.section_code='INT3306-03'), 8.00, NULL, 'lecturer2', DATE_SUB(NOW(), INTERVAL 1 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV004' AND cs.section_code='INT3507-02'), 7.50, NULL, 'lecturer4', DATE_SUB(NOW(), INTERVAL 1 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV010' AND cs.section_code='INT3303-02'), 8.50, NULL, 'lecturer5', DATE_SUB(NOW(), INTERVAL 1 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV011' AND cs.section_code='INT3303-02'), 8.00, NULL, 'lecturer5', DATE_SUB(NOW(), INTERVAL 1 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV011' AND cs.section_code='INT3304-02'), 9.00, NULL, 'lecturer6', DATE_SUB(NOW(), INTERVAL 1 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV012' AND cs.section_code='INT3303-02'), 7.50, NULL, 'lecturer5', DATE_SUB(NOW(), INTERVAL 1 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV012' AND cs.section_code='INT3305-02'), 7.80, NULL, 'lecturer5', DATE_SUB(NOW(), INTERVAL 1 DAY)),
((SELECT e.id FROM enrollments e JOIN students s ON e.student_id=s.id JOIN course_sections cs ON e.section_id=cs.id WHERE s.student_code='SV001' AND cs.section_code='INT3305-02'), 8.60, NULL, 'lecturer5', DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ------------------------------------------------------------
-- SỔ ĐIỂM (GRADEBOOK)
-- ------------------------------------------------------------
INSERT INTO gradebooks (section_id, status, locked_at, locked_by) VALUES
((SELECT id FROM course_sections WHERE section_code='INT2204-01'), 'LOCKED', DATE_SUB(NOW(), INTERVAL 295 DAY), 'admin'),
((SELECT id FROM course_sections WHERE section_code='INT3306-01'), 'LOCKED', DATE_SUB(NOW(), INTERVAL 295 DAY), 'admin'),
((SELECT id FROM course_sections WHERE section_code='INT3401-01'), 'LOCKED', DATE_SUB(NOW(), INTERVAL 295 DAY), 'admin'),
((SELECT id FROM course_sections WHERE section_code='MAT1013-01'), 'LOCKED', DATE_SUB(NOW(), INTERVAL 295 DAY), 'admin'),
((SELECT id FROM course_sections WHERE section_code='INT3102-01'), 'LOCKED', DATE_SUB(NOW(), INTERVAL 295 DAY), 'admin'),
((SELECT id FROM course_sections WHERE section_code='INT3305-01'), 'LOCKED', DATE_SUB(NOW(), INTERVAL 295 DAY), 'admin'),
((SELECT id FROM course_sections WHERE section_code='INT2204-02'), 'LOCKED', DATE_SUB(NOW(), INTERVAL 160 DAY), 'admin'),
((SELECT id FROM course_sections WHERE section_code='INT3306-02'), 'LOCKED', DATE_SUB(NOW(), INTERVAL 160 DAY), 'admin'),
((SELECT id FROM course_sections WHERE section_code='INT3507-01'), 'LOCKED', DATE_SUB(NOW(), INTERVAL 160 DAY), 'admin'),
((SELECT id FROM course_sections WHERE section_code='INT3405-01'), 'LOCKED', DATE_SUB(NOW(), INTERVAL 160 DAY), 'admin'),
((SELECT id FROM course_sections WHERE section_code='INT3202-01'), 'LOCKED', DATE_SUB(NOW(), INTERVAL 160 DAY), 'admin'),
((SELECT id FROM course_sections WHERE section_code='INT3303-01'), 'LOCKED', DATE_SUB(NOW(), INTERVAL 135 DAY), 'admin'),
((SELECT id FROM course_sections WHERE section_code='INT3304-01'), 'LOCKED', DATE_SUB(NOW(), INTERVAL 135 DAY), 'admin'),
((SELECT id FROM course_sections WHERE section_code='INT2204-03'), 'OPEN', NULL, NULL),
((SELECT id FROM course_sections WHERE section_code='INT3306-03'), 'OPEN', NULL, NULL),
((SELECT id FROM course_sections WHERE section_code='INT3401-02'), 'OPEN', NULL, NULL),
((SELECT id FROM course_sections WHERE section_code='INT3507-02'), 'OPEN', NULL, NULL),
((SELECT id FROM course_sections WHERE section_code='MAT1013-02'), 'OPEN', NULL, NULL),
((SELECT id FROM course_sections WHERE section_code='INT3102-02'), 'OPEN', NULL, NULL),
((SELECT id FROM course_sections WHERE section_code='INT3405-02'), 'OPEN', NULL, NULL),
((SELECT id FROM course_sections WHERE section_code='INT3301-01'), 'OPEN', NULL, NULL),
((SELECT id FROM course_sections WHERE section_code='INT3303-02'), 'OPEN', NULL, NULL),
((SELECT id FROM course_sections WHERE section_code='INT3304-02'), 'OPEN', NULL, NULL),
((SELECT id FROM course_sections WHERE section_code='INT3305-02'), 'OPEN', NULL, NULL),
((SELECT id FROM course_sections WHERE section_code='INT3501-01'), 'OPEN', NULL, NULL);

-- Các bảng điểm của học kỳ đã kết thúc được duyệt và công bố để sinh viên tra cứu.
UPDATE gradebooks
SET status = 'PUBLISHED',
    approved_at = DATE_SUB(NOW(), INTERVAL 290 DAY),
    approved_by = 'admin',
    published_at = DATE_SUB(NOW(), INTERVAL 289 DAY),
    published_by = 'admin'
WHERE status = 'LOCKED';


-- Cấu hình thành phần điểm cho bảng điểm đã công bố (mẫu 10%/20%/70%)
INSERT IGNORE INTO grade_components (gradebook_id, component_name, weight, max_score, component_type, display_order, is_required, created_at, updated_at)
SELECT g.id, 'Chuyên cần', 10.00, 10.00, 'ATTENDANCE', 1, 1, NOW(), NOW() FROM gradebooks g WHERE g.status = 'PUBLISHED';
INSERT IGNORE INTO grade_components (gradebook_id, component_name, weight, max_score, component_type, display_order, is_required, created_at, updated_at)
SELECT g.id, 'Giữa kỳ', 20.00, 10.00, 'MIDTERM', 2, 1, NOW(), NOW() FROM gradebooks g WHERE g.status = 'PUBLISHED';
INSERT IGNORE INTO grade_components (gradebook_id, component_name, weight, max_score, component_type, display_order, is_required, created_at, updated_at)
SELECT g.id, 'Cuối kỳ', 70.00, 10.00, 'FINAL', 3, 1, NOW(), NOW() FROM gradebooks g WHERE g.status = 'PUBLISHED';

-- Điểm thành phần mẫu cho sinh viên đang học ở lớp công bố
INSERT IGNORE INTO grade_component_scores (grade_component_id, enrollment_id, score, updated_by, updated_at)
SELECT gc.id, e.id,
       ROUND(6.5 + (RAND()*(4.0)), 2),
       'lecturer', NOW()
FROM grade_components gc
JOIN gradebooks g ON gc.gradebook_id = g.id
JOIN enrollments e ON e.section_id = g.section_id
WHERE g.status = 'PUBLISHED' AND e.status = 'ENROLLED' AND gc.component_name = 'Chuyên cần';

INSERT IGNORE INTO grade_component_scores (grade_component_id, enrollment_id, score, updated_by, updated_at)
SELECT gc.id, e.id,
       ROUND(5.5 + (RAND()*(4.5)), 2),
       'lecturer', NOW()
FROM grade_components gc
JOIN gradebooks g ON gc.gradebook_id = g.id
JOIN enrollments e ON e.section_id = g.section_id
WHERE g.status = 'PUBLISHED' AND e.status = 'ENROLLED' AND gc.component_name = 'Giữa kỳ';

INSERT IGNORE INTO grade_component_scores (grade_component_id, enrollment_id, score, updated_by, updated_at)
SELECT gc.id, e.id,
       ROUND(5.0 + (RAND()*(5.0)), 2),
       'lecturer', NOW()
FROM grade_components gc
JOIN gradebooks g ON gc.gradebook_id = g.id
JOIN enrollments e ON e.section_id = g.section_id
WHERE g.status = 'PUBLISHED' AND e.status = 'ENROLLED' AND gc.component_name = 'Cuối kỳ';

-- Cập nhật điểm tổng kết theo trọng số (thang 10)
UPDATE grades gr
JOIN enrollments e ON gr.enrollment_id = e.id
JOIN gradebooks g ON e.section_id = g.section_id
JOIN (
  SELECT e2.id AS enrollment_id,
         SUM(CASE WHEN gc.max_score = 0 THEN 0 ELSE gcs.score * gc.weight / gc.max_score / 100.0 END) * 10 AS calc_final,
         SUM(CASE WHEN gc.max_score = 0 THEN 0 ELSE gcs.score * gc.weight / gc.max_score / 100.0 END) * 10 AS calc_comp
  FROM enrollments e2
  JOIN gradebooks g2 ON e2.section_id = g2.section_id
  JOIN grade_components gc ON gc.gradebook_id = g2.id
  JOIN grade_component_scores gcs ON gcs.grade_component_id = gc.id AND gcs.enrollment_id = e2.id
  WHERE g2.status = 'PUBLISHED' AND e2.status = 'ENROLLED'
  GROUP BY e2.id
  HAVING COUNT(DISTINCT gc.id) = 3
) calc ON calc.enrollment_id = e.id
SET gr.component_score = ROUND(calc.calc_comp, 2),
    gr.final_score = ROUND(calc.calc_final, 2),
    gr.updated_by = 'system',
    gr.updated_at = NOW()
WHERE g.status = 'PUBLISHED';


package com.example.student_management.entity.academic;

public enum EnrollmentStatus {
    // Đơn đăng ký mới, chờ quản trị viên duyệt.
    PENDING,
    // Đã được duyệt - trạng thái chính thức (giữ tên cũ để tương thích dữ liệu).
    ENROLLED,
    // Bị từ chối khi duyệt.
    REJECTED,
    // Sinh viên đã hủy đăng ký.
    WITHDRAWN
}

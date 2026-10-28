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
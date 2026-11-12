package com.example.student_management.service.auth;

import com.example.student_management.entity.auth.Student;
import com.example.student_management.dto.auth.StudentAccountCreateRequest;

import java.util.List;
import java.util.Optional;

public interface StudentService {
    List<Student> findAll();
    Optional<Student> findById(Long id);
    Student createAccount(StudentAccountCreateRequest request);
    Student update(Long id, Student student);
    void delete(Long id);
}

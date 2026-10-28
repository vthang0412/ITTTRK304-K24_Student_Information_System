package com.example.student_management.service;



import com.example.student_management.entity.Student;
import java.util.List;
import java.util.Optional;

public interface StudentService {
    List<Student> findAll();
    Optional<Student> findById(Long id);
    Student create(Student student);
    Student update(Long id, Student student);
}

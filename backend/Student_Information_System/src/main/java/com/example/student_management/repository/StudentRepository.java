package com.example.student_management.repository;



import com.example.student_management.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByStudentCode(String studentCode);
    Optional<Student> findByUserId(Long userId);
    boolean existsByStudentCode(String studentCode);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);
}

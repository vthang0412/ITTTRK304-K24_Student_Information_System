package com.example.student_management.repository.auth;



import com.example.student_management.entity.auth.Lecturer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LecturerRepository extends JpaRepository<Lecturer, Long> {
    Optional<Lecturer> findByLecturerCode(String lecturerCode);
    Optional<Lecturer> findByUserId(Long userId);
    boolean existsByLecturerCode(String lecturerCode);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);
}

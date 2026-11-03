package com.example.student_management.repository.academic;



import com.example.student_management.entity.academic.Semester;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    Optional<Semester> findBySemesterCode(String semesterCode);
    boolean existsBySemesterCode(String semesterCode);
}

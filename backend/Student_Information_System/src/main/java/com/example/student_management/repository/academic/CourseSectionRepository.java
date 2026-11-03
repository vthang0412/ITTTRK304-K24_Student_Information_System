package com.example.student_management.repository.academic;



import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.academic.CourseSectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {
    Optional<CourseSection> findBySectionCode(String sectionCode);
    boolean existsBySectionCode(String sectionCode);
    List<CourseSection> findBySemesterId(Long semesterId);
    List<CourseSection> findByLecturerId(Long lecturerId);
    List<CourseSection> findByStatus(CourseSectionStatus status);
}

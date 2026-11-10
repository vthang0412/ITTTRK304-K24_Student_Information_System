package com.example.student_management.repository.academic;

import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.academic.CourseSectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {
    Optional<CourseSection> findBySectionCode(String sectionCode);
    boolean existsBySectionCode(String sectionCode);
    boolean existsByCourseId(Long courseId);
    boolean existsBySemesterId(Long semesterId);
    List<CourseSection> findByCourseId(Long courseId);
    List<CourseSection> findBySemesterId(Long semesterId);
    List<CourseSection> findByLecturerId(Long lecturerId);
    List<CourseSection> findByStatus(CourseSectionStatus status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT cs FROM CourseSection cs WHERE cs.id = :id")
    Optional<CourseSection> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}

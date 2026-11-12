package com.example.student_management.repository.grade;



import com.example.student_management.entity.grade.Gradebook;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GradebookRepository extends JpaRepository<Gradebook, Long> {
    Optional<Gradebook> findByCourseSectionId(Long sectionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Gradebook g where g.id = :id")
    Optional<Gradebook> findByIdForUpdate(@Param("id") Long id);

    @Query("select distinct g.courseSection.id from Gradebook g")
    List<Long> findCourseSectionIdsWithGradebooks();
}

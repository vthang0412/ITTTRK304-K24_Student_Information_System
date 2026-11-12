package com.example.student_management.repository.academic;

import com.example.student_management.entity.academic.CourseSectionSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CourseSectionScheduleRepository extends JpaRepository<CourseSectionSchedule, Long> {
    List<CourseSectionSchedule> findByCourseSectionIdInOrderByEffectiveFromAsc(Collection<Long> sectionIds);

    List<CourseSectionSchedule> findByCourseSectionIdOrderByEffectiveFromAsc(Long sectionId);

    boolean existsByCourseSectionId(Long sectionId);

    @Query("select distinct s.courseSection.id from CourseSectionSchedule s")
    List<Long> findCourseSectionIdsWithScheduleHistory();
}

package com.example.student_management.repository.academic;

import com.example.student_management.entity.academic.RecurringCourseSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface RecurringCourseScheduleRepository extends JpaRepository<RecurringCourseSchedule, Long> {
    List<RecurringCourseSchedule> findByCourseSectionIdAndCancelledFromIsNullOrderByDayOfWeekAscStartTimeAsc(
            Long sectionId);

    List<RecurringCourseSchedule> findByCourseSectionIdOrderByDayOfWeekAscStartTimeAsc(Long sectionId);

    boolean existsByCourseSectionId(Long sectionId);

    List<RecurringCourseSchedule> findByCourseSectionIdInOrderByStartDateAsc(Collection<Long> sectionIds);

    @Query("select distinct s.courseSection.id from RecurringCourseSchedule s")
    List<Long> findCourseSectionIdsWithScheduleHistory();
}

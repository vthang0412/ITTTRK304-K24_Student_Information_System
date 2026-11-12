package com.example.student_management.repository.academic;

import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.academic.CourseSectionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {
    Optional<CourseSection> findBySectionCode(String sectionCode);
    boolean existsBySectionCode(String sectionCode);
    boolean existsByCourseId(Long courseId);
    boolean existsBySemesterId(Long semesterId);
    @Query("select distinct s.course.id from CourseSection s")
    List<Long> findCourseIdsWithSections();
    @Query("select distinct s.semester.id from CourseSection s")
    List<Long> findSemesterIdsWithSections();
    List<CourseSection> findByCourseId(Long courseId);
    List<CourseSection> findBySemesterId(Long semesterId);
    List<CourseSection> findByLecturerId(Long lecturerId);
    List<CourseSection> findByStatus(CourseSectionStatus status);

    @Query("""
            select distinct s from CourseSection s
            join fetch s.course c
            join fetch s.semester sem
            left join fetch s.lecturer l
            where (:semesterId is null or sem.id = :semesterId)
              and (:sectionId is null or s.id = :sectionId)
              and (:lecturerId is null or l.id = :lecturerId)
            order by s.sectionCode
            """)
    List<CourseSection> findForAdminTimetable(@Param("semesterId") Long semesterId,
                                              @Param("sectionId") Long sectionId,
                                              @Param("lecturerId") Long lecturerId);

    // Khóa ghi để tuần tự hóa việc kiểm tra sức chứa khi đăng ký/duyệt,
    // tránh tình trạng hai giao dịch cùng đọc số chỗ trống rồi cùng ghi (oversell).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CourseSection s where s.id = :id")
    Optional<CourseSection> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CourseSection s order by s.id")
    List<CourseSection> findAllForUpdate();
}

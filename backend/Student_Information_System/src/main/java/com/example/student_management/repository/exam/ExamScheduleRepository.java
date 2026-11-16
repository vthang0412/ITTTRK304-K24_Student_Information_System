package com.example.student_management.repository.exam;

import com.example.student_management.entity.exam.ExamSchedule;
import com.example.student_management.entity.exam.ExamScheduleStatus;
import com.example.student_management.entity.exam.ExamType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long>, JpaSpecificationExecutor<ExamSchedule> {

    boolean existsByRoomId(Long roomId);

    List<ExamSchedule> findByRoomIdAndExamDateAndStatusNot(Long roomId, LocalDate examDate, ExamScheduleStatus status);

    List<ExamSchedule> findByExamDateAndStatusNot(LocalDate examDate, ExamScheduleStatus status);

    List<ExamSchedule> findByCourseSectionIdIn(List<Long> sectionIds);

    List<ExamSchedule> findByCourseSectionIdInAndStatusNot(List<Long> sectionIds, ExamScheduleStatus status);

    @Query("SELECT s FROM ExamSchedule s " +
            "LEFT JOIN s.courseSection cs " +
            "LEFT JOIN cs.course c " +
            "LEFT JOIN cs.semester sem " +
            "LEFT JOIN s.room r " +
            "WHERE " +
            "(:semesterId IS NULL OR sem.id = :semesterId) AND " +

            "(:courseId IS NULL OR c.id = :courseId) AND " +
            "(:status IS NULL OR s.status = :status) AND " +
            "(:examType IS NULL OR s.examType = :examType) AND " +
            "(:fromDate IS NULL OR s.examDate >= :fromDate) AND " +
            "(:toDate IS NULL OR s.examDate <= :toDate) AND " +
            "(:keyword IS NULL OR " +
            "LOWER(c.courseCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.courseName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(cs.sectionCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.roomCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ExamSchedule> searchSchedules(
            @Param("keyword") String keyword,
            @Param("semesterId") Long semesterId,
            @Param("courseId") Long courseId,
            @Param("status") ExamScheduleStatus status,
            @Param("examType") ExamType examType,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );
}


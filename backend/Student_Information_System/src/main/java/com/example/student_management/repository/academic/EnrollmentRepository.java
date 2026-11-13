package com.example.student_management.repository.academic;

import com.example.student_management.entity.academic.Enrollment;
import com.example.student_management.entity.academic.EnrollmentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentId(Long studentId);
    List<Enrollment> findByStudentIdOrderByRegisteredAtDesc(Long studentId);
    List<Enrollment> findByCourseSectionId(Long sectionId);
    List<Enrollment> findByCourseSectionIdInAndStatus(Collection<Long> sectionIds, EnrollmentStatus status);
    @EntityGraph(attributePaths = {"student", "courseSection", "courseSection.course", "courseSection.semester"})
    List<Enrollment> findByStatusOrderByRegisteredAtAsc(EnrollmentStatus status);
    @Query("""
            select e from Enrollment e
            join fetch e.student student
            join fetch e.courseSection section
            join fetch section.course course
            join fetch section.semester semester
            order by e.registeredAt desc
            """)
    List<Enrollment> findAllAdminDetails();
    @Query("""
            select e from Enrollment e
            join fetch e.student student
            join fetch e.courseSection section
            join fetch section.course course
            join fetch section.semester semester
            where e.id = :id
            """)
    Optional<Enrollment> findAdminDetailsById(@Param("id") Long id);
    @Query("""
            select e from Enrollment e
            join fetch e.student student
            join fetch e.courseSection section
            join fetch section.course course
            join fetch section.semester semester
            where e.id in :ids
            """)
    List<Enrollment> findAdminDetailsByIdIn(@Param("ids") Collection<Long> ids);
    boolean existsByCourseSectionId(Long sectionId);
    @Query("select distinct e.courseSection.id from Enrollment e")
    List<Long> findCourseSectionIdsWithEnrollmentHistory();
    Optional<Enrollment> findByStudentIdAndCourseSectionId(Long studentId, Long sectionId);
    long countByCourseSectionIdAndStatus(Long sectionId, EnrollmentStatus status);
    long countByCourseSectionIdAndStatusIn(Long sectionId, Collection<EnrollmentStatus> statuses);
    long countByCourseSectionIdAndStatusInAndIdNot(Long sectionId,
                                                    Collection<EnrollmentStatus> statuses,
                                                    Long enrollmentId);

    @Query("""
            select e.courseSection.id, count(e)
            from Enrollment e
            where e.courseSection.id in :sectionIds and e.status in :statuses
            group by e.courseSection.id
            """)
    List<Object[]> countActiveByCourseSectionIds(@Param("sectionIds") Collection<Long> sectionIds,
                                                 @Param("statuses") Collection<EnrollmentStatus> statuses);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update Enrollment e
            set e.status = :approvedStatus, e.approvedAt = :approvedAt, e.approvedBy = :admin,
                e.rejectedAt = null, e.rejectionReason = null, e.rejectedBy = null
            where e.id = :enrollmentId and e.status = :pendingStatus
            """)
    int approveIfPending(@Param("enrollmentId") Long enrollmentId,
                         @Param("pendingStatus") EnrollmentStatus pendingStatus,
                         @Param("approvedStatus") EnrollmentStatus approvedStatus,
                         @Param("approvedAt") java.time.LocalDateTime approvedAt,
                         @Param("admin") com.example.student_management.entity.auth.User admin);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update Enrollment e
            set e.status = :rejectedStatus, e.rejectedAt = :rejectedAt,
                e.rejectionReason = :reason, e.rejectedBy = :admin,
                e.approvedAt = null, e.approvedBy = null
            where e.id = :enrollmentId and e.status = :pendingStatus
            """)
    int rejectIfPending(@Param("enrollmentId") Long enrollmentId,
                        @Param("pendingStatus") EnrollmentStatus pendingStatus,
                        @Param("rejectedStatus") EnrollmentStatus rejectedStatus,
                        @Param("rejectedAt") java.time.LocalDateTime rejectedAt,
                        @Param("reason") String reason,
                        @Param("admin") com.example.student_management.entity.auth.User admin);

    @Query("""
            select distinct e from Enrollment e
            join fetch e.courseSection s
            join fetch s.course c
            join fetch s.semester sem
            left join fetch s.lecturer l
            where e.student.id = :studentId and e.status = :status
            """)
    List<Enrollment> findForTimetable(@Param("studentId") Long studentId,
                                      @Param("status") EnrollmentStatus status);

    @Query("""
            select distinct e from Enrollment e
            join fetch e.courseSection s
            join fetch s.course c
            join fetch s.semester sem
            left join fetch s.lecturer l
            where e.student.id = :studentId and e.status = :status and sem.id = :semesterId
            """)
    List<Enrollment> findForTimetableBySemester(@Param("studentId") Long studentId,
                                                @Param("status") EnrollmentStatus status,
                                                @Param("semesterId") Long semesterId);
}

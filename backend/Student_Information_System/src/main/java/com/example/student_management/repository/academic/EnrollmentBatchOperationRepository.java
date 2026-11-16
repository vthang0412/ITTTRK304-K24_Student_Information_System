package com.example.student_management.repository.academic;

import com.example.student_management.entity.academic.EnrollmentBatchOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EnrollmentBatchOperationRepository extends JpaRepository<EnrollmentBatchOperation, Long> {
    Optional<EnrollmentBatchOperation> findByBatchCode(String batchCode);

    @Query(
            value = """
                    select distinct operation from EnrollmentBatchOperation operation
                    left join EnrollmentBatchOperationItem item on item.batchId = operation.id
                    where (:admin is null or lower(operation.adminUsername) like lower(concat('%', :admin, '%')))
                      and (:semesterId is null or operation.semesterId = :semesterId)
                      and (:action is null or operation.action = :action)
                      and (:fromTime is null or operation.createdAt >= :fromTime)
                      and (:toTime is null or operation.createdAt <= :toTime)
                      and (:student is null or
                           lower(coalesce(item.studentCode, '')) like lower(concat('%', :student, '%')) or
                           lower(coalesce(item.studentName, '')) like lower(concat('%', :student, '%')) or
                           lower(coalesce(item.studentEmail, '')) like lower(concat('%', :student, '%')))
                      and (:course is null or
                           lower(coalesce(item.courseCode, '')) like lower(concat('%', :course, '%')) or
                           lower(coalesce(item.courseName, '')) like lower(concat('%', :course, '%')))
                      and (:section is null or
                           lower(coalesce(item.sectionCode, '')) like lower(concat('%', :section, '%')))
                      and (:result is null or item.resultStatus = :result)
                    """,
            countQuery = """
                    select count(distinct operation.id) from EnrollmentBatchOperation operation
                    left join EnrollmentBatchOperationItem item on item.batchId = operation.id
                    where (:admin is null or lower(operation.adminUsername) like lower(concat('%', :admin, '%')))
                      and (:semesterId is null or operation.semesterId = :semesterId)
                      and (:action is null or operation.action = :action)
                      and (:fromTime is null or operation.createdAt >= :fromTime)
                      and (:toTime is null or operation.createdAt <= :toTime)
                      and (:student is null or
                           lower(coalesce(item.studentCode, '')) like lower(concat('%', :student, '%')) or
                           lower(coalesce(item.studentName, '')) like lower(concat('%', :student, '%')) or
                           lower(coalesce(item.studentEmail, '')) like lower(concat('%', :student, '%')))
                      and (:course is null or
                           lower(coalesce(item.courseCode, '')) like lower(concat('%', :course, '%')) or
                           lower(coalesce(item.courseName, '')) like lower(concat('%', :course, '%')))
                      and (:section is null or
                           lower(coalesce(item.sectionCode, '')) like lower(concat('%', :section, '%')))
                      and (:result is null or item.resultStatus = :result)
                    """)
    Page<EnrollmentBatchOperation> search(
            @Param("admin") String admin,
            @Param("semesterId") Long semesterId,
            @Param("action") String action,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            @Param("student") String student,
            @Param("course") String course,
            @Param("section") String section,
            @Param("result") String result,
            Pageable pageable
    );
}

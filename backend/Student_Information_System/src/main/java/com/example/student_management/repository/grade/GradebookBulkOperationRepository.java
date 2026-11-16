package com.example.student_management.repository.grade;

import com.example.student_management.entity.grade.GradebookBulkOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface GradebookBulkOperationRepository extends JpaRepository<GradebookBulkOperation, Long> {
    Optional<GradebookBulkOperation> findByOperationCode(String operationCode);

    @Query(value = """
            select distinct operation from GradebookBulkOperation operation
            left join GradebookBulkOperationItem item on item.operationId = operation.id
            where (:actor is null or lower(operation.actor) like lower(concat('%', :actor, '%')))
              and (:action is null or operation.action = :action)
              and (:fromTime is null or operation.createdAt >= :fromTime)
              and (:toTime is null or operation.createdAt <= :toTime)
              and (:course is null or
                   lower(coalesce(item.courseCode, '')) like lower(concat('%', :course, '%')) or
                   lower(coalesce(item.courseName, '')) like lower(concat('%', :course, '%')))
              and (:section is null or lower(coalesce(item.sectionCode, '')) like lower(concat('%', :section, '%')))
              and (:semester is null or lower(coalesce(item.semesterName, '')) like lower(concat('%', :semester, '%')))
              and (:result is null or item.resultStatus = :result)
            """, countQuery = """
            select count(distinct operation.id) from GradebookBulkOperation operation
            left join GradebookBulkOperationItem item on item.operationId = operation.id
            where (:actor is null or lower(operation.actor) like lower(concat('%', :actor, '%')))
              and (:action is null or operation.action = :action)
              and (:fromTime is null or operation.createdAt >= :fromTime)
              and (:toTime is null or operation.createdAt <= :toTime)
              and (:course is null or
                   lower(coalesce(item.courseCode, '')) like lower(concat('%', :course, '%')) or
                   lower(coalesce(item.courseName, '')) like lower(concat('%', :course, '%')))
              and (:section is null or lower(coalesce(item.sectionCode, '')) like lower(concat('%', :section, '%')))
              and (:semester is null or lower(coalesce(item.semesterName, '')) like lower(concat('%', :semester, '%')))
              and (:result is null or item.resultStatus = :result)
            """)
    Page<GradebookBulkOperation> search(
            @Param("actor") String actor,
            @Param("action") String action,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            @Param("course") String course,
            @Param("section") String section,
            @Param("semester") String semester,
            @Param("result") String result,
            Pageable pageable
    );
}

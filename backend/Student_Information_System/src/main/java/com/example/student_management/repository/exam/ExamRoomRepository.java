package com.example.student_management.repository.exam;

import com.example.student_management.entity.exam.ExamRoom;
import com.example.student_management.entity.exam.ExamRoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExamRoomRepository extends JpaRepository<ExamRoom, Long>, JpaSpecificationExecutor<ExamRoom> {

    boolean existsByRoomCode(String roomCode);

    boolean existsByRoomCodeAndIdNot(String roomCode, Long id);

    Optional<ExamRoom> findByRoomCode(String roomCode);

    @Query("SELECT r FROM ExamRoom r WHERE " +
            "(:status IS NULL OR r.status = :status) AND " +
            "(:building IS NULL OR LOWER(r.building) LIKE LOWER(CONCAT('%', :building, '%'))) AND " +
            "(:minCapacity IS NULL OR r.capacity >= :minCapacity) AND " +
            "(:keyword IS NULL OR LOWER(r.roomCode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(r.roomName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(r.building) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ExamRoom> searchRooms(
            @Param("keyword") String keyword,
            @Param("building") String building,
            @Param("status") ExamRoomStatus status,
            @Param("minCapacity") Integer minCapacity,
            Pageable pageable
    );
}


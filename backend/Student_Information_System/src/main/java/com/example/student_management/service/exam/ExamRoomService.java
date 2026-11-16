package com.example.student_management.service.exam;

import com.example.student_management.dto.exam.ExamRoomDto;
import com.example.student_management.dto.exam.ExamRoomRequest;
import com.example.student_management.entity.exam.ExamRoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ExamRoomService {

    Page<ExamRoomDto> searchRooms(String keyword, String building, ExamRoomStatus status, Integer minCapacity, Pageable pageable);

    ExamRoomDto getRoomById(Long id);

    ExamRoomDto createRoom(ExamRoomRequest request);

    ExamRoomDto updateRoom(Long id, ExamRoomRequest request);

    void deleteRoom(Long id);

    ExamRoomDto updateRoomStatus(Long id, ExamRoomStatus status);
}


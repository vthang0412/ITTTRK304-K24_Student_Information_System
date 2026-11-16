package com.example.student_management.service.exam.impl;

import com.example.student_management.dto.exam.ExamRoomDto;
import com.example.student_management.dto.exam.ExamRoomRequest;
import com.example.student_management.entity.exam.ExamRoom;
import com.example.student_management.entity.exam.ExamRoomStatus;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.exam.ExamRoomRepository;
import com.example.student_management.repository.exam.ExamScheduleRepository;
import com.example.student_management.service.exam.ExamRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamRoomServiceImpl implements ExamRoomService {

    @Autowired
    private ExamRoomRepository examRoomRepository;

    @Autowired
    private ExamScheduleRepository examScheduleRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<ExamRoomDto> searchRooms(String keyword, String building, ExamRoomStatus status, Integer minCapacity, Pageable pageable) {
        String kw = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String bld = (building != null && !building.trim().isEmpty()) ? building.trim() : null;
        return examRoomRepository.searchRooms(kw, bld, status, minCapacity, pageable)
                .map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamRoomDto getRoomById(Long id) {
        ExamRoom room = findRoomById(id);
        return mapToDto(room);
    }

    @Override
    @Transactional
    public ExamRoomDto createRoom(ExamRoomRequest request) {
        String code = request.getRoomCode().trim();
        if (examRoomRepository.existsByRoomCode(code)) {
            throw new ConflictException("Mã phòng thi [" + code + "] đã tồn tại trong hệ thống");
        }
        if (request.getCapacity() == null || request.getCapacity() <= 0) {
            throw new BadRequestException("Sức chứa phòng thi phải lớn hơn 0");
        }

        ExamRoom room = ExamRoom.builder()
                .roomCode(code)
                .roomName(request.getRoomName().trim())
                .building(request.getBuilding() != null ? request.getBuilding().trim() : null)
                .capacity(request.getCapacity())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : ExamRoomStatus.ACTIVE)
                .build();

        ExamRoom saved = examRoomRepository.save(room);
        return mapToDto(saved);
    }

    @Override
    @Transactional
    public ExamRoomDto updateRoom(Long id, ExamRoomRequest request) {
        ExamRoom room = findRoomById(id);
        String code = request.getRoomCode().trim();
        if (examRoomRepository.existsByRoomCodeAndIdNot(code, id)) {
            throw new ConflictException("Mã phòng thi [" + code + "] đã được sử dụng bởi phòng thi khác");
        }
        if (request.getCapacity() == null || request.getCapacity() <= 0) {
            throw new BadRequestException("Sức chứa phòng thi phải lớn hơn 0");
        }

        room.setRoomCode(code);
        room.setRoomName(request.getRoomName().trim());
        room.setBuilding(request.getBuilding() != null ? request.getBuilding().trim() : null);
        room.setCapacity(request.getCapacity());
        room.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            room.setStatus(request.getStatus());
        }

        ExamRoom updated = examRoomRepository.save(room);
        return mapToDto(updated);
    }

    @Override
    @Transactional
    public void deleteRoom(Long id) {
        ExamRoom room = findRoomById(id);
        if (examScheduleRepository.existsByRoomId(id)) {
            throw new ConflictException("Không thể xóa phòng thi [" + room.getRoomCode() + "] đang được sử dụng trong lịch thi. Vui lòng xử lý các lịch thi liên quan trước.");
        }
        examRoomRepository.delete(room);
    }

    @Override
    @Transactional
    public ExamRoomDto updateRoomStatus(Long id, ExamRoomStatus status) {
        if (status == null) {
            throw new BadRequestException("Trạng thái phòng thi không được để trống");
        }
        ExamRoom room = findRoomById(id);
        room.setStatus(status);
        ExamRoom updated = examRoomRepository.save(room);
        return mapToDto(updated);
    }

    private ExamRoom findRoomById(Long id) {
        return examRoomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Phòng thi với ID " + id + " không tồn tại"));
    }

    private ExamRoomDto mapToDto(ExamRoom room) {
        return ExamRoomDto.builder()
                .id(room.getId())
                .roomCode(room.getRoomCode())
                .roomName(room.getRoomName())
                .building(room.getBuilding())
                .capacity(room.getCapacity())
                .description(room.getDescription())
                .status(room.getStatus())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }
}


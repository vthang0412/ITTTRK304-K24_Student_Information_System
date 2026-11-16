package com.example.student_management.service.exam;

import com.example.student_management.dto.exam.ExamRoomDto;
import com.example.student_management.dto.exam.ExamRoomRequest;
import com.example.student_management.entity.exam.ExamRoom;
import com.example.student_management.entity.exam.ExamRoomStatus;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.exam.ExamRoomRepository;
import com.example.student_management.repository.exam.ExamScheduleRepository;
import com.example.student_management.service.exam.impl.ExamRoomServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamRoomServiceTest {

    @Mock
    private ExamRoomRepository examRoomRepository;

    @Mock
    private ExamScheduleRepository examScheduleRepository;

    @InjectMocks
    private ExamRoomServiceImpl examRoomService;

    private ExamRoom room1;

    @BeforeEach
    void setUp() {
        room1 = ExamRoom.builder()
                .id(1L)
                .roomCode("P101-A2")
                .roomName("Phòng thi máy 101")
                .building("Nhà A2")
                .capacity(40)
                .description("Phòng thi máy tính")
                .status(ExamRoomStatus.ACTIVE)
                .build();
    }

    @Test
    void testCreateRoom_Success() {
        ExamRoomRequest request = ExamRoomRequest.builder()
                .roomCode("P102-A2")
                .roomName("Phòng thi máy 102")
                .building("Nhà A2")
                .capacity(45)
                .build();

        when(examRoomRepository.existsByRoomCode("P102-A2")).thenReturn(false);
        when(examRoomRepository.save(any(ExamRoom.class))).thenAnswer(inv -> {
            ExamRoom r = inv.getArgument(0);
            r.setId(2L);
            return r;
        });

        ExamRoomDto result = examRoomService.createRoom(request);

        assertNotNull(result);
        assertEquals(2L, result.getId());
        assertEquals("P102-A2", result.getRoomCode());
        assertEquals(45, result.getCapacity());
        assertEquals(ExamRoomStatus.ACTIVE, result.getStatus());
    }

    @Test
    void testCreateRoom_DuplicateCode_ThrowsConflict() {
        ExamRoomRequest request = ExamRoomRequest.builder()
                .roomCode("P101-A2")
                .roomName("Trùng mã")
                .capacity(40)
                .build();

        when(examRoomRepository.existsByRoomCode("P101-A2")).thenReturn(true);

        assertThrows(ConflictException.class, () -> examRoomService.createRoom(request));
        verify(examRoomRepository, never()).save(any());
    }

    @Test
    void testCreateRoom_InvalidCapacity_ThrowsBadRequest() {
        ExamRoomRequest request = ExamRoomRequest.builder()
                .roomCode("P103-A2")
                .roomName("Sức chứa 0")
                .capacity(0)
                .build();

        assertThrows(BadRequestException.class, () -> examRoomService.createRoom(request));
    }

    @Test
    void testUpdateRoom_Success() {
        ExamRoomRequest request = ExamRoomRequest.builder()
                .roomCode("P101-A2-EDIT")
                .roomName("Tên đã đổi")
                .capacity(50)
                .build();

        when(examRoomRepository.findById(1L)).thenReturn(Optional.of(room1));
        when(examRoomRepository.existsByRoomCodeAndIdNot("P101-A2-EDIT", 1L)).thenReturn(false);
        when(examRoomRepository.save(any(ExamRoom.class))).thenAnswer(inv -> inv.getArgument(0));

        ExamRoomDto result = examRoomService.updateRoom(1L, request);

        assertNotNull(result);
        assertEquals("P101-A2-EDIT", result.getRoomCode());
        assertEquals("Tên đã đổi", result.getRoomName());
        assertEquals(50, result.getCapacity());
    }

    @Test
    void testUpdateRoom_DuplicateCodeOtherRoom_ThrowsConflict() {
        ExamRoomRequest request = ExamRoomRequest.builder()
                .roomCode("P202-B1")
                .roomName("Trùng với phòng khác")
                .capacity(50)
                .build();

        when(examRoomRepository.findById(1L)).thenReturn(Optional.of(room1));
        when(examRoomRepository.existsByRoomCodeAndIdNot("P202-B1", 1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> examRoomService.updateRoom(1L, request));
    }

    @Test
    void testDeleteRoom_Success() {
        when(examRoomRepository.findById(1L)).thenReturn(Optional.of(room1));
        when(examScheduleRepository.existsByRoomId(1L)).thenReturn(false);

        examRoomService.deleteRoom(1L);

        verify(examRoomRepository).delete(room1);
    }

    @Test
    void testDeleteRoom_UsedInSchedule_ThrowsConflict() {
        when(examRoomRepository.findById(1L)).thenReturn(Optional.of(room1));
        when(examScheduleRepository.existsByRoomId(1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> examRoomService.deleteRoom(1L));
        verify(examRoomRepository, never()).delete(any(ExamRoom.class));
    }

    @Test
    void testUpdateRoomStatus_Success() {
        when(examRoomRepository.findById(1L)).thenReturn(Optional.of(room1));
        when(examRoomRepository.save(any(ExamRoom.class))).thenAnswer(inv -> inv.getArgument(0));

        ExamRoomDto result = examRoomService.updateRoomStatus(1L, ExamRoomStatus.INACTIVE);

        assertNotNull(result);
        assertEquals(ExamRoomStatus.INACTIVE, result.getStatus());
    }

    @Test
    void testUpdateRoomStatus_CanReactivateInactiveRoom() {
        room1.setStatus(ExamRoomStatus.INACTIVE);
        when(examRoomRepository.findById(1L)).thenReturn(Optional.of(room1));
        when(examRoomRepository.save(any(ExamRoom.class))).thenAnswer(inv -> inv.getArgument(0));

        ExamRoomDto result = examRoomService.updateRoomStatus(1L, ExamRoomStatus.ACTIVE);

        assertEquals(ExamRoomStatus.ACTIVE, result.getStatus());
        assertEquals(ExamRoomStatus.ACTIVE, room1.getStatus());
        verify(examRoomRepository).save(room1);
    }

    @Test
    void testSearchRooms_Success() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(examRoomRepository.searchRooms(any(), any(), any(), any(), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(room1)));

        Page<ExamRoomDto> result = examRoomService.searchRooms("101", null, null, null, pageRequest);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("P101-A2", result.getContent().get(0).getRoomCode());
    }
}

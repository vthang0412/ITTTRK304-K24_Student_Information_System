package com.example.student_management.controller.exam;

import com.example.student_management.dto.exam.ExamRoomDto;
import com.example.student_management.dto.exam.ExamRoomRequest;
import com.example.student_management.entity.exam.ExamRoomStatus;
import com.example.student_management.service.exam.ExamRoomService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/exam-rooms")
@PreAuthorize("hasRole('ADMIN')")
public class AdminExamRoomController {

    @Autowired
    private ExamRoomService examRoomService;

    /**
     * 1. Danh sách phòng thi có phân trang, tìm kiếm và lọc
     */
    @GetMapping
    public ResponseEntity<Page<ExamRoomDto>> searchRooms(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String building,
            @RequestParam(required = false) ExamRoomStatus status,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort
    ) {
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction direction = (sortParams.length > 1 && "asc".equalsIgnoreCase(sortParams[1]))
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sortField));
        return ResponseEntity.ok(examRoomService.searchRooms(keyword, building, status, minCapacity, pageRequest));
    }

    /**
     * 2. Chi tiết phòng thi
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExamRoomDto> getRoomById(@PathVariable Long id) {
        return ResponseEntity.ok(examRoomService.getRoomById(id));
    }

    /**
     * 3. Thêm mới phòng thi
     */
    @PostMapping
    public ResponseEntity<ExamRoomDto> createRoom(@Valid @RequestBody ExamRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(examRoomService.createRoom(request));
    }

    /**
     * 4. Cập nhật thông tin phòng thi
     */
    @PutMapping("/{id}")
    public ResponseEntity<ExamRoomDto> updateRoom(
            @PathVariable Long id,
            @Valid @RequestBody ExamRoomRequest request
    ) {
        return ResponseEntity.ok(examRoomService.updateRoom(id, request));
    }

    /**
     * 5. Xóa phòng thi (từ chối nếu phòng đang có lịch thi)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteRoom(@PathVariable Long id) {
        examRoomService.deleteRoom(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Xóa phòng thi thành công");
        return ResponseEntity.ok(response);
    }

    /**
     * 6. Bật/tắt trạng thái phòng thi (ACTIVE / INACTIVE)
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ExamRoomDto> updateRoomStatus(
            @PathVariable Long id,
            @RequestParam(required = false) ExamRoomStatus status,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        ExamRoomStatus targetStatus = status;
        if (targetStatus == null && body != null && body.containsKey("status")) {
            targetStatus = ExamRoomStatus.valueOf(body.get("status").toString().toUpperCase());
        }
        return ResponseEntity.ok(examRoomService.updateRoomStatus(id, targetStatus));
    }
}


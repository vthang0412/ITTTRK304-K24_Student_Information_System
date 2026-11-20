package com.example.student_management.controller.ai;

import com.example.student_management.dto.ai.*;
import com.example.student_management.service.ai.AIChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/ai")
@PreAuthorize("hasRole('STUDENT')")
public class AIChatController {

    @Autowired
    private AIChatService aiChatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 1. Lấy tổng quan thông tin học tập thực tế (GPA, tín chỉ, môn học)
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", aiChatService.getAcademicOverview(authentication.getName()));
        return ResponseEntity.ok(response);
    }

    /**
     * 2. Lấy danh sách các cuộc hội thoại AI của sinh viên
     */
    @GetMapping("/chat/conversations")
    public ResponseEntity<Map<String, Object>> getConversations(Authentication authentication) {
        List<AIConversationDto> conversations = aiChatService.getConversations(authentication.getName());
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", conversations);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. Khởi tạo một cuộc hội thoại mới
     */
    @PostMapping("/chat/conversations")
    public ResponseEntity<Map<String, Object>> createConversation(
            @Valid @RequestBody(required = false) AICreateConversationRequest request,
            Authentication authentication
    ) {
        AIConversationDto created = aiChatService.createConversation(authentication.getName(), request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", created);
        return ResponseEntity.ok(response);
    }

    /**
     * 4. Xem lịch sử tin nhắn trong một cuộc hội thoại
     */
    @GetMapping("/chat/conversations/{id}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @PathVariable Long id,
            Authentication authentication
    ) {
        List<AIMessageDto> messages = aiChatService.getMessages(authentication.getName(), id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", messages);
        return ResponseEntity.ok(response);
    }

    /**
     * 5. Gửi câu hỏi cho AI Study Assistant
     */
    @PostMapping("/chat/conversations/{id}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody AIChatRequest request,
            Authentication authentication
    ) {
        AIChatResponse result = aiChatService.sendMessage(authentication.getName(), id, request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", result);
        return ResponseEntity.ok(response);
    }

    /**
     * 6. Xóa cuộc hội thoại
     */
    @DeleteMapping("/chat/conversations/{id}")
    public ResponseEntity<Map<String, Object>> deleteConversation(
            @PathVariable Long id,
            Authentication authentication
    ) {
        aiChatService.deleteConversation(authentication.getName(), id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đã xóa cuộc hội thoại thành công");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/chat/conversations/{id}/title")
    public ResponseEntity<Map<String, Object>> renameConversation(
            @PathVariable Long id,
            @Valid @RequestBody AIRenameConversationRequest request,
            Authentication authentication
    ) {
        AIConversationDto updated = aiChatService.renameConversation(authentication.getName(), id, request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", updated);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/chat/messages/{id}/feedback")
    public ResponseEntity<Map<String, Object>> setMessageFeedback(
            @PathVariable Long id,
            @Valid @RequestBody AIMessageFeedbackRequest request,
            Authentication authentication
    ) {
        AIMessageDto updated = aiChatService.setMessageFeedback(authentication.getName(), id, request.getFeedback());
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", updated);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getReport(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", aiChatService.getAcademicReport(authentication.getName()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/report/excel")
    public void exportReportExcel(Authentication authentication, HttpServletResponse response) throws IOException {
        Map<String, Object> report = aiChatService.getAcademicReport(authentication.getName());
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Bao cao hoc tap");
            int rowIndex = 0;
            Row title = sheet.createRow(rowIndex++);
            title.createCell(0).setCellValue("Báo cáo học tập AI");
            rowIndex = writeKeyValue(sheet, rowIndex, "Mã sinh viên", report.get("studentCode"));
            rowIndex = writeKeyValue(sheet, rowIndex, "Họ tên", report.get("fullName"));
            rowIndex = writeKeyValue(sheet, rowIndex, "Ngày tạo báo cáo", report.get("generatedAt"));
            rowIndex++;
            rowIndex = writeKeyValue(sheet, rowIndex, "GPA 10", report.get("gpa10"));
            rowIndex = writeKeyValue(sheet, rowIndex, "GPA 4", report.get("gpa4"));
            rowIndex = writeKeyValue(sheet, rowIndex, "Phân loại", report.get("academicClassification"));
            rowIndex = writeKeyValue(sheet, rowIndex, "Tín chỉ tích lũy", report.get("accumulatedCredits"));
            rowIndex = writeKeyValue(sheet, rowIndex, "Tín chỉ đang học", report.get("activeCredits"));
            rowIndex = writeKeyValue(sheet, rowIndex, "Số môn đã có điểm", report.get("passedCourses"));
            rowIndex = writeKeyValue(sheet, rowIndex, "Số môn chưa đạt", report.get("failedCourses"));
            rowIndex++;
            rowIndex = writeMapRows(sheet, rowIndex, "Kết quả theo học kỳ", asList(report.get("semesters")), List.of("semester", "gpa10", "earnedCredits", "passedCount", "failedCount"));
            rowIndex = writeMapRows(sheet, rowIndex, "Môn điểm thấp", asList(report.get("lowScoreCourses")), List.of("courseCode", "courseName", "finalScore", "classification"));
            rowIndex = writeMapRows(sheet, rowIndex, "Mục tiêu cải thiện", asList(report.get("improvementTargets")), List.of("courseCode", "courseName", "reason", "suggestion"));
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);
            sheet.autoSizeColumn(3);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"AI_Academic_Report.xlsx\"");
            workbook.write(response.getOutputStream());
        }
    }

    private int writeKeyValue(XSSFSheet sheet, int rowIndex, String label, Object value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value == null ? "" : String.valueOf(value));
        return rowIndex + 1;
    }

    private int writeMapRows(XSSFSheet sheet, int rowIndex, String title, List<?> items, List<String> columns) {
        Row section = sheet.createRow(rowIndex++);
        section.createCell(0).setCellValue(title);
        if (items.isEmpty()) {
            sheet.createRow(rowIndex++).createCell(0).setCellValue("Không có dữ liệu");
            return rowIndex + 1;
        }
        Row header = sheet.createRow(rowIndex++);
        for (int i = 0; i < columns.size(); i++) {
            header.createCell(i).setCellValue(columns.get(i));
        }
        for (Object item : items) {
            Map<?, ?> rowData = objectMapper.convertValue(item, Map.class);
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < columns.size(); i++) {
                Object value = rowData.get(columns.get(i));
                row.createCell(i).setCellValue(value == null ? "" : String.valueOf(value));
            }
        }
        return rowIndex + 1;
    }

    private List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }
}


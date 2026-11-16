package com.example.student_management.service.exam.impl;

import com.example.student_management.dto.exam.ExamScheduleDto;
import com.example.student_management.dto.exam.ExamStudentDto;
import com.example.student_management.service.exam.ExamExcelExportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExamExcelExportServiceImpl implements ExamExcelExportService {
    private static final DateTimeFormatter EXPORTED_AT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int[] SCHEDULE_WIDTHS = { 12, 20, 32, 18, 24, 22, 14, 14, 14, 22, 16, 28, 18, 18 };

    @Override
    public byte[] exportSchedules(List<ExamScheduleDto> schedules) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Lịch thi");
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            CellStyle timeStyle = workbook.createCellStyle();
            timeStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("hh:mm"));

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("DANH SÁCH LỊCH THI");
            title.getCell(0).setCellStyle(titleStyle);
            Row exportedAt = sheet.createRow(1);
            exportedAt.createCell(0).setCellValue("Ngày xuất: " + LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(EXPORTED_AT));
            String semesters = schedules.stream()
                    .map(ExamScheduleDto::getSemesterName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));
            sheet.createRow(2).createCell(0).setCellValue("Học kỳ: " + (semesters.isEmpty() ? "—" : semesters));

            String[] headers = {
                    "Mã môn học", "Môn học", "Lớp học phần", "Học kỳ", "Ngày thi", "Bắt đầu", "Kết thúc",
                    "Phòng thi", "Tòa nhà", "Hình thức", "Trạng thái", "Địa điểm", "Số sinh viên", "Link thi"
            };
            writeHeader(sheet, 3, headers, headerStyle);
            int rowIndex = 4;
            for (ExamScheduleDto schedule : schedules) {
                Row row = sheet.createRow(rowIndex++);
                text(row, 0, schedule.getCourseCode());
                text(row, 1, schedule.getCourseName());
                text(row, 2, schedule.getSectionCode());
                text(row, 3, schedule.getSemesterName());
                if (schedule.getExamDate() != null) {
                    Cell cell = row.createCell(4);
                    cell.setCellValue(Date.valueOf(schedule.getExamDate()));
                    cell.setCellStyle(dateStyle);
                }
                if (schedule.getStartTime() != null) {
                    Cell cell = row.createCell(5);
                    cell.setCellValue(Time.valueOf(schedule.getStartTime()));
                    cell.setCellStyle(timeStyle);
                }
                if (schedule.getEndTime() != null) {
                    Cell cell = row.createCell(6);
                    cell.setCellValue(Time.valueOf(schedule.getEndTime()));
                    cell.setCellStyle(timeStyle);
                }
                text(row, 7, schedule.getRoomCode());
                text(row, 8, schedule.getBuilding());
                text(row, 9, schedule.getExamType() == null ? null : schedule.getExamType().name());
                text(row, 10, schedule.getStatus() == null ? null : schedule.getStatus().name());
                text(row, 11, schedule.getExamLocation());
                row.createCell(12).setCellValue(schedule.getEnrolledStudentsCount());
                text(row, 13, schedule.getExamLink());
            }
            applyWidths(sheet, SCHEDULE_WIDTHS);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể tạo tệp Excel lịch thi", ex);
        }
    }

    @Override
    public byte[] exportStudents(List<ExamStudentDto> students, String context) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sinh viên dự thi");
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("DANH SÁCH SINH VIÊN DỰ THI");
            title.getCell(0).setCellStyle(titleStyle);
            sheet.createRow(1).createCell(0).setCellValue(
                    "Ngày xuất: " + LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(EXPORTED_AT));
            sheet.createRow(2).createCell(0).setCellValue(context);
            writeHeader(sheet, 3, new String[] { "Mã sinh viên", "Họ và tên", "Email", "Lớp", "Khoa", "Ngày đăng ký" }, headerStyle);

            int rowIndex = 4;
            for (ExamStudentDto student : students) {
                Row row = sheet.createRow(rowIndex++);
                text(row, 0, student.getStudentCode());
                text(row, 1, student.getFullName());
                text(row, 2, student.getEmail());
                text(row, 3, student.getClassName());
                text(row, 4, student.getDepartment());
                text(row, 5, student.getRegisteredAt() == null ? null
                        : student.getRegisteredAt().format(EXPORTED_AT));
            }
            applyWidths(sheet, new int[] { 20, 30, 34, 20, 24, 22 });
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể tạo tệp Excel sinh viên dự thi", ex);
        }
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 15);
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setWrapText(true);
        return style;
    }

    private void writeHeader(Sheet sheet, int rowIndex, String[] headers, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
        row.setHeightInPoints(30);
        sheet.createFreezePane(0, rowIndex + 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(rowIndex, rowIndex, 0, headers.length - 1));
    }

    private void applyWidths(Sheet sheet, int[] widths) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private void text(Row row, int column, String value) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
        }
    }
}
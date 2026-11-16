package com.example.student_management.service.exam;

import com.example.student_management.dto.exam.ExamScheduleDto;
import com.example.student_management.dto.exam.ExamStudentDto;
import com.example.student_management.entity.exam.ExamScheduleStatus;
import com.example.student_management.entity.exam.ExamType;
import com.example.student_management.service.exam.impl.ExamExcelExportServiceImpl;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExamExcelExportServiceTest {
    private final ExamExcelExportService exporter = new ExamExcelExportServiceImpl();

    @Test
    void exportsSchedulesAsFormattedWorkbook() throws Exception {
        ExamScheduleDto schedule = ExamScheduleDto.builder()
                .courseCode("CS101")
                .courseName("Introduction to Computing")
                .sectionCode("CS101-01")
                .semesterName("Semester 1")
                .examDate(LocalDate.of(2026, 12, 10))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(11, 0))
                .examType(ExamType.OFFLINE)
                .status(ExamScheduleStatus.SCHEDULED)
                .enrolledStudentsCount(30)
                .build();

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(exporter.exportSchedules(List.of(schedule))))) {
            var sheet = workbook.getSheet("Lịch thi");
            assertNotNull(sheet);
            assertEquals("DANH SÁCH LỊCH THI", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Ngày xuất:", sheet.getRow(1).getCell(0).getStringCellValue().substring(0, 10));
            assertEquals("Học kỳ: Semester 1", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("Mã môn học", sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("CS101", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals(CellType.NUMERIC, sheet.getRow(4).getCell(4).getCellType());
            assertEquals("dd/mm/yyyy", sheet.getRow(4).getCell(4).getCellStyle().getDataFormatString());
            assertEquals("hh:mm", sheet.getRow(4).getCell(5).getCellStyle().getDataFormatString());
            assertEquals(30, (int) sheet.getRow(4).getCell(12).getNumericCellValue());
        }
    }

    @Test
    void exportsExamRosterAsWorkbook() throws Exception {
        ExamStudentDto student = ExamStudentDto.builder()
                .studentCode("ST100")
                .fullName("Student Name")
                .email("student@example.test")
                .className("Class A")
                .department("Computing")
                .build();

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(exporter.exportStudents(List.of(student), "Lớp: CS101-01 | Học kỳ: Semester 1")))) {
            var sheet = workbook.getSheet("Sinh viên dự thi");
            assertNotNull(sheet);
            assertEquals("Lớp: CS101-01 | Học kỳ: Semester 1", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("Mã sinh viên", sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("ST100", sheet.getRow(4).getCell(0).getStringCellValue());
        }
    }
}

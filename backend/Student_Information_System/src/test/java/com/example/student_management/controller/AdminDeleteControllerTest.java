package com.example.student_management.controller;

import com.example.student_management.entity.*;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDeleteControllerTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseSectionRepository courseSectionRepository;
    @Mock
    private SemesterRepository semesterRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private GradebookRepository gradebookRepository;

    @InjectMocks
    private CourseController courseController;
    @InjectMocks
    private SemesterController semesterController;
    @InjectMocks
    private CourseSectionController courseSectionController;

    @Test
    void courseDeleteRejectsWhenCourseHasLinkedSections() {
        Course course = Course.builder().id(10L).courseCode("CS101").courseName("Lập trình").credits(3)
                .status(AccountStatus.ACTIVE).build();
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.existsByCourseId(10L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> courseController.delete(10L));

        assertTrue(ex.getMessage().contains("học phần"));
        verify(courseRepository, never()).delete(any());
    }

    @Test
    void semesterDeleteRejectsWhenSemesterHasLinkedSections() {
        Semester semester = Semester.builder().id(20L).semesterCode("20251").semesterName("Học kỳ 1")
                .status(SemesterStatus.REGISTRATION_OPEN).build();
        when(semesterRepository.findById(20L)).thenReturn(Optional.of(semester));
        when(courseSectionRepository.existsBySemesterId(20L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> semesterController.delete(20L));

        assertTrue(ex.getMessage().contains("học phần"));
        verify(semesterRepository, never()).delete(any());
    }

    @Test
    void courseSectionDeleteRejectsWhenSectionHasEnrollments() {
        CourseSection section = CourseSection.builder().id(30L).sectionCode("CS101-01").capacity(40)
                .status(CourseSectionStatus.OPEN).build();
        when(courseSectionRepository.findById(30L)).thenReturn(Optional.of(section));
        when(enrollmentRepository.existsByCourseSectionId(30L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> courseSectionController.delete(30L));

        assertTrue(ex.getMessage().contains("sinh viên"));
        verify(courseSectionRepository, never()).delete(any());
    }
}

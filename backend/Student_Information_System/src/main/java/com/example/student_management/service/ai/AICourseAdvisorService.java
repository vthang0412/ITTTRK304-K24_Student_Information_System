package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.CourseAdvisorRequestDto;
import com.example.student_management.dto.ai.CourseAdvisorResponseDto;

public interface AICourseAdvisorService {
    CourseAdvisorResponseDto getCourseRecommendations(String username);

    CourseAdvisorResponseDto recommendCoursesWithPreferences(String username, CourseAdvisorRequestDto request);
}


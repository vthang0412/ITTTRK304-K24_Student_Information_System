package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.*;

import java.util.List;

public interface AIStudyPlanService {

    StudyPlanDto createStudyPlan(String username, CreateStudyPlanRequestDto request);

    List<StudyPlanDto> getStudentStudyPlans(String username);

    StudyPlanDto getStudyPlanDetail(String username, Long planId);

    StudyPlanDto updateStudyPlan(String username, Long planId, UpdateStudyPlanRequestDto request);

    void deleteStudyPlan(String username, Long planId);

    StudyPlanItemDto updatePlanItem(String username, Long planId, Long itemId, UpdateStudyPlanItemRequestDto request);
}


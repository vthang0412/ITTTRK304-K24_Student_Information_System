package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.AIInsightsDto;

public interface AIInsightsService {
    AIInsightsDto getInsights(String username);

    AIInsightsDto refreshInsights(String username);
}


package com.example.student_management.service.academic;

import com.example.student_management.dto.academic.RecurringScheduleRequest;
import com.example.student_management.dto.academic.RecurringScheduleSessionResponse;

import java.util.List;

public interface RecurringCourseScheduleService {
    List<RecurringScheduleSessionResponse> getActiveSessions(Long sectionId);

    void check(Long sectionId, RecurringScheduleRequest request);

    List<RecurringScheduleSessionResponse> replace(Long sectionId, RecurringScheduleRequest request);

    void cancelActiveSessions(Long sectionId);
}

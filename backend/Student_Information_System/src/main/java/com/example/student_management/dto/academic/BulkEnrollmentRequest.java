package com.example.student_management.dto.academic;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BulkEnrollmentRequest {
    @NotEmpty
    @Size(max = 1000)
    private List<@NotNull Long> enrollmentIds;

    @Size(max = 500)
    private String reason;

    @Size(max = 1000)
    private Map<@NotNull Long, @Size(max = 500) String> reasonsByEnrollmentId;
}

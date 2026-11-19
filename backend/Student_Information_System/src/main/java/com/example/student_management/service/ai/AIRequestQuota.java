package com.example.student_management.service.ai;

import com.example.student_management.entity.ai.AIUsageLog;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.exception.TooManyRequestsException;
import com.example.student_management.repository.ai.AIUsageLogRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AIRequestQuota {

    private static final Map<Long, Object> STUDENT_LOCKS = new ConcurrentHashMap<>();

    private AIRequestQuota() {
    }

    public static AIUsageLog reserve(AIUsageLogRepository repository, Student student, int limit, String featureType) {
        Object lock = STUDENT_LOCKS.computeIfAbsent(student.getId(), id -> new Object());
        synchronized (lock) {
            long recentCalls = repository.countByStudentIdAndCreatedAtAfter(
                    student.getId(), LocalDateTime.now().minusHours(1));
            if (recentCalls >= limit) {
                throw new TooManyRequestsException(
                        "Bạn đã dùng hết hạn mức " + limit + " lượt gọi AI trong 1 giờ. Vui lòng thử lại sau.");
            }
            AIUsageLog reservation = AIUsageLog.builder()
                    .student(student)
                    .featureType(featureType)
                    .provider("PENDING")
                    .model("PENDING")
                    .status("PENDING")
                    .latencyMs(0L)
                    .createdAt(LocalDateTime.now())
                    .build();
            repository.save(reservation);
            return reservation;
        }
    }

    public static void complete(AIUsageLogRepository repository, AIUsageLog reservation,
                                String provider, String model, boolean success, long latencyMs) {
        if (reservation == null) return;
        reservation.setProvider(provider);
        reservation.setModel(model);
        reservation.setStatus(success ? "SUCCESS" : "FAILED");
        reservation.setLatencyMs(latencyMs);
        repository.save(reservation);
    }

    public static void resetForTests() {
        STUDENT_LOCKS.clear();
    }
}

package com.example.student_management.service.ai;

import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Lưu tạm trạng thái tiếp tục câu trả lời bị ngắt do giới hạn output. */
@Service
public class AIResponseContinuationService {

    private static final long TTL_MINUTES = 60;
    private static final int MAX_STATES = 500;

    private final Map<Long, ContinuationState> states = new ConcurrentHashMap<>();

    public static class ContinuationState {
        final String ownerKey;
        final Long conversationId;
        final String originalQuestion;
        final String partialContent;
        final int count;
        final Instant createdAt;
        final boolean inProgress;

        ContinuationState(String ownerKey, Long conversationId, String originalQuestion,
                          String partialContent, int count, Instant createdAt, boolean inProgress) {
            this.ownerKey = ownerKey;
            this.conversationId = conversationId;
            this.originalQuestion = originalQuestion;
            this.partialContent = partialContent;
            this.count = count;
            this.createdAt = createdAt;
            this.inProgress = inProgress;
        }

        public int getCount() {
            return count;
        }

        public String getOriginalQuestion() {
            return originalQuestion;
        }

        public String getPartialContent() {
            return partialContent;
        }
    }

    public void register(String ownerKey, Long conversationId, Long messageId,
                         String originalQuestion, String partialContent) {
        if (messageId == null) {
            return;
        }
        cleanup();
        states.put(messageId, new ContinuationState(
                ownerKey, conversationId,
                originalQuestion, partialContent, 0, Instant.now(), false));
    }

    public ContinuationState begin(Long messageId, String ownerKey, Long conversationId, int maxCount) {
        cleanup();
        AtomicReference<ContinuationState> available = new AtomicReference<>();
        states.compute(messageId, (id, state) -> {
            if (state == null) {
                throw new BadRequestException("Câu trả lời này đã hết hạn hoặc không còn thể tiếp tục. Vui lòng gửi lại câu hỏi.");
            }
            if (!java.util.Objects.equals(state.ownerKey, ownerKey)
                    || !java.util.Objects.equals(state.conversationId, conversationId)) {
                throw new BadRequestException("Không thể tiếp tục câu trả lời không thuộc hội thoại này.");
            }
            if (state.count >= maxCount) {
                throw new BadRequestException("Đã đạt số lần tiếp tục tối đa cho câu trả lời này. Vui lòng gửi lại câu hỏi để nhận câu trả lời mới.");
            }
            if (state.inProgress) {
                throw new ConflictException("Câu trả lời này đang được tiếp tục. Vui lòng đợi rồi thử lại.");
            }
            available.set(state);
            return copy(state, state.partialContent, state.count, true);
        });
        return available.get();
    }

    public ContinuationState complete(Long messageId, String partialContent, boolean retainState) {
        AtomicReference<ContinuationState> completed = new AtomicReference<>();
        states.compute(messageId, (id, state) -> {
            if (state == null || !state.inProgress) {
                throw new IllegalStateException("Continuation state is not in progress");
            }
            ContinuationState updated = copy(state, partialContent, state.count + 1, false);
            completed.set(updated);
            return retainState ? updated : null;
        });
        return completed.get();
    }

    public void cancel(Long messageId) {
        if (messageId == null) {
            return;
        }
        states.computeIfPresent(messageId, (id, state) -> (
            state.inProgress ? copy(state, state.partialContent, state.count, false) : state
        ));
    }

    public void remove(Long messageId) {
        if (messageId != null) {
            states.remove(messageId);
        }
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(TTL_MINUTES));
        states.entrySet().removeIf(entry -> (
                !entry.getValue().inProgress && entry.getValue().createdAt.isBefore(cutoff)
        ));
        int excess = states.size() - MAX_STATES;
        if (excess > 0) {
            states.entrySet().stream()
                    .filter(entry -> !entry.getValue().inProgress)
                    .sorted((a, b) -> a.getValue().createdAt.compareTo(b.getValue().createdAt))
                    .limit(excess)
                    .forEach(entry -> states.remove(entry.getKey()));
        }
    }

    private ContinuationState copy(ContinuationState state, String partialContent,
                                   int count, boolean inProgress) {
        return new ContinuationState(
                state.ownerKey, state.conversationId, state.originalQuestion,
                partialContent, count, state.createdAt, inProgress);
    }
}

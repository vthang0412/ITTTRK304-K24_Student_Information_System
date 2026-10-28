package com.example.student_management.service;

import com.example.student_management.dto.*;
import com.example.student_management.dto.request.GradeComponentConfigRequest;
import com.example.student_management.dto.request.GradeComponentRequest;
import com.example.student_management.dto.request.GradeRequest;
import com.example.student_management.entity.*;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GradebookService {
    private static final BigDecimal TEN = new BigDecimal("10");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DEFAULT_MAX_SCORE = new BigDecimal("10");

    @Autowired
    private GradebookRepository gradebookRepository;

    @Autowired
    private GradeComponentRepository gradeComponentRepository;

    @Autowired
    private GradeComponentScoreRepository gradeComponentScoreRepository;

    @Autowired
    private GradeAuditLogRepository gradeAuditLogRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public List<GradebookSummaryDto> getGradebookSummaries() {
        return courseSectionRepository.findAll().stream()
                .map(this::summarizeSection)
                .collect(Collectors.toList());
    }

    @Transactional
    public GradebookSummaryDto getGradebookSummary(Long gradebookId) {
        Gradebook gradebook = requireGradebook(gradebookId);
        return summarizeSection(gradebook.getCourseSection());
    }

    @Transactional
    public GradebookDetailDto getSectionGradebook(Long sectionId, String username) {
        User user = currentUser(username);
        CourseSection section = requireSection(sectionId);
        requireSectionAccess(section, user);
        Gradebook gradebook = getOrCreateGradebook(section);

        List<GradeComponent> components = gradeComponentRepository
                .findByGradebookIdOrderByDisplayOrderAscIdAsc(gradebook.getId());
        List<Enrollment> enrollments = enrolledEnrollments(sectionId);
        Map<Long, Map<Long, BigDecimal>> scores = scoresByEnrollment(gradebook.getId());
        Map<Long, Grade> grades = gradesByEnrollment(enrollments);

        List<GradebookEnrollmentDto> items = enrollments.stream().map(enrollment -> {
            Map<Long, BigDecimal> componentScores = scores.getOrDefault(enrollment.getId(), Map.of());
            Grade grade = grades.get(enrollment.getId());
            return GradebookEnrollmentDto.builder()
                    .enrollmentId(enrollment.getId())
                    .studentId(enrollment.getStudent().getId())
                    .studentCode(enrollment.getStudent().getStudentCode())
                    .fullName(enrollment.getStudent().getFullName())
                    .email(enrollment.getStudent().getEmail())
                    .componentScores(componentScores)
                    .componentScore(grade == null ? null : grade.getComponentScore())
                    .finalScore(grade == null ? null : grade.getFinalScore())
                    .complete(isComplete(components, componentScores, grade))
                    .build();
        }).collect(Collectors.toList());

        return GradebookDetailDto.builder()
                .gradebookId(gradebook.getId())
                .sectionId(section.getId())
                .sectionCode(section.getSectionCode())
                .courseCode(section.getCourse() == null ? null : section.getCourse().getCourseCode())
                .courseName(section.getCourse() == null ? null : section.getCourse().getCourseName())
                .semesterName(section.getSemester() == null ? null : section.getSemester().getSemesterName())
                .status(gradebook.getStatus().name())
                .components(components.stream().map(this::toComponentDto).collect(Collectors.toList()))
                .enrollments(items)
                .build();
    }

    @Transactional
    public List<GradeComponentDto> getComponents(Long sectionId, String username) {
        User user = currentUser(username);
        CourseSection section = requireSection(sectionId);
        requireSectionAccess(section, user);
        Gradebook gradebook = getOrCreateGradebook(section);
        return gradeComponentRepository.findByGradebookIdOrderByDisplayOrderAscIdAsc(gradebook.getId())
                .stream().map(this::toComponentDto).collect(Collectors.toList());
    }

    @Transactional
    public List<GradeComponentDto> updateComponents(Long sectionId, GradeComponentConfigRequest request, String username) {
        requireAdmin(username);
        CourseSection section = requireSection(sectionId);
        Gradebook gradebook = getOrCreateGradebook(section);
        if (gradebook.getStatus() != GradebookStatus.OPEN) {
            throw new ConflictException("Cannot change grade components when the gradebook is not open");
        }
        if (gradeComponentScoreRepository.countByGradeComponentGradebookId(gradebook.getId()) > 0) {
            throw new ConflictException("Cannot change grade components after component scores have been entered");
        }

        List<GradeComponentRequest> items = request == null || request.getComponents() == null
                ? List.of() : request.getComponents();
        validateComponentConfig(items);

        List<GradeComponent> existing = gradeComponentRepository
                .findByGradebookIdOrderByDisplayOrderAscIdAsc(gradebook.getId());
        String oldValue = describeComponents(existing);
        if (!existing.isEmpty()) {
            gradeComponentRepository.deleteAll(existing);
            gradeComponentRepository.flush();
        }

        List<GradeComponent> saved = new ArrayList<>();
        int order = 1;
        for (GradeComponentRequest item : items) {
            BigDecimal maxScore = item.getMaxScore() == null ? DEFAULT_MAX_SCORE : item.getMaxScore();
            GradeComponent component = GradeComponent.builder()
                    .gradebook(gradebook)
                    .componentName(item.getComponentName().trim())
                    .weight(item.getWeight())
                    .maxScore(maxScore)
                    .componentType(item.getComponentType())
                    .displayOrder(item.getDisplayOrder() == null ? order : item.getDisplayOrder())
                    .required(item.getRequired() == null || item.getRequired())
                    .build();
            saved.add(gradeComponentRepository.save(component));
            order += 1;
        }

        gradeAuditLogRepository.save(GradeAuditLog.builder()
                .gradebook(gradebook)
                .actor(username)
                .action("UPDATE_COMPONENTS")
                .oldValue(oldValue)
                .newValue(describeComponents(saved))
                .build());

        return saved.stream()
                .sorted(Comparator.comparing(GradeComponent::getDisplayOrder).thenComparing(GradeComponent::getId))
                .map(this::toComponentDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public Grade saveGrade(Long enrollmentId, GradeRequest request, String username) {
        User user = currentUser(username);
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException("Enrollment not found"));
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED) {
            throw new BadRequestException("Cannot grade a withdrawn enrollment");
        }
        CourseSection section = enrollment.getCourseSection();
        requireSectionAccess(section, user);

        Gradebook gradebook = getOrCreateGradebook(section);
        if (gradebook.getStatus() != GradebookStatus.OPEN) {
            throw new ConflictException("Gradebook is locked or not editable");
        }

        List<GradeComponent> components = gradeComponentRepository
                .findByGradebookIdOrderByDisplayOrderAscIdAsc(gradebook.getId());
        Grade grade = gradeRepository.findByEnrollmentId(enrollmentId)
                .orElseGet(() -> Grade.builder().enrollment(enrollment).build());

        BigDecimal oldFinal = grade.getFinalScore();
        BigDecimal oldComponent = grade.getComponentScore();

        if (!components.isEmpty()) {
            if (request.getComponentScores() == null) {
                throw new BadRequestException("Component scores are required for this course section");
            }
            Map<Long, BigDecimal> submitted = request.getComponentScores();
            Set<Long> configuredIds = components.stream().map(GradeComponent::getId).collect(Collectors.toSet());
            for (Long submittedId : submitted.keySet()) {
                if (!configuredIds.contains(submittedId)) {
                    throw new BadRequestException("Component score does not belong to this gradebook configuration");
                }
            }
            for (GradeComponent component : components) {
                BigDecimal raw = submitted.get(component.getId());
                BigDecimal score = raw == null ? null : normalizeScore(raw, component.getMaxScore(), component.getComponentName());
                Optional<GradeComponentScore> existing = gradeComponentScoreRepository
                        .findByGradeComponentIdAndEnrollmentId(component.getId(), enrollmentId);
                if (score == null) {
                    if (existing.isPresent()) {
                        GradeComponentScore current = existing.get();
                        gradeComponentScoreRepository.delete(current);
                        logChange(gradebook, enrollment, component, username, "CLEAR_COMPONENT_SCORE",
                                current.getScore(), null);
                    }
                    continue;
                }
                GradeComponentScore entity = existing.orElseGet(() -> GradeComponentScore.builder()
                        .gradeComponent(component)
                        .enrollment(enrollment)
                        .build());
                BigDecimal previous = entity.getScore();
                entity.setScore(score);
                entity.setUpdatedBy(username);
                gradeComponentScoreRepository.save(entity);
                if (previous == null || previous.compareTo(score) != 0) {
                    logChange(gradebook, enrollment, component, username, "UPDATE_COMPONENT_SCORE", previous, score);
                }
            }

            Map<Long, BigDecimal> allScores = gradeComponentScoreRepository.findByEnrollmentId(enrollmentId)
                    .stream().collect(Collectors.toMap(s -> s.getGradeComponent().getId(), GradeComponentScore::getScore));
            boolean complete = isComplete(components, allScores, grade);
            grade.setFinalScore(complete ? calculateFinalScore(components, allScores) : null);
            if (!Objects.equals(oldFinal, grade.getFinalScore())) {
                logChange(gradebook, enrollment, null, username, "UPDATE_FINAL_SCORE", oldFinal, grade.getFinalScore());
            }
        } else {
            BigDecimal finalScore = request.getFinalScore() == null ? null
                    : normalizeScore(request.getFinalScore(), TEN, "Final score");
            BigDecimal componentScore = request.getComponentScore() == null ? null
                    : normalizeScore(request.getComponentScore(), TEN, "Component score");
            grade.setFinalScore(finalScore);
            grade.setComponentScore(componentScore);
            if (oldComponent == null ? componentScore != null : componentScore == null || oldComponent.compareTo(componentScore) != 0) {
                logChange(gradebook, enrollment, null, username, "UPDATE_COMPONENT_SCORE", oldComponent, componentScore);
            }
            if (oldFinal == null ? finalScore != null : finalScore == null || oldFinal.compareTo(finalScore) != 0) {
                logChange(gradebook, enrollment, null, username, "UPDATE_FINAL_SCORE", oldFinal, finalScore);
            }
        }

        grade.setUpdatedBy(username);
        return gradeRepository.save(grade);
    }

    @Transactional(readOnly = true)
    public List<StudentGradeDto> getStudentPublishedGrades(String username) {
        User user = currentUser(username);
        if (user.getStudent() == null) {
            return List.of();
        }
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(user.getStudent().getId())
                .stream().filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
                .collect(Collectors.toList());
        List<StudentGradeDto> result = new ArrayList<>();
        for (Enrollment enrollment : enrollments) {
            Optional<Gradebook> gradebookOpt = gradebookRepository
                    .findByCourseSectionId(enrollment.getCourseSection().getId());
            if (gradebookOpt.isEmpty() || gradebookOpt.get().getStatus() != GradebookStatus.PUBLISHED) {
                continue;
            }
            Gradebook gradebook = gradebookOpt.get();
            Grade grade = gradeRepository.findByEnrollmentId(enrollment.getId()).orElse(null);
            List<GradeComponent> components = gradeComponentRepository
                    .findByGradebookIdOrderByDisplayOrderAscIdAsc(gradebook.getId());
            Map<Long, BigDecimal> scores = gradeComponentScoreRepository.findByEnrollmentId(enrollment.getId())
                    .stream().collect(Collectors.toMap(s -> s.getGradeComponent().getId(), GradeComponentScore::getScore));
            CourseSection section = enrollment.getCourseSection();
            Course course = section.getCourse();
            result.add(StudentGradeDto.builder()
                    .gradebookId(gradebook.getId())
                    .enrollmentId(enrollment.getId())
                    .sectionId(section.getId())
                    .sectionCode(section.getSectionCode())
                    .courseCode(course == null ? null : course.getCourseCode())
                    .courseName(course == null ? null : course.getCourseName())
                    .credits(course == null ? null : course.getCredits())
                    .semesterName(section.getSemester() == null ? null : section.getSemester().getSemesterName())
                    .componentScore(grade == null ? null : grade.getComponentScore())
                    .finalScore(grade == null ? null : grade.getFinalScore())
                    .componentScores(components.stream().map(c -> StudentComponentScoreDto.builder()
                            .componentId(c.getId())
                            .componentName(c.getComponentName())
                            .weight(c.getWeight())
                            .maxScore(c.getMaxScore())
                            .score(scores.get(c.getId()))
                            .build()).collect(Collectors.toList()))
                    .publishedAt(gradebook.getPublishedAt())
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Enrollment> getMyPublishedEnrollments(String username) {
        User user = currentUser(username);
        if (user.getStudent() == null) {
            return List.of();
        }
        return enrollmentRepository.findByStudentId(user.getStudent().getId()).stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
                .filter(e -> gradebookRepository.findByCourseSectionId(e.getCourseSection().getId())
                        .map(g -> g.getStatus() == GradebookStatus.PUBLISHED).orElse(false))
                .collect(Collectors.toList());
    }

    @Transactional
    public GradebookSummaryDto lock(Long gradebookId, String actor) {
        Gradebook gradebook = requireGradebook(gradebookId);
        if (gradebook.getStatus() != GradebookStatus.OPEN) {
            throw new ConflictException("Only an open gradebook can be locked");
        }
        gradebook.setStatus(GradebookStatus.LOCKED);
        gradebook.setLockedAt(LocalDateTime.now());
        gradebook.setLockedBy(actor);
        gradebookRepository.save(gradebook);
        logStatus(gradebook, actor, "LOCK", GradebookStatus.OPEN.name(), GradebookStatus.LOCKED.name());
        return summarizeSection(gradebook.getCourseSection());
    }

    @Transactional
    public GradebookSummaryDto unlock(Long gradebookId, String actor) {
        Gradebook gradebook = requireGradebook(gradebookId);
        GradebookStatus previous = gradebook.getStatus();
        if (previous == GradebookStatus.OPEN) {
            throw new ConflictException("Gradebook is already open");
        }
        gradebook.setStatus(GradebookStatus.OPEN);
        gradebook.setLockedAt(null);
        gradebook.setLockedBy(null);
        gradebook.setApprovedAt(null);
        gradebook.setApprovedBy(null);
        gradebook.setPublishedAt(null);
        gradebook.setPublishedBy(null);
        gradebookRepository.save(gradebook);
        String action = previous == GradebookStatus.LOCKED ? "UNLOCK" : "REOPEN";
        logStatus(gradebook, actor, action, previous.name(), GradebookStatus.OPEN.name());
        return summarizeSection(gradebook.getCourseSection());
    }

    @Transactional
    public GradebookSummaryDto approve(Long gradebookId, String actor) {
        Gradebook gradebook = requireGradebook(gradebookId);
        if (gradebook.getStatus() != GradebookStatus.LOCKED) {
            throw new ConflictException("Gradebook must be locked before it can be approved");
        }
        Progress progress = progress(gradebook);
        if (progress.incomplete > 0) {
            throw new ConflictException("Cannot approve a gradebook with incomplete grades");
        }
        gradebook.setStatus(GradebookStatus.APPROVED);
        gradebook.setApprovedAt(LocalDateTime.now());
        gradebook.setApprovedBy(actor);
        gradebookRepository.save(gradebook);
        logStatus(gradebook, actor, "APPROVE", GradebookStatus.LOCKED.name(), GradebookStatus.APPROVED.name());
        return summarizeSection(gradebook.getCourseSection());
    }

    @Transactional
    public GradebookSummaryDto publish(Long gradebookId, String actor) {
        Gradebook gradebook = requireGradebook(gradebookId);
        if (gradebook.getStatus() != GradebookStatus.APPROVED) {
            throw new ConflictException("Gradebook must be approved before it can be published");
        }
        Progress progress = progress(gradebook);
        if (progress.incomplete > 0) {
            throw new ConflictException("Cannot publish a gradebook with incomplete grades");
        }
        gradebook.setStatus(GradebookStatus.PUBLISHED);
        gradebook.setPublishedAt(LocalDateTime.now());
        gradebook.setPublishedBy(actor);
        gradebookRepository.save(gradebook);
        logStatus(gradebook, actor, "PUBLISH", GradebookStatus.APPROVED.name(), GradebookStatus.PUBLISHED.name());
        return summarizeSection(gradebook.getCourseSection());
    }

    @Transactional(readOnly = true)
    public List<GradeAuditLogDto> getAuditLogs(Long gradebookId) {
        Gradebook gradebook = requireGradebook(gradebookId);
        return gradeAuditLogRepository.findByGradebookIdOrderByCreatedAtDesc(gradebook.getId()).stream()
                .map(log -> {
                    Enrollment enrollment = log.getEnrollment();
                    return GradeAuditLogDto.builder()
                            .id(log.getId())
                            .actor(log.getActor())
                            .action(log.getAction())
                            .oldValue(log.getOldValue())
                            .newValue(log.getNewValue())
                            .reason(log.getReason())
                            .createdAt(log.getCreatedAt())
                            .enrollmentId(enrollment == null ? null : enrollment.getId())
                            .studentCode(enrollment == null || enrollment.getStudent() == null ? null
                                    : enrollment.getStudent().getStudentCode())
                            .studentName(enrollment == null || enrollment.getStudent() == null ? null
                                    : enrollment.getStudent().getFullName())
                            .componentName(log.getGradeComponent() == null ? null : log.getGradeComponent().getComponentName())
                            .build();
                }).collect(Collectors.toList());
    }

    private GradebookSummaryDto summarizeSection(CourseSection section) {
        Gradebook gradebook = getOrCreateGradebook(section);
        Progress progress = progress(gradebook);
        int componentCount = (int) gradeComponentRepository.countByGradebookId(gradebook.getId());
        return GradebookSummaryDto.builder()
                .gradebookId(gradebook.getId())
                .sectionId(section.getId())
                .sectionCode(section.getSectionCode())
                .courseId(section.getCourse() == null ? null : section.getCourse().getId())
                .courseCode(section.getCourse() == null ? null : section.getCourse().getCourseCode())
                .courseName(section.getCourse() == null ? null : section.getCourse().getCourseName())
                .semesterId(section.getSemester() == null ? null : section.getSemester().getId())
                .semesterName(section.getSemester() == null ? null : section.getSemester().getSemesterName())
                .lecturerId(section.getLecturer() == null ? null : section.getLecturer().getId())
                .lecturerName(section.getLecturer() == null ? null : section.getLecturer().getFullName())
                .totalStudents(progress.total)
                .completedStudents(progress.complete)
                .incompleteStudents(progress.incomplete)
                .status(gradebook.getStatus().name())
                .gradingMode(componentCount > 0 ? "COMPONENT" : "FINAL")
                .componentCount(componentCount)
                .lockedAt(gradebook.getLockedAt())
                .lockedBy(gradebook.getLockedBy())
                .approvedAt(gradebook.getApprovedAt())
                .approvedBy(gradebook.getApprovedBy())
                .publishedAt(gradebook.getPublishedAt())
                .publishedBy(gradebook.getPublishedBy())
                .build();
    }

    private Progress progress(Gradebook gradebook) {
        List<Enrollment> enrollments = enrolledEnrollments(gradebook.getCourseSection().getId());
        List<GradeComponent> components = gradeComponentRepository
                .findByGradebookIdOrderByDisplayOrderAscIdAsc(gradebook.getId());
        Map<Long, Map<Long, BigDecimal>> scoreMap = scoresByEnrollment(gradebook.getId());
        Map<Long, Grade> grades = gradesByEnrollment(enrollments);
        long complete = enrollments.stream()
                .filter(e -> isComplete(components, scoreMap.getOrDefault(e.getId(), Map.of()), grades.get(e.getId())))
                .count();
        Progress progress = new Progress();
        progress.total = enrollments.size();
        progress.complete = complete;
        progress.incomplete = enrollments.size() - complete;
        return progress;
    }

    private List<Enrollment> enrolledEnrollments(Long sectionId) {
        return enrollmentRepository.findByCourseSectionId(sectionId).stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
                .collect(Collectors.toList());
    }

    private Map<Long, Map<Long, BigDecimal>> scoresByEnrollment(Long gradebookId) {
        Map<Long, Map<Long, BigDecimal>> result = new HashMap<>();
        for (GradeComponentScore score : gradeComponentScoreRepository.findByGradeComponentGradebookId(gradebookId)) {
            result.computeIfAbsent(score.getEnrollment().getId(), k -> new HashMap<>())
                    .put(score.getGradeComponent().getId(), score.getScore());
        }
        return result;
    }

    private Map<Long, Grade> gradesByEnrollment(List<Enrollment> enrollments) {
        if (enrollments.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = enrollments.stream().map(Enrollment::getId).collect(Collectors.toList());
        return gradeRepository.findByEnrollmentIdIn(ids).stream()
                .collect(Collectors.toMap(g -> g.getEnrollment().getId(), Function.identity(), (a, b) -> a));
    }

    private boolean isComplete(List<GradeComponent> components, Map<Long, BigDecimal> scores, Grade grade) {
        if (components.isEmpty()) {
            return grade != null && grade.getFinalScore() != null;
        }
        for (GradeComponent component : components) {
            if (scores.get(component.getId()) == null) {
                return false;
            }
        }
        return true;
    }

    private BigDecimal calculateFinalScore(List<GradeComponent> components, Map<Long, BigDecimal> scores) {
        BigDecimal total = BigDecimal.ZERO;
        for (GradeComponent component : components) {
            BigDecimal score = scores.get(component.getId());
            if (score == null) {
                return null;
            }
            BigDecimal normalized = score.multiply(TEN)
                    .divide(component.getMaxScore(), 6, RoundingMode.HALF_UP);
            total = total.add(normalized.multiply(component.getWeight()).divide(HUNDRED, 6, RoundingMode.HALF_UP));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeScore(BigDecimal raw, BigDecimal maxScore, String label) {
        BigDecimal score = raw.setScale(2, RoundingMode.HALF_UP);
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(maxScore) > 0) {
            throw new BadRequestException(label + " must be between 0 and " + maxScore.stripTrailingZeros().toPlainString());
        }
        return score;
    }

    private void validateComponentConfig(List<GradeComponentRequest> items) {
        Set<String> names = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (GradeComponentRequest item : items) {
            if (item.getComponentName() == null || item.getComponentName().isBlank()) {
                throw new BadRequestException("Component name is required");
            }
            String key = item.getComponentName().trim().toLowerCase();
            if (!names.add(key)) {
                throw new BadRequestException("Component names must be unique");
            }
            if (item.getWeight() == null) {
                throw new BadRequestException("Component weight is required");
            }
            if (item.getWeight().compareTo(BigDecimal.ZERO) <= 0
                    || item.getWeight().compareTo(HUNDRED) > 0) {
                throw new BadRequestException("Component weight must be greater than 0 and at most 100");
            }
            if (item.getMaxScore() != null
                    && (item.getMaxScore().compareTo(BigDecimal.ZERO) <= 0
                    || item.getMaxScore().compareTo(HUNDRED) > 0)) {
                throw new BadRequestException("Component max score must be greater than 0 and at most 100");
            }
            total = total.add(item.getWeight());
        }
        if (!items.isEmpty() && total.compareTo(HUNDRED) != 0) {
            throw new BadRequestException("Total component weight must equal 100%");
        }
    }

    private String describeComponents(List<GradeComponent> components) {
        if (components.isEmpty()) {
            return "[]";
        }
        return components.stream()
                .sorted(Comparator.comparing(GradeComponent::getDisplayOrder))
                .map(c -> c.getComponentName() + " (" + c.getWeight().stripTrailingZeros().toPlainString() + "%)")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private void logStatus(Gradebook gradebook, String actor, String action, String oldValue, String newValue) {
        gradeAuditLogRepository.save(GradeAuditLog.builder()
                .gradebook(gradebook)
                .actor(actor)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .build());
    }

    private void logChange(Gradebook gradebook, Enrollment enrollment, GradeComponent component,
                           String actor, String action, Object oldValue, Object newValue) {
        gradeAuditLogRepository.save(GradeAuditLog.builder()
                .gradebook(gradebook)
                .enrollment(enrollment)
                .gradeComponent(component)
                .actor(actor)
                .action(action)
                .oldValue(oldValue == null ? null : String.valueOf(oldValue))
                .newValue(newValue == null ? null : String.valueOf(newValue))
                .build());
    }

    private Gradebook getOrCreateGradebook(CourseSection section) {
        return gradebookRepository.findByCourseSectionId(section.getId())
                .orElseGet(() -> gradebookRepository.save(Gradebook.builder()
                        .courseSection(section)
                        .status(GradebookStatus.OPEN)
                        .build()));
    }

    private Gradebook requireGradebook(Long gradebookId) {
        return gradebookRepository.findById(gradebookId)
                .orElseThrow(() -> new NotFoundException("Gradebook not found"));
    }

    private CourseSection requireSection(Long sectionId) {
        return courseSectionRepository.findById(sectionId)
                .orElseThrow(() -> new NotFoundException("Course section not found"));
    }

    private User currentUser(String username) {
        if (username == null) {
            throw new ForbiddenException("Not authenticated");
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private void requireAdmin(String username) {
        User user = currentUser(username);
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Admin permission is required");
        }
    }

    private void requireSectionAccess(CourseSection section, User user) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        Lecturer lecturer = user.getLecturer();
        if (lecturer == null || section.getLecturer() == null
                || !section.getLecturer().getId().equals(lecturer.getId())) {
            throw new ForbiddenException("You are not assigned to this course section");
        }
    }

    private GradeComponentDto toComponentDto(GradeComponent component) {
        return GradeComponentDto.builder()
                .id(component.getId())
                .componentName(component.getComponentName())
                .weight(component.getWeight())
                .maxScore(component.getMaxScore())
                .componentType(component.getComponentType())
                .displayOrder(component.getDisplayOrder())
                .required(component.getRequired())
                .build();
    }

    private static class Progress {
        private long total;
        private long complete;
        private long incomplete;
    }
}

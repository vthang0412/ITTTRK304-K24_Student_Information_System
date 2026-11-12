package com.example.student_management.entity.academic;



import com.example.student_management.entity.auth.Lecturer;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "course_sections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String sectionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id")
    private Lecturer lecturer;

    @Column(nullable = false)
    private Integer capacity;

    @Column(length = 500)
    private String schedule;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private LocalDate scheduleEffectiveFrom;

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long registeredCount;

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String deleteBlockedReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseSectionStatus status;
}

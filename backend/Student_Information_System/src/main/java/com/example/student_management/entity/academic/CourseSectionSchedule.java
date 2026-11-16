package com.example.student_management.entity.academic;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "course_section_schedules", indexes = {
        @Index(name = "idx_section_schedules_section_dates", columnList = "section_id,effective_from,effective_to")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSectionSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private CourseSection courseSection;

    @Column(length = 500)
    private String schedule;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "cancelled_from")
    private LocalDate cancelledFrom;
}

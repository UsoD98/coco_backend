package com.eodegano.cocobackend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
    name = "tour_course_user_defined_detail",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_course_date_seq_type",
            columnNames = {"tour_course_id", "date", "seq", "type"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TourCourseUserDefinedDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // FK는 DB 레벨에서만 관리 (CASCADE DELETE는 DB에서 처리)
    @Column(name = "tour_course_id", nullable = false)
    private Long tourCourseId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "time", nullable = false)
    private LocalTime time;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "cost")
    private Integer cost;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

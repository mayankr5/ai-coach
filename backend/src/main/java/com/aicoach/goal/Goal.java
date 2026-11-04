package com.aicoach.goal;

import com.aicoach.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "goals")
@Getter
@Setter
@NoArgsConstructor
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private LocalDate deadline;

    @Column(name = "minutes_per_day", nullable = false)
    private int minutesPerDay;

    /** Allowed on top of minutesPerDay on catch-up days only; the plan itself never uses it. */
    @Column(name = "extra_minutes_per_day", nullable = false)
    private int extraMinutesPerDay;

    /** Weekday kept free of planned tasks for catching up; null for goals created before buffer days. */
    @Enumerated(EnumType.STRING)
    @Column(name = "buffer_day")
    private DayOfWeek bufferDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false)
    private ExperienceLevel experienceLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalStatus status = GoalStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<Milestone> milestones = new ArrayList<>();
}

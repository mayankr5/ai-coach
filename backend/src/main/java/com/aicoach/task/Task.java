package com.aicoach.task;

import com.aicoach.goal.Goal;
import com.aicoach.goal.Milestone;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Comparator;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class Task {

    /** Gap between planned tasks' sequence numbers, leaving room for catch-up tasks in between. */
    public static final int SEQUENCE_STEP = 1000;

    /** How a goal's tasks are listed: by day, then by place in the series; id only breaks ties. */
    public static final Comparator<Task> DAY_ORDER = Comparator.comparing(Task::getScheduledDate)
            .thenComparingInt(Task::getSequence)
            .thenComparing(Task::getId);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    @Column(nullable = false)
    private String title;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "carried_over", nullable = false)
    private boolean carriedOver;

    /**
     * Place in the goal's series. Future dates are derived from it by the re-planner, which walks tasks in
     * this order, so a lower number never lands on a later day. Not unique: ties are broken by id.
     */
    @Column(name = "sequence_no", nullable = false)
    private int sequence;
}

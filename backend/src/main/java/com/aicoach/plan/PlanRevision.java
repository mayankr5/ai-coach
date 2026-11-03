package com.aicoach.plan;

import com.aicoach.goal.Goal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "plan_revisions")
@Getter
@Setter
@NoArgsConstructor
public class PlanRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Column(nullable = false)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RevisionSource source = RevisionSource.INITIAL;

    /** "provider/model" that wrote the plan or message; null when no AI was involved. */
    @Column(name = "ai_model")
    private String aiModel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

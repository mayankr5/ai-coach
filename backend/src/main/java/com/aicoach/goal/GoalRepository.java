package com.aicoach.goal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    /** Scoped lookup: returns empty for goals owned by someone else, so callers answer 404. */
    Optional<Goal> findByIdAndUserId(Long id, Long userId);

    Page<Goal> findByUserId(Long userId, Pageable pageable);

    Page<Goal> findByUserIdAndStatus(Long userId, GoalStatus status, Pageable pageable);
}

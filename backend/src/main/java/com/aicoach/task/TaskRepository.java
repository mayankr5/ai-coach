package com.aicoach.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByGoalIdAndScheduledDateOrderByIdAsc(Long goalId, LocalDate date);

    List<Task> findByGoalIdOrderByScheduledDateAscIdAsc(Long goalId);

    /** True once the user has recorded any progress on the goal. */
    boolean existsByGoalIdAndStatusNot(Long goalId, TaskStatus status);

    @Modifying
    @Query("delete from Task t where t.goal.id = :goalId")
    void deleteByGoalId(Long goalId);

    /** Ownership check through the goal, in one query. */
    @Query("select t from Task t where t.id = :taskId and t.goal.user.id = :userId")
    Optional<Task> findOwned(Long taskId, Long userId);
}

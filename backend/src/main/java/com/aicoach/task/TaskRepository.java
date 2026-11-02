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

    @Query("""
            select t from Task t where t.goal.id = :goalId and t.status = com.aicoach.task.TaskStatus.PENDING
            and t.scheduledDate >= :from order by t.scheduledDate, t.id""")
    List<Task> findUpcomingPending(Long goalId, LocalDate from);

    @Query("""
            select t from Task t where t.goal.id = :goalId and t.status = com.aicoach.task.TaskStatus.PENDING
            and t.scheduledDate <= :until""")
    List<Task> findOverduePending(Long goalId, LocalDate until);

    /** Skipped or partial work the re-planner hasn't seen yet. */
    @Query("""
            select t from Task t where t.goal.id = :goalId and t.carriedOver = false and t.scheduledDate < :before
            and t.status in (com.aicoach.task.TaskStatus.SKIPPED, com.aicoach.task.TaskStatus.PARTIAL)
            order by t.scheduledDate, t.id""")
    List<Task> findUncarriedMissed(Long goalId, LocalDate before);

    List<Task> findByGoalIdAndScheduledDateBetween(Long goalId, LocalDate from, LocalDate to);

    List<Task> findByGoalIdAndIdIn(Long goalId, List<Long> ids);

    List<Task> findByGoalId(Long goalId);

    /** Ownership check through the goal, in one query. */
    @Query("select t from Task t where t.id = :taskId and t.goal.user.id = :userId")
    Optional<Task> findOwned(Long taskId, Long userId);
}

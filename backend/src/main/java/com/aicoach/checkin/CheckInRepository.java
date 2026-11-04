package com.aicoach.checkin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    Optional<CheckIn> findByGoalIdAndCheckInDate(Long goalId, LocalDate date);

    boolean existsByGoalIdAndCheckInDate(Long goalId, LocalDate date);

    Optional<CheckIn> findFirstByGoalIdOrderByCheckInDateDesc(Long goalId);

    Page<CheckIn> findByGoalId(Long goalId, Pageable pageable);
}

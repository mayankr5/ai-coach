package com.aicoach.adjust;

import com.aicoach.checkin.CheckInDtos.NextMilestone;
import com.aicoach.common.MaybeNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class AdjustmentDtos {

    private AdjustmentDtos() {
    }

    /**
     * How far behind the next milestone is, and what each fix would change. The options are null when the plan
     * fits, and {@code moreTime} also when no realistic amount of daily time could fit the milestone's work.
     */
    public record AdjustmentOptions(int minutesBehind, int healthPercent, @MaybeNull NextMilestone milestone,
                                    @MaybeNull ExtendOption extend, @MaybeNull MoreTimeOption moreTime) {
    }

    /** Moves the milestone, every later one and the deadline by {@code days}. */
    public record ExtendOption(int days, LocalDate milestoneDate, LocalDate deadline) {
    }

    /**
     * The smallest daily time at which the milestone's tasks fit by its date.
     *
     * @param realistic false when it's more than half as much again as the current daily time
     */
    public record MoreTimeOption(int minutesPerDay, int currentMinutesPerDay, boolean realistic) {
    }

    /** The AI's picks to drop, for the user to confirm; {@code minutesFreed} if all of them go. */
    public record CutSuggestion(int minutesBehind, int minutesFreed, List<SuggestedCut> tasks) {
    }

    public record SuggestedCut(long taskId, String title, int minutes, LocalDate scheduledDate, String reason) {
    }

    /** The tasks the user chose to drop; any upcoming pending tasks, not only the suggested ones. */
    public record CutScopeRequest(@NotEmpty @Size(max = 50) List<@NotNull Long> taskIds) {
    }

    public record ExtendRequest(@NotNull @Min(1) @Max(365) Integer days) {
    }

    public record MoreTimeRequest(@NotNull @Min(10) @Max(600) Integer minutesPerDay) {
    }
}

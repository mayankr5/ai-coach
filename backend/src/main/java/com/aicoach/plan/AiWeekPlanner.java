package com.aicoach.plan;

import com.aicoach.ai.AiChain;
import com.aicoach.ai.AiProperties;
import com.aicoach.ai.AiResult;
import com.aicoach.ai.AiTask;
import com.aicoach.plan.WeekRequest.MilestoneSpan;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Asks the AI chain for the next week of an ongoing plan; tasks outside the week are dropped before validation. */
@Component
public class AiWeekPlanner implements WeekPlanner {

    /** Below this completion rate the next week is made a little lighter. */
    static final double STRUGGLING = 0.7;

    private static final String SYSTEM_PROMPT = """
            You are a realistic, encouraging coach planning the next week of an ongoing study/work plan.
            Continue from what is already done; never repeat a done or still planned task.
            Tasks are concrete daily actions for the milestone each date belongs to.
            Follow the numeric targets in the request exactly. Dates use the format YYYY-MM-DD.
            """;

    private final AiChain chain;
    private final AiProperties props;
    private final PlanValidator validator;

    public AiWeekPlanner(AiChain chain, AiProperties props, PlanValidator validator) {
        this.chain = chain;
        this.props = props;
        this.validator = validator;
    }

    @Override
    public AiResult<PlannedWeek> plan(WeekRequest req) {
        PlanRequest window = req.window();
        return chain.call(new AiTask<>("plan the next week", props.planTimeout(), props.planBudget(),
                (client, errors) -> {
                    PlannedWeek raw = client.prompt().system(SYSTEM_PROMPT).user(userPrompt(req, errors))
                            .call().entity(PlannedWeek.class);
                    return raw == null || raw.tasks() == null ? raw : new PlannedWeek(raw.tasks().stream()
                            .filter(Objects::nonNull)
                            .filter(t -> t.date() == null || !t.date().isBefore(window.today())
                                    && !t.date().isAfter(window.taskWindowEnd()))
                            .toList());
                },
                week -> validator.validateWeek(week, window)));
    }

    static String userPrompt(WeekRequest req, List<String> previousErrors) {
        PlanRequest w = req.window();
        long days = ChronoUnit.DAYS.between(w.today(), w.taskWindowEnd()) + 1;
        double fill = w.bufferDay() == null ? AiPlanGenerator.DAILY_FILL : AiPlanGenerator.DAILY_FILL_WITH_BUFFER_DAY;
        int dailyTarget = (int) Math.round(w.minutesPerDay() * fill);
        boolean struggling = req.completionRate() != null && req.completionRate() < STRUGGLING;

        StringBuilder sb = new StringBuilder()
                .append("Goal: ").append(w.title()).append('\n')
                .append("Details: ").append(w.description() == null ? "none" : w.description()).append('\n')
                .append("Experience level: ").append(w.experienceLevel()).append('\n')
                .append("Deadline: ").append(w.deadline()).append('\n')
                .append("Completion rate (last 2 weeks): ")
                .append(req.completionRate() == null ? "no data yet" : Math.round(req.completionRate() * 100) + "%")
                .append(struggling ? " - the user is struggling, so keep tasks small and simple" : "")
                .append("\n\nMilestones for these dates:\n");
        for (MilestoneSpan m : req.milestones()) {
            sb.append("- ").append(m.from()).append(" to ").append(m.to()).append(": ").append(m.title())
                    .append(" (target ").append(m.targetDate()).append(")\n");
        }
        list(sb, "Already done, oldest first", req.done());
        list(sb, "Still planned before these dates (don't repeat them)", req.pending());

        sb.append("\nTargets:\n")
                .append("- Tasks only on dates ").append(w.today()).append(" to ").append(w.taskWindowEnd())
                .append(" (").append(days).append(" days)\n");
        List<LocalDate> bufferDates = w.bufferDates();
        if (!bufferDates.isEmpty()) {
            sb.append("- No tasks at all on ").append(bufferDates.stream().map(LocalDate::toString)
                    .collect(Collectors.joining(", "))).append(" (buffer days, kept free for catching up)\n");
        }
        sb.append("- About ").append(dailyTarget).append(" minutes of tasks per day, never more than ")
                .append(w.minutesPerDay()).append(" on one date\n")
                .append("- Usually 1 or 2 tasks per day of 20 to ").append(w.minutesPerDay()).append(" minutes each\n")
                .append("- About ").append(w.workingDays() * dailyTarget).append(" task minutes in total\n");
        if (!previousErrors.isEmpty()) {
            sb.append("\nYour previous answer was rejected. Fix these problems:\n");
            previousErrors.forEach(e -> sb.append("- ").append(e).append('\n'));
        }
        return sb.toString();
    }

    private static void list(StringBuilder sb, String heading, List<String> titles) {
        sb.append('\n').append(heading).append(":\n");
        if (titles.isEmpty()) {
            sb.append("- none\n");
        }
        titles.forEach(t -> sb.append("- ").append(t).append('\n'));
    }
}

package com.aicoach.plan;

import com.aicoach.ai.AiChain;
import com.aicoach.ai.AiProperties;
import com.aicoach.ai.AiResult;
import com.aicoach.ai.AiTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Asks the AI chain for a plan. Out-of-window tasks are dropped before validation, and a rejected plan's
 * problems are sent back, also to the next model in the chain, so it can fix them.
 */
@Component
public class AiPlanGenerator implements PlanGenerator {

    private static final Logger log = LoggerFactory.getLogger(AiPlanGenerator.class);
    /** Share of the daily time to plan, leaving room for catch-up work. */
    static final double DAILY_FILL = 0.8;
    /** With a weekly buffer day the slack lives in that day, so working days can be fuller. */
    static final double DAILY_FILL_WITH_BUFFER_DAY = 0.9;

    private static final String SYSTEM_PROMPT = """
            You are a realistic, encouraging coach who turns a goal into a study/work plan.
            Milestones are big checkpoints spread across the whole period up to the deadline.
            Tasks are concrete daily actions, and most days should use close to the daily time.
            Follow the numeric targets in the request exactly. Dates use the format YYYY-MM-DD.
            """;

    private final AiChain chain;
    private final AiProperties props;
    private final PlanRepairer repairer;
    private final PlanValidator validator;

    public AiPlanGenerator(AiChain chain, AiProperties props, PlanRepairer repairer, PlanValidator validator) {
        this.chain = chain;
        this.props = props;
        this.repairer = repairer;
        this.validator = validator;
    }

    @Override
    public AiResult<GeneratedPlan> generate(PlanRequest req) {
        return chain.call(new AiTask<>("generate a plan", props.planTimeout(), props.planBudget(),
                (client, errors) -> {
                    GeneratedPlan raw = client.prompt().system(SYSTEM_PROMPT).user(userPrompt(req, errors))
                            .call().entity(GeneratedPlan.class);
                    if (raw == null) {
                        return null;
                    }
                    PlanRepairer.Repaired repaired = repairer.repair(raw, req);
                    if (repaired.droppedTasks() > 0) {
                        log.info("Dropped {} out-of-window tasks from an AI plan", repaired.droppedTasks());
                    }
                    return repaired.plan();
                },
                plan -> validator.validate(plan, req)));
    }

    private static String userPrompt(PlanRequest req, List<String> previousErrors) {
        long totalDays = ChronoUnit.DAYS.between(req.today(), req.deadline()) + 1;
        long windowDays = ChronoUnit.DAYS.between(req.today(), req.taskWindowEnd()) + 1;
        // Small models follow concrete numbers far better than abstract rules, so the targets are computed here
        long milestoneCount = Math.max(2, Math.min(8, Math.round(totalDays / 10.0)));
        // Slack is kept either a little each day or, with a buffer day, all in that day
        double fill = req.bufferDay() == null ? DAILY_FILL : DAILY_FILL_WITH_BUFFER_DAY;
        int dailyTarget = (int) Math.round(req.minutesPerDay() * fill);
        long windowTarget = req.workingDays() * dailyTarget;
        List<LocalDate> bufferDates = req.bufferDates();

        StringBuilder sb = new StringBuilder()
                .append("Goal: ").append(req.title()).append('\n')
                .append("Details: ").append(req.description() == null ? "none" : req.description()).append('\n')
                .append("Experience level: ").append(req.experienceLevel()).append('\n')
                .append("Today: ").append(req.today()).append('\n')
                .append("Deadline: ").append(req.deadline()).append(" (").append(totalDays).append(" days)\n\n")
                .append("Targets:\n")
                .append("- Exactly ").append(milestoneCount).append(" milestones, evenly spaced; the last milestone's ")
                .append("targetDate is ").append(req.deadline()).append('\n')
                .append("- Tasks only on dates ").append(req.today()).append(" to ").append(req.taskWindowEnd())
                .append(" (").append(windowDays).append(" days); later milestones get an empty tasks list\n");
        if (!bufferDates.isEmpty()) {
            sb.append("- No tasks at all on ").append(bufferDates.stream().map(LocalDate::toString)
                    .collect(Collectors.joining(", "))).append(" (buffer days, kept free for catching up)\n");
        }
        sb
                .append("- About ").append(dailyTarget).append(" minutes of tasks per day, never more than ")
                .append(req.minutesPerDay()).append(" on one date\n")
                .append("- Usually 1 or 2 tasks per day of 20 to ").append(req.minutesPerDay()).append(" minutes each\n")
                .append("- About ").append(windowTarget).append(" task minutes in total\n");
        if (!previousErrors.isEmpty()) {
            sb.append("\nYour previous plan was rejected. Fix these problems:\n");
            previousErrors.forEach(e -> sb.append("- ").append(e).append('\n'));
        }
        return sb.toString();
    }
}

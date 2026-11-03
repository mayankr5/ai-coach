package com.aicoach.plan;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;

/** Provider-agnostic generator: the ChatClient is backed by whichever model spring.ai.model.chat selects. */
@Component
public class AiPlanGenerator implements PlanGenerator {

    private static final String SYSTEM_PROMPT = """
            You are a realistic, encouraging coach who turns a goal into a study/work plan.
            Milestones are big checkpoints spread across the whole period up to the deadline.
            Tasks are concrete daily actions, and most days should use close to the daily time.
            Follow the numeric targets in the request exactly. Dates use the format YYYY-MM-DD.
            """;

    private final ChatClient chatClient;

    public AiPlanGenerator(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT)
                // Logs prompts and responses at DEBUG: logging.level.org.springframework.ai.chat.client.advisor=DEBUG
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Override
    public GeneratedPlan generate(PlanRequest req, List<String> previousErrors) {
        try {
            return chatClient.prompt()
                    .user(userPrompt(req, previousErrors))
                    .call()
                    .entity(GeneratedPlan.class);
        } catch (RuntimeException e) {
            throw new AiUnavailableException("AI provider failed to generate a plan", e);
        }
    }

    private static String userPrompt(PlanRequest req, List<String> previousErrors) {
        long totalDays = ChronoUnit.DAYS.between(req.today(), req.deadline()) + 1;
        long windowDays = ChronoUnit.DAYS.between(req.today(), req.taskWindowEnd()) + 1;
        // Small models follow concrete numbers far better than abstract rules, so the targets are computed here
        long milestoneCount = Math.max(2, Math.min(8, Math.round(totalDays / 10.0)));
        int dailyTarget = (int) Math.round(req.minutesPerDay() * 0.8);
        long windowTarget = windowDays * dailyTarget;

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
                .append(" (").append(windowDays).append(" days); later milestones get an empty tasks list\n")
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

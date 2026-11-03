package com.aicoach.replan;

import com.aicoach.plan.AiUnavailableException;
import com.aicoach.replan.RePlanRequest.TaskView;
import com.aicoach.task.TaskStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Provider-agnostic re-planner backed by whichever model spring.ai.model.chat selects. */
@Component
public class AiRePlanner implements RePlanner {

    private static final String SYSTEM_PROMPT = """
            You are a realistic, encouraging coach adjusting a user's plan after their daily check-in.
            Return a short coachMessage (1-3 sentences, specific and honest, no generic praise) and a list of changes.
            If the user wrote a note, the coachMessage must respond to it directly.
            Allowed actions:
            - MOVE: give taskId and a new date
            - RESIZE: give taskId and new minutes
            - REMOVE: give taskId
            - ADD: give title, date, minutes and optionally milestoneId
            Rules:
            - Only change tasks listed under "Upcoming tasks"; never invent task ids.
            - All dates must be between the first and last allowed dates.
            - The total minutes on any date must not exceed the daily limit.
            - For every task under "Missed or partly done", ADD exactly one follow-up task as instructed there.
            - If the completion rate is low, make days lighter rather than piling work up.
            - Fill empty days up to the last allowed date with concrete tasks for the current milestone.
            - Change as little as needed. An empty list of changes is fine if the plan still works.
            - Dates use the format YYYY-MM-DD.
            """;

    private final ChatClient chatClient;

    public AiRePlanner(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT)
                // Logs prompts and responses at DEBUG: logging.level.org.springframework.ai.chat.client.advisor=DEBUG
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Override
    public PlanChanges replan(RePlanRequest req, List<String> previousErrors) {
        try {
            return chatClient.prompt()
                    .user(userPrompt(req, previousErrors))
                    .call()
                    .entity(PlanChanges.class);
        } catch (RuntimeException e) {
            throw new AiUnavailableException("AI provider failed to re-plan", e);
        }
    }

    private static String userPrompt(RePlanRequest req, List<String> previousErrors) {
        StringBuilder sb = new StringBuilder()
                .append("Goal: ").append(req.title()).append('\n')
                .append("Experience level: ").append(req.experienceLevel()).append('\n')
                .append("Daily time limit: ").append(req.minutesPerDay()).append(" minutes\n")
                .append("Deadline: ").append(req.deadline()).append('\n')
                .append("Allowed dates: ").append(req.from()).append(" to ").append(req.windowEnd()).append('\n')
                .append("Completion rate (last 2 weeks): ")
                .append(req.completionRate() == null ? "no data yet" : Math.round(req.completionRate() * 100) + "%")
                .append('\n')
                .append("User's note: ").append(req.note() == null || req.note().isBlank() ? "none" : req.note())
                .append("\n\nMilestones:\n");
        req.milestones().forEach(m -> sb.append("- id ").append(m.id()).append(": ").append(m.title())
                .append(" (target ").append(m.targetDate()).append(")\n"));
        sb.append("\nMissed or partly done:\n");
        if (req.missed().isEmpty()) {
            sb.append("- none\n");
        }
        req.missed().forEach(t -> sb.append("- ").append(t.status()).append(": ").append(t.title())
                .append(" (").append(t.minutes()).append(" min, was ").append(t.date()).append(") -> ADD ")
                .append(t.status() == TaskStatus.PARTIAL
                        ? "\"Finish: " + t.title() + "\" of about " + Math.max(5, t.minutes() / 2) + " min"
                        : "\"" + t.title() + "\" of " + Math.min(t.minutes(), req.minutesPerDay()) + " min")
                .append(" on a day with free time\n"));
        sb.append("\nUpcoming tasks:\n");
        if (req.upcoming().isEmpty()) {
            sb.append("- none\n");
        }
        req.upcoming().forEach(t -> appendTask(sb, t));
        if (!previousErrors.isEmpty()) {
            sb.append("\nYour previous changes were rejected. Fix these problems:\n");
            previousErrors.forEach(e -> sb.append("- ").append(e).append('\n'));
        }
        return sb.toString();
    }

    private static void appendTask(StringBuilder sb, TaskView t) {
        sb.append("- id ").append(t.id()).append(": ").append(t.title()).append(" on ").append(t.date())
                .append(", ").append(t.minutes()).append(" min");
        if (t.milestoneId() != null) {
            sb.append(", milestone ").append(t.milestoneId());
        }
        sb.append('\n');
    }
}

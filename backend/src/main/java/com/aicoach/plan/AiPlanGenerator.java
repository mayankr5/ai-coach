package com.aicoach.plan;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/** Provider-agnostic generator: the ChatClient is backed by whichever model spring.ai.model.chat selects. */
@Component
public class AiPlanGenerator implements PlanGenerator {

    private static final String SYSTEM_PROMPT = """
            You are a realistic, encouraging coach who turns a goal into a study/work plan.
            Rules:
            - Split the goal into 2-8 milestones in order, each with a targetDate no later than the deadline.
            - Schedule concrete, specific tasks only between the first and last allowed task dates.
              Milestones after that window may have an empty tasks list; they are planned later.
            - The total minutes of all tasks on one date must not exceed the daily limit.
              Leave some days lighter for review or rest.
            - Each task is one concrete action of 5 minutes up to the daily limit.
            - Match difficulty to the experience level.
            - Dates use the format YYYY-MM-DD.
            """;

    private final ChatClient chatClient;

    public AiPlanGenerator(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
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
        StringBuilder sb = new StringBuilder()
                .append("Goal: ").append(req.title()).append('\n')
                .append("Details: ").append(req.description() == null ? "none" : req.description()).append('\n')
                .append("Experience level: ").append(req.experienceLevel()).append('\n')
                .append("Daily time limit: ").append(req.minutesPerDay()).append(" minutes\n")
                .append("Today: ").append(req.today()).append('\n')
                .append("Deadline: ").append(req.deadline()).append('\n')
                .append("Tasks allowed from ").append(req.today()).append(" to ").append(req.taskWindowEnd()).append('\n');
        if (!previousErrors.isEmpty()) {
            sb.append("\nYour previous plan was rejected. Fix these problems:\n");
            previousErrors.forEach(e -> sb.append("- ").append(e).append('\n'));
        }
        return sb.toString();
    }
}

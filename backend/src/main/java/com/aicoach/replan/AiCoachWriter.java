package com.aicoach.replan;

import com.aicoach.ai.AiChain;
import com.aicoach.ai.AiProperties;
import com.aicoach.ai.AiResult;
import com.aicoach.ai.AiTask;
import com.aicoach.replan.PlanChanges.TaskChange;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/** Writes coach messages through the AI chain, so any configured model can answer. */
@Component
public class AiCoachWriter implements CoachWriter {

    static final int MAX_MESSAGE = 500;

    private static final String SYSTEM_PROMPT = """
            You are a realistic, encouraging coach writing a short message after a user's daily check-in.
            The app has already adjusted the plan; you only explain it. Never invent changes, dates or tasks.
            Write 1-3 sentences, specific and honest, with no generic praise.
            If the user wrote a note, respond to it directly and give one concrete, practical tip.
            Mention the most important plan change in plain words.
            If some work doesn't fit before the deadline, say so honestly.
            """;

    record CoachMessage(String message) {
    }

    private final AiChain chain;
    private final AiProperties props;

    public AiCoachWriter(AiChain chain, AiProperties props) {
        this.chain = chain;
        this.props = props;
    }

    @Override
    public AiResult<String> write(RePlanRequest req, CatchUpPlan plan) {
        AiResult<CoachMessage> result = chain.call(new AiTask<>("write a coach message", props.messageTimeout(),
                props.messageBudget(),
                (client, problems) -> client.prompt().system(SYSTEM_PROMPT).user(userPrompt(req, plan, problems))
                        .call().entity(CoachMessage.class),
                AiCoachWriter::check));
        return new AiResult<>(result.value().message().strip(), result.model());
    }

    private static List<String> check(CoachMessage m) {
        if (m.message() == null || m.message().isBlank()) {
            return List.of("The message is empty");
        }
        if (m.message().length() > MAX_MESSAGE) {
            return List.of("The message is " + m.message().length() + " characters; keep it under " + MAX_MESSAGE);
        }
        return List.of();
    }

    private static String userPrompt(RePlanRequest req, CatchUpPlan plan, List<String> problems) {
        StringBuilder sb = new StringBuilder()
                .append("Goal: ").append(req.title()).append('\n')
                .append("Experience level: ").append(req.experienceLevel()).append('\n')
                .append("Deadline: ").append(req.deadline()).append('\n')
                .append("Completion rate (last 2 weeks): ")
                .append(req.completionRate() == null ? "no data yet" : Math.round(req.completionRate() * 100) + "%")
                .append('\n')
                .append("User's note: ").append(req.note() == null || req.note().isBlank() ? "none" : req.note())
                .append("\n\nMissed or partly done:\n");
        if (req.missed().isEmpty()) {
            sb.append("- none\n");
        }
        req.missed().forEach(t -> sb.append("- ").append(t.status()).append(": ").append(t.title()).append('\n'));

        sb.append("\nPlan changes already made:\n");
        if (plan.isEmpty()) {
            sb.append("- none, the plan stays as it is\n");
        }
        for (TaskChange f : plan.followUps()) {
            sb.append("- Added \"").append(f.title()).append("\" (").append(f.minutes()).append(" min) on ")
                    .append(f.date()).append('\n');
        }
        if (plan.shifted() > 0) {
            sb.append("- Moved ").append(plan.shifted()).append(" later tasks forward to keep them in order\n");
        }
        if (!plan.extraDays().isEmpty()) {
            sb.append("- Uses the user's extra catch-up time on ").append(plan.extraDays()).append('\n');
        }
        if (plan.overflow() > 0) {
            sb.append("- ").append(plan.overflow()).append(" tasks don't fit before the deadline\n");
        }

        sb.append("\nNext tasks:\n");
        nextTasks(req, plan).forEach(line -> sb.append("- ").append(line).append('\n'));
        if (!problems.isEmpty()) {
            sb.append("\nYour previous answer was rejected. Fix these problems:\n");
            problems.forEach(p -> sb.append("- ").append(p).append('\n'));
        }
        return sb.toString();
    }

    /** The first few tasks after the re-plan, so the message can point at what comes next. */
    private static List<String> nextTasks(RePlanRequest req, CatchUpPlan plan) {
        record Row(LocalDate date, int sequence, String title) {
        }
        Map<Long, LocalDate> moved = new HashMap<>();
        plan.changes().stream().filter(c -> c.taskId() != null).forEach(c -> moved.put(c.taskId(), c.date()));
        List<Row> rows = new ArrayList<>();
        plan.followUps().forEach(f -> rows.add(new Row(f.date(), f.sequence(), f.title())));
        req.upcoming().forEach(t -> rows.add(new Row(moved.getOrDefault(t.id(), t.date()), t.sequence(), t.title())));
        // Same order the user sees: by day, then by place in the series (catch-up work first)
        return rows.stream().sorted(Comparator.comparing(Row::date).thenComparingInt(Row::sequence)).limit(3)
                .map(r -> r.title() + " on " + r.date()).toList();
    }
}

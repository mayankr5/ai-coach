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
            Write dates exactly as the facts do, e.g. "tomorrow (Thu 5 Nov)" or "Sat 7 Nov", and mention no other dates.
            Plan changes never happen today; never call a later day "today".
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
        DayLabels labels = new DayLabels(req.today());
        String facts = facts(req, plan, labels);
        AiResult<CoachMessage> result = chain.call(new AiTask<>("write a coach message", props.messageTimeout(),
                props.messageBudget(),
                (client, problems) -> client.prompt().system(SYSTEM_PROMPT).user(withProblems(facts, problems))
                        .call().entity(CoachMessage.class),
                m -> check(m, labels)));
        return new AiResult<>(result.value().message().strip(), result.model());
    }

    /** Length limits, then dates: only today and the dates in the facts may appear. */
    static List<String> check(CoachMessage m, DayLabels labels) {
        if (m.message() == null || m.message().isBlank()) {
            return List.of("The message is empty");
        }
        if (m.message().length() > MAX_MESSAGE) {
            return List.of("The message is " + m.message().length() + " characters; keep it under " + MAX_MESSAGE);
        }
        return MessageDates.problems(m.message(), labels.today(), labels.written());
    }

    private static String withProblems(String facts, List<String> problems) {
        if (problems.isEmpty()) {
            return facts;
        }
        StringBuilder sb = new StringBuilder(facts).append("\nYour previous answer was rejected. Fix these problems:\n");
        problems.forEach(p -> sb.append("- ").append(p).append('\n'));
        return sb.toString();
    }

    /** Every date goes through {@code labels}, which records it as one the message may mention. */
    static String facts(RePlanRequest req, CatchUpPlan plan, DayLabels labels) {
        StringBuilder sb = new StringBuilder()
                .append("Today: ").append(labels.of(req.today())).append('\n')
                .append("Plan changes start ").append(labels.of(req.from()))
                .append("; today's tasks never change\n")
                .append("Goal: ").append(req.title()).append('\n')
                .append("Experience level: ").append(req.experienceLevel()).append('\n')
                .append("Deadline: ").append(labels.of(req.deadline())).append('\n')
                .append("Completion rate (last 2 weeks): ")
                .append(req.completionRate() == null ? "no data yet" : Math.round(req.completionRate() * 100) + "%")
                .append('\n')
                .append("User's note: ").append(req.note() == null || req.note().isBlank() ? "none" : req.note())
                .append("\n\nMissed or partly done:\n");
        if (req.missed().isEmpty()) {
            sb.append("- none\n");
        }
        req.missed().forEach(t -> sb.append("- ").append(t.status()).append(": ").append(t.title())
                .append(" (planned ").append(labels.of(t.date())).append(")\n"));

        sb.append("\nPlan changes already made:\n");
        if (plan.isEmpty()) {
            sb.append("- none, the plan stays as it is\n");
        }
        for (TaskChange f : plan.followUps()) {
            sb.append("- Added \"").append(f.title()).append("\" (").append(f.minutes()).append(" min) on ")
                    .append(labels.of(f.date())).append('\n');
        }
        if (plan.shifted() > 0) {
            sb.append("- Moved ").append(plan.shifted()).append(" later tasks forward to keep them in order\n");
        }
        if (!plan.extraDays().isEmpty()) {
            sb.append("- Uses the user's extra catch-up time on ").append(labels.of(plan.extraDays())).append('\n');
        }
        if (!plan.bufferDaysUsed().isEmpty()) {
            sb.append("- Uses the user's weekly buffer day (kept free for catching up) on ")
                    .append(labels.of(plan.bufferDaysUsed())).append('\n');
        }
        if (plan.overflow() > 0) {
            sb.append("- ").append(plan.overflow()).append(" tasks don't fit before the deadline\n");
        }

        sb.append("\nNext tasks:\n");
        nextTasks(req, plan, labels).forEach(line -> sb.append("- ").append(line).append('\n'));
        return sb.toString();
    }

    /** The first few tasks after the re-plan, so the message can point at what comes next. */
    private static List<String> nextTasks(RePlanRequest req, CatchUpPlan plan, DayLabels labels) {
        record Row(LocalDate date, int sequence, String title) {
        }
        Map<Long, LocalDate> moved = new HashMap<>();
        plan.changes().stream().filter(c -> c.taskId() != null).forEach(c -> moved.put(c.taskId(), c.date()));
        List<Row> rows = new ArrayList<>();
        plan.followUps().forEach(f -> rows.add(new Row(f.date(), f.sequence(), f.title())));
        req.upcoming().forEach(t -> rows.add(new Row(moved.getOrDefault(t.id(), t.date()), t.sequence(), t.title())));
        // Same order the user sees: by day, then by place in the series (catch-up work first)
        return rows.stream().sorted(Comparator.comparing(Row::date).thenComparingInt(Row::sequence)).limit(3)
                .map(r -> r.title() + " on " + labels.of(r.date())).toList();
    }
}

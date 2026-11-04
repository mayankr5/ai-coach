package com.aicoach.replan;

import com.aicoach.ai.AiChain;
import com.aicoach.ai.AiProperties;
import com.aicoach.ai.AiResult;
import com.aicoach.ai.ScriptedChatModel;
import com.aicoach.goal.ExperienceLevel;
import com.aicoach.replan.PlanChanges.Action;
import com.aicoach.replan.PlanChanges.TaskChange;
import com.aicoach.replan.RePlanRequest.TaskView;
import com.aicoach.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiCoachWriterTest {

    /** Checked in on Wednesday 4 Nov, so the re-plan starts on Thursday. */
    static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
    static final RePlanRequest REQUEST = new RePlanRequest(1L, "Learn Java", null, ExperienceLevel.BEGINNER, 60, 15,
            null, TODAY.plusMonths(2), TODAY.plusDays(1), TODAY.plusDays(14), null, "Maven failed", List.of(),
            List.of(), List.of(new TaskView(7L, "Set up JDK", TODAY, 54, null, TaskStatus.PARTIAL, 1000)), List.of());
    static final CatchUpPlan PLAN = new CatchUpPlan(
            List.of(new TaskChange(Action.ADD, null, "Finish: Set up JDK", TODAY.plusDays(1), 27, null, 1001)),
            List.of(new TaskChange(Action.ADD, null, "Finish: Set up JDK", TODAY.plusDays(1), 27, null, 1001)),
            0, List.of(), List.of(), 0);

    final ScriptedChatModel model = new ScriptedChatModel();
    final AiCoachWriter writer = new AiCoachWriter(new AiChain(List.of(model.named("test/model"))),
            new AiProperties(List.of(), Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofSeconds(5),
                    Duration.ofSeconds(10), 0.3, null, null, null, null));

    @Test
    void factsUseRelativeDayLabels() {
        model.answers(json("Run mvn -U. Finish the setup tomorrow (Thu 5 Nov)."));

        writer.write(REQUEST, PLAN);

        assertThat(model.prompts.getFirst())
                .contains("Today: today (Wed 4 Nov)")
                .contains("Plan changes start tomorrow (Thu 5 Nov); today's tasks never change")
                .contains("PARTIAL: Set up JDK (planned today (Wed 4 Nov))")
                .contains("\"Finish: Set up JDK\" (27 min) on tomorrow (Thu 5 Nov)")
                .contains("Deadline: Mon 4 Jan 2027");
    }

    @Test
    void wrongDateIsSentBackWithTheProblem() {
        model.answers(json("I added 27 minutes to your plan today (Nov 5)."))
                .answers(json("Run mvn -U; the rest of the setup is first tomorrow (Thu 5 Nov)."));

        AiResult<String> result = writer.write(REQUEST, PLAN);

        assertThat(result.value()).contains("tomorrow (Thu 5 Nov)");
        assertThat(model.prompts.get(1)).contains("You called tomorrow (Thu 5 Nov) \"today\"");
    }

    private static String json(String message) {
        return "{\"message\":\"" + message + "\"}";
    }
}

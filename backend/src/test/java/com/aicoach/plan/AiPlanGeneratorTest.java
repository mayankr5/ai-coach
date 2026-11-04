package com.aicoach.plan;

import com.aicoach.ai.AiChain;
import com.aicoach.ai.AiProperties;
import com.aicoach.ai.AiResult;
import com.aicoach.ai.ScriptedChatModel;
import com.aicoach.goal.ExperienceLevel;
import com.aicoach.plan.GeneratedPlan.PlannedMilestone;
import com.aicoach.plan.GeneratedPlan.PlannedTask;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The real prompt-and-parse path, with scripted model answers instead of a provider. */
class AiPlanGeneratorTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    static final PlanRequest REQUEST = new PlanRequest("Learn Java", null, ExperienceLevel.BEGINNER, 60,
            TODAY.plusMonths(2), TODAY, TODAY.plusDays(13), null);

    final ScriptedChatModel first = new ScriptedChatModel();
    final ScriptedChatModel second = new ScriptedChatModel();
    final AiPlanGenerator generator = new AiPlanGenerator(
            new AiChain(List.of(first.named("first/model"), second.named("second/model"))),
            new AiProperties(List.of(), Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofSeconds(5),
                    Duration.ofSeconds(10), 0.3, null, null, null, null),
            new PlanRepairer(), new PlanValidator());

    @Test
    void impossibleDateIsRetriedWithAnExplanation() {
        String valid = json(PlanFlowTest.VALID.apply(REQUEST));
        first.answers(valid.replaceFirst("\\d{4}-\\d{2}-\\d{2}", "2026-11-31")).answers(valid);

        AiResult<GeneratedPlan> result = generator.generate(REQUEST);

        assertThat(result.model()).isEqualTo("first/model");
        assertThat(first.prompts.get(1)).contains("could not be read").contains("NOVEMBER 31");
    }

    @Test
    void rejectedPlanProblemsAreSentToTheNextModel() {
        String overLimit = json(PlanFlowTest.OVER_LIMIT.apply(REQUEST));
        first.answers(overLimit).answers(overLimit);
        second.answers(json(PlanFlowTest.VALID.apply(REQUEST)));

        AiResult<GeneratedPlan> result = generator.generate(REQUEST);

        assertThat(result.model()).isEqualTo("second/model");
        assertThat(second.prompts.getFirst()).contains("previous plan was rejected").contains("500 minutes");
    }

    @Test
    void bufferDaysAreListedAndATaskOnOneIsRejected() {
        // 2026-11-03 is a Tuesday: the window holds two Sundays
        PlanRequest withBuffer = new PlanRequest("Learn Java", null, ExperienceLevel.BEGINNER, 60,
                TODAY.plusMonths(2), TODAY, TODAY.plusDays(13), DayOfWeek.SUNDAY);
        GeneratedPlan valid = PlanFlowTest.VALID.apply(withBuffer);
        List<PlannedTask> onSunday = new java.util.ArrayList<>(valid.milestones().getFirst().tasks());
        onSunday.add(new PlannedTask("Weekend reading", LocalDate.of(2026, 11, 8), 20));
        first.answers(json(new GeneratedPlan(List.of(new PlannedMilestone("Basics", withBuffer.taskWindowEnd(), onSunday),
                valid.milestones().getLast())))).answers(json(valid));

        AiResult<GeneratedPlan> result = generator.generate(withBuffer);

        assertThat(first.prompts.getFirst())
                .contains("No tasks at all on 2026-11-08, 2026-11-15")
                .contains("About 54 minutes of tasks per day")
                .contains("About 648 task minutes in total");
        assertThat(first.prompts.get(1)).contains("'Weekend reading' is on 2026-11-08, a buffer day");
        assertThat(result.value().milestones().getFirst().tasks())
                .noneMatch(t -> t.date().getDayOfWeek() == DayOfWeek.SUNDAY);
    }

    private static String json(GeneratedPlan plan) {
        return JsonMapper.builder().build().writeValueAsString(plan);
    }
}

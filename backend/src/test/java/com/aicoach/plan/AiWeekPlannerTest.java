package com.aicoach.plan;

import com.aicoach.ai.AiChain;
import com.aicoach.ai.AiProperties;
import com.aicoach.ai.AiResult;
import com.aicoach.ai.ScriptedChatModel;
import com.aicoach.goal.ExperienceLevel;
import com.aicoach.plan.GeneratedPlan.PlannedTask;
import com.aicoach.plan.WeekRequest.MilestoneSpan;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiWeekPlannerTest {

    /** Tuesday to Monday, with Sunday 15 Nov as the buffer day. */
    static final LocalDate FROM = LocalDate.of(2026, 11, 10);
    static final WeekRequest REQUEST = new WeekRequest(
            new PlanRequest("Learn Java", null, ExperienceLevel.BEGINNER, 60, FROM.plusMonths(2), FROM,
                    FROM.plusDays(6), DayOfWeek.SUNDAY),
            List.of(new MilestoneSpan("Basics", FROM.plusDays(2), FROM, FROM.plusDays(2)),
                    new MilestoneSpan("Spring", FROM.plusDays(30), FROM.plusDays(3), FROM.plusDays(6))),
            List.of("Install JDK", "Variables and loops"), List.of("Classes and objects"), 0.5);

    final ScriptedChatModel model = new ScriptedChatModel();
    final AiWeekPlanner planner = new AiWeekPlanner(new AiChain(List.of(model.named("test/model"))),
            new AiProperties(List.of(), Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofSeconds(5),
                    Duration.ofSeconds(10), 0.3, null, null, null, null),
            new PlanValidator());

    @Test
    void promptCarriesTheContextAndTargets() {
        String prompt = AiWeekPlanner.userPrompt(REQUEST, List.of());

        assertThat(prompt)
                .contains("- 2026-11-10 to 2026-11-12: Basics (target 2026-11-12)")
                .contains("- 2026-11-13 to 2026-11-16: Spring (target 2026-12-10)")
                .contains("- Install JDK\n- Variables and loops")
                .contains("don't repeat them):\n- Classes and objects")
                .contains("50% - the user is struggling")
                .contains("No tasks at all on 2026-11-15")
                .contains("About 54 minutes of tasks per day")
                .contains("About 324 task minutes in total");
    }

    @Test
    void outOfWeekTasksAreDroppedAndBufferDayTasksRetried() {
        List<PlannedTask> valid = new ArrayList<>();
        for (LocalDate d = FROM; !d.isAfter(FROM.plusDays(6)); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SUNDAY) valid.add(new PlannedTask("Task " + d, d, 50));
        }
        List<PlannedTask> first = new ArrayList<>(valid);
        first.add(new PlannedTask("Weekend project", LocalDate.of(2026, 11, 15), 30));
        first.add(new PlannedTask("Too far ahead", LocalDate.of(2026, 11, 20), 30));
        model.answers(json(first)).answers(json(valid));

        AiResult<PlannedWeek> result = planner.plan(REQUEST);

        assertThat(model.prompts.get(1)).contains("'Weekend project' is on 2026-11-15, a buffer day")
                .doesNotContain("Too far ahead");
        assertThat(result.value().tasks()).hasSize(6);
    }

    private static String json(List<PlannedTask> tasks) {
        return JsonMapper.builder().build().writeValueAsString(new PlannedWeek(tasks));
    }
}

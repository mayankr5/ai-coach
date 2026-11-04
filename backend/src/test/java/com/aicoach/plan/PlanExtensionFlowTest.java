package com.aicoach.plan;

import com.aicoach.TestcontainersConfiguration;
import com.aicoach.plan.GeneratedPlan.PlannedMilestone;
import com.aicoach.plan.GeneratedPlan.PlannedTask;
import com.aicoach.plan.WeekRequest.MilestoneSpan;
import com.aicoach.replan.MissedDayJob;
import com.aicoach.task.Task;
import com.aicoach.task.TaskRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.aicoach.TestApi.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PlanExtensionFlowTest.FakeAiConfig.class})
class PlanExtensionFlowTest {

    @TestConfiguration
    static class FakeAiConfig {
        @Bean
        @Primary
        FakePlanGenerator fakePlanGenerator() {
            return new FakePlanGenerator();
        }

        @Bean
        @Primary
        FakeWeekPlanner fakeWeekPlanner() {
            return new FakeWeekPlanner();
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    FakePlanGenerator planGenerator;
    @Autowired
    FakeWeekPlanner weekPlanner;
    @Autowired
    TaskRepository tasks;
    @Autowired
    PlanRevisionRepository revisions;
    @Autowired
    MissedDayJob missedDayJob;
    @Autowired
    JdbcTemplate jdbc;

    LocalDate today;
    String token;
    String title;
    long goalId;

    /** One 40-minute task on every working day of the week asked for. */
    static PlannedWeek fullWeek(WeekRequest req) {
        List<PlannedTask> planned = new ArrayList<>();
        PlanRequest w = req.window();
        for (LocalDate d = w.today(); !d.isAfter(w.taskWindowEnd()); d = d.plusDays(1)) {
            if (!w.isBufferDay(d)) planned.add(new PlannedTask("Week task " + d, d, 40));
        }
        return new PlannedWeek(planned);
    }

    @BeforeEach
    void setUp() throws Exception {
        planGenerator.reset();
        weekPlanner.reset();
        today = LocalDate.now(ZoneOffset.UTC);
        token = register(mvc, "UTC");
        title = "Learn Java " + UUID.randomUUID();
    }

    @Test
    void nightlyJobPlansTheNextWeekWhenThePlanRunsLow() throws Exception {
        createShortPlan(today.plusMonths(2));
        weekPlanner.answerWith(PlanExtensionFlowTest::fullWeek);
        int lastSequence = lastTask().getSequence();

        missedDayJob.run();

        // Tasks ran to today + 5 (buffer day today + 6): the next week is today + 6 to today + 12
        WeekRequest req = weekPlanner.requestsFor(title).getFirst();
        assertThat(req.window().today()).isEqualTo(today.plusDays(6));
        assertThat(req.window().taskWindowEnd()).isEqualTo(today.plusDays(12));
        assertThat(req.milestones()).extracting(MilestoneSpan::title, MilestoneSpan::from, MilestoneSpan::to)
                .containsExactly(tuple("Basics", today.plusDays(6), today.plusDays(9)),
                        tuple("Projects", today.plusDays(10), today.plusDays(12)));
        assertThat(req.pending()).contains("Study " + today, "Study " + today.plusDays(5));

        List<Task> added = tasks.findByGoalId(goalId).stream()
                .filter(t -> t.getTitle().startsWith("Week task")).sorted(Task.DAY_ORDER).toList();
        assertThat(added).extracting(Task::getScheduledDate).first().isEqualTo(today.plusDays(7));
        assertThat(added).hasSize(6);
        assertThat(added.getFirst().getSequence()).isEqualTo(lastSequence + Task.SEQUENCE_STEP);
        // Milestones by date: Basics ends at today + 9
        assertThat(jdbc.queryForList("""
                        select distinct m.title from tasks t join milestones m on m.id = t.milestone_id
                        where t.goal_id = ? and t.scheduled_date > ?""", String.class, goalId, today.plusDays(9)))
                .containsExactly("Projects");
        assertThat(revisions.findAll()).anyMatch(r -> r.getSource() == RevisionSource.EXTENDED
                && FakeWeekPlanner.MODEL.equals(r.getAiModel()) && r.getGoal().getId().equals(goalId));

        // A week ahead now, so the next night leaves it alone
        missedDayJob.run();
        assertThat(weekPlanner.requestsFor(title)).hasSize(1);
    }

    @Test
    void nightlyJobSkipsGoalsNotUsedForTwoWeeks() throws Exception {
        createShortPlan(today.plusMonths(2));
        weekPlanner.answerWith(PlanExtensionFlowTest::fullWeek);
        jdbc.update("update goals set created_at = now() - interval '20 days' where id = ?", goalId);

        missedDayJob.run();

        assertThat(weekPlanner.requestsFor(title)).isEmpty();
    }

    @Test
    void planAheadOnRequestStopsAtTheDeadline() throws Exception {
        createShortPlan(today.plusDays(8));
        weekPlanner.answerWith(PlanExtensionFlowTest::fullWeek);

        mvc.perform(post(extend()).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(today.plusDays(6).toString()))
                .andExpect(jsonPath("$.to").value(today.plusDays(8).toString()))
                .andExpect(jsonPath("$.tasks.length()").value(2));

        mvc.perform(post(extend()).header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("deadline")));
    }

    @Test
    void planAheadNeedsAPlanAndAWorkingAi() throws Exception {
        goalId = createGoal(today.plusMonths(2));
        mvc.perform(post(extend()).header("Authorization", bearer(token)))
                .andExpect(status().isConflict());

        planGenerator.willReturn(PlanExtensionFlowTest::shortPlan);
        generatePlan(mvc, token, goalId);
        mvc.perform(post(extend()).header("Authorization", bearer(token)))
                .andExpect(status().isBadGateway());
        assertThat(tasks.findByGoalId(goalId)).hasSize(6);
    }

    /** Six one-hour tasks, today to today + 5; milestone "Basics" ends at today + 9. */
    static GeneratedPlan shortPlan(PlanRequest req) {
        List<PlannedTask> planned = new ArrayList<>();
        for (int d = 0; d < 6; d++) {
            planned.add(new PlannedTask("Study " + req.today().plusDays(d), req.today().plusDays(d), 60));
        }
        LocalDate basicsEnd = req.today().plusDays(9).isBefore(req.deadline()) ? req.today().plusDays(9) : req.deadline();
        return new GeneratedPlan(List.of(new PlannedMilestone("Basics", basicsEnd, planned),
                new PlannedMilestone("Projects", req.deadline(), List.of())));
    }

    private void createShortPlan(LocalDate deadline) throws Exception {
        goalId = createGoal(deadline);
        planGenerator.willReturn(PlanExtensionFlowTest::shortPlan);
        generatePlan(mvc, token, goalId);
    }

    private long createGoal(LocalDate deadline) throws Exception {
        String body = mvc.perform(post("/api/v1/goals").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"deadline\":\"" + deadline
                                + "\",\"minutesPerDay\":60,\"experienceLevel\":\"BEGINNER\",\"bufferDay\":\""
                                + bufferDaySixDaysFrom(ZoneOffset.UTC) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Task lastTask() {
        return tasks.findByGoalId(goalId).stream().max(Task.DAY_ORDER).orElseThrow();
    }

    private String extend() {
        return "/api/v1/goals/" + goalId + "/plan/extend";
    }
}

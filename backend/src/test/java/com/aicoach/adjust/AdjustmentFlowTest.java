package com.aicoach.adjust;

import com.aicoach.TestcontainersConfiguration;
import com.aicoach.goal.GoalRepository;
import com.aicoach.plan.FakePlanGenerator;
import com.aicoach.plan.FakeWeekPlanner;
import com.aicoach.plan.GeneratedPlan;
import com.aicoach.plan.GeneratedPlan.PlannedMilestone;
import com.aicoach.plan.GeneratedPlan.PlannedTask;
import com.aicoach.plan.PlanRequest;
import com.aicoach.plan.PlanRevisionRepository;
import com.aicoach.plan.RevisionSource;
import com.aicoach.task.TaskRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.aicoach.TestApi.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AdjustmentFlowTest.FakeAiConfig.class})
class AdjustmentFlowTest {

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

        @Bean
        @Primary
        FakeScopeCutter fakeScopeCutter() {
            return new FakeScopeCutter();
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    FakePlanGenerator planGenerator;
    @Autowired
    FakeScopeCutter scopeCutter;
    @Autowired
    TaskRepository tasks;
    @Autowired
    GoalRepository goals;
    @Autowired
    PlanRevisionRepository revisions;
    @Autowired
    JdbcTemplate jdbc;

    LocalDate today;
    String token;
    long goalId;

    /**
     * Behind from the start: "Basics" is due in 4 days (240 minutes of time) but has six one-hour tasks, today
     * to today + 5, so 120 minutes are missing.
     */
    static GeneratedPlan behindPlan(PlanRequest req) {
        List<PlannedTask> planned = new ArrayList<>();
        for (int d = 0; d < 6; d++) {
            planned.add(new PlannedTask("Lesson " + d, req.today().plusDays(d), 60));
        }
        return new GeneratedPlan(List.of(new PlannedMilestone("Basics", req.today().plusDays(3), planned),
                new PlannedMilestone("Projects", req.deadline(), List.of())));
    }

    @BeforeEach
    void setUp() throws Exception {
        planGenerator.reset();
        scopeCutter.reset();
        today = LocalDate.now(ZoneOffset.UTC);
        token = register(mvc, "UTC");
        goalId = createGoal(mvc, token, ZoneOffset.UTC);
        planGenerator.willReturn(AdjustmentFlowTest::behindPlan);
        generatePlan(mvc, token, goalId);
    }

    @Test
    void optionsShowWhatEachFixWouldChange() throws Exception {
        LocalDate deadline = goals.findById(goalId).orElseThrow().getDeadline();

        mvc.perform(get(adjustments()).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesBehind").value(120))
                .andExpect(jsonPath("$.healthPercent").value(66))
                .andExpect(jsonPath("$.milestone.title").value("Basics"))
                // 2 days of time, and the last task (today + 5) lands on the new date
                .andExpect(jsonPath("$.extend.days").value(2))
                .andExpect(jsonPath("$.extend.milestoneDate").value(today.plusDays(5).toString()))
                .andExpect(jsonPath("$.extend.deadline").value(deadline.plusDays(2).toString()))
                // Five hour-long tasks in the three days before the milestone: two a day
                .andExpect(jsonPath("$.moreTime.minutesPerDay").value(120))
                .andExpect(jsonPath("$.moreTime.currentMinutesPerDay").value(60))
                .andExpect(jsonPath("$.moreTime.realistic").value(false));
    }

    @Test
    void extendingMovesTheMilestonesAndDeadlineButNoTasks() throws Exception {
        LocalDate deadline = goals.findById(goalId).orElseThrow().getDeadline();
        Map<Long, LocalDate> before = taskDates();

        post(adjustments() + "/extend", "{\"days\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesBehind").value(0))
                .andExpect(jsonPath("$.extend").doesNotExist());

        assertThat(jdbc.queryForList("select target_date from milestones where goal_id = ? order by position",
                LocalDate.class, goalId)).containsExactly(today.plusDays(5), deadline.plusDays(2));
        assertThat(goals.findById(goalId).orElseThrow().getDeadline()).isEqualTo(deadline.plusDays(2));
        assertThat(taskDates()).isEqualTo(before);
        assertThat(revisions.findAll()).anyMatch(r -> r.getSource() == RevisionSource.ADJUSTED
                && r.getSummary().contains("by 2 days"));
    }

    @Test
    void moreDailyTimePullsTasksEarlierFromTomorrow() throws Exception {
        post(adjustments() + "/more-time", "{\"minutesPerDay\":120}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesBehind").value(0));

        Map<LocalDate, Long> perDay = new TreeMap<>();
        tasks.findByGoalId(goalId).forEach(t -> perDay.merge(t.getScheduledDate(), 1L, Long::sum));
        // Today is left alone; the five later lessons now fit in the three days before the milestone
        assertThat(perDay).containsExactly(Map.entry(today, 1L), Map.entry(today.plusDays(1), 2L),
                Map.entry(today.plusDays(2), 2L), Map.entry(today.plusDays(3), 1L));
        assertThat(goals.findById(goalId).orElseThrow().getMinutesPerDay()).isEqualTo(120);
    }

    @Test
    void cutScopeSuggestsThenDropsOnlyWhatTheUserConfirms() throws Exception {
        // Upcoming lessons 1 to 5 are the candidates; the "AI" ranks three, two of which free the 120 minutes
        scopeCutter.answerWith(req -> new ScopeSuggestion(List.of(new ScopeSuggestion.Drop(4, "Optional practice"),
                new ScopeSuggestion.Drop(5, "Covered again later"), new ScopeSuggestion.Drop(1, "Revision only"))));

        String body = post(adjustments() + "/cut-scope/suggest", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesBehind").value(120))
                .andExpect(jsonPath("$.minutesFreed").value(120))
                .andExpect(jsonPath("$.tasks[0].title").value("Lesson 4"))
                .andExpect(jsonPath("$.tasks[0].reason").value("Optional practice"))
                .andExpect(jsonPath("$.tasks.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        CutRequest asked = scopeCutter.requests.getFirst();
        assertThat(asked.minutesToFree()).isEqualTo(120);
        assertThat(asked.candidates()).extracting(CutRequest.Candidate::title)
                .containsExactly("Lesson 1", "Lesson 2", "Lesson 3", "Lesson 4", "Lesson 5");
        assertThat(tasks.findByGoalId(goalId)).hasSize(6);

        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(body, "$.tasks[*].taskId");
        post(adjustments() + "/cut-scope", "{\"taskIds\":" + ids + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesBehind").value(0));

        assertThat(tasks.findByGoalId(goalId)).extracting(t -> t.getTitle())
                .containsExactlyInAnyOrder("Lesson 0", "Lesson 1", "Lesson 2", "Lesson 3");
        assertThat(revisions.findAll()).anyMatch(r -> r.getSource() == RevisionSource.ADJUSTED
                && r.getSummary().equals("Dropped 2 tasks (120 min): Lesson 4; Lesson 5"));
        // On track again, so there is nothing more to cut
        post(adjustments() + "/cut-scope/suggest", "").andExpect(status().isConflict());
    }

    @Test
    void onlyUpcomingPendingTasksCanBeDropped() throws Exception {
        long todaysTask = tasks.findByGoalId(goalId).stream()
                .filter(t -> t.getScheduledDate().equals(today)).findFirst().orElseThrow().getId();

        post(adjustments() + "/cut-scope", "{\"taskIds\":[" + todaysTask + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("can't be dropped")));
        post(adjustments() + "/cut-scope", "{\"taskIds\":[999999]}").andExpect(status().isBadRequest());
        assertThat(tasks.findByGoalId(goalId)).hasSize(6);
    }

    @Test
    void moreTimeMustBeMore() throws Exception {
        post(adjustments() + "/more-time", "{\"minutesPerDay\":60}").andExpect(status().isBadRequest());
        post(adjustments() + "/extend", "{\"days\":0}").andExpect(status().isBadRequest());
    }

    private ResultActions post(String url, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url)
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Map<Long, LocalDate> taskDates() {
        Map<Long, LocalDate> dates = new TreeMap<>();
        tasks.findByGoalId(goalId).forEach(t -> dates.put(t.getId(), t.getScheduledDate()));
        return dates;
    }

    private String adjustments() {
        return "/api/v1/goals/" + goalId + "/adjustments";
    }
}

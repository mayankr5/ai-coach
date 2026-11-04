package com.aicoach.checkin;

import com.aicoach.TestcontainersConfiguration;
import com.aicoach.plan.FakePlanGenerator;
import com.aicoach.plan.FakeWeekPlanner;
import com.aicoach.plan.GeneratedPlan;
import com.aicoach.plan.GeneratedPlan.PlannedMilestone;
import com.aicoach.plan.GeneratedPlan.PlannedTask;
import com.aicoach.plan.PlanRevisionRepository;
import com.aicoach.plan.RevisionSource;
import com.aicoach.replan.CatchUpPlan;
import com.aicoach.replan.FakeCoachWriter;
import com.aicoach.replan.MissedDayJob;
import com.aicoach.task.Task;
import com.aicoach.task.TaskRepository;
import com.aicoach.task.TaskStatus;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static com.aicoach.TestApi.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CheckInFlowTest.FakeAiConfig.class})
class CheckInFlowTest {

    static final String TZ = "Asia/Kolkata";

    @TestConfiguration
    static class FakeAiConfig {
        @Bean
        @Primary
        FakePlanGenerator fakePlanGenerator() {
            return new FakePlanGenerator();
        }

        /** Never asked in these tests; fails like an unavailable AI if the nightly job finds a plan running low. */
        @Bean
        @Primary
        FakeWeekPlanner fakeWeekPlanner() {
            return new FakeWeekPlanner();
        }

        @Bean
        @Primary
        FakeCoachWriter fakeCoachWriter() {
            return new FakeCoachWriter();
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    FakePlanGenerator planGenerator;
    @Autowired
    FakeCoachWriter coachWriter;
    @Autowired
    TaskRepository tasks;
    @Autowired
    PlanRevisionRepository revisions;
    @Autowired
    MissedDayJob missedDayJob;

    String token;
    long goalId;
    LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        planGenerator.reset();
        coachWriter.reset();
        today = LocalDate.now(ZoneId.of(TZ));

        // One 40-minute task per day across the 14-day window, except the buffer days (today + 6 and + 13)
        planGenerator.willReturn(req -> {
            List<PlannedTask> planned = new ArrayList<>();
            for (LocalDate d = req.today(); !d.isAfter(req.taskWindowEnd()); d = d.plusDays(1)) {
                if (req.isBufferDay(d)) continue;
                planned.add(new PlannedTask("Study " + d, d, 40));
            }
            return new GeneratedPlan(List.of(
                    new PlannedMilestone("Basics", req.taskWindowEnd(), planned),
                    new PlannedMilestone("Projects", req.deadline(), List.of())));
        });
        token = register(mvc, TZ);
        goalId = createGoal(mvc, token, ZoneId.of(TZ));
        generatePlan(mvc, token, goalId);
    }

    @Test
    void checkInStoresAiCoachMessage() throws Exception {
        Task todayTask = taskOn(today);
        Task tomorrowTask = taskOn(today.plusDays(1));
        coachWriter.willReturn("Great start! Tomorrow continues with the next topic.");

        mvc.perform(post(checkIns()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(today, todayTask.getId(), "DONE", "Felt easy")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coachMessage").value("Great start! Tomorrow continues with the next topic."))
                .andExpect(jsonPath("$.replanSource").value("AI"));

        assertThat(coachWriter.requests).hasSize(1);
        var request = coachWriter.requests.getFirst();
        assertThat(request.from()).isEqualTo(today.plusDays(1));
        assertThat(request.note()).isEqualTo("Felt easy");
        assertThat(request.completionRate()).isEqualTo(1.0);
        assertThat(request.upcoming()).anyMatch(t -> t.id().equals(tomorrowTask.getId()));
        assertThat(request.upcoming()).noneMatch(t -> t.id().equals(todayTask.getId()));
        assertThat(coachWriter.plans.getFirst().isEmpty()).as("nothing missed, nothing changed").isTrue();
        assertThat(tasks.findById(tomorrowTask.getId()).orElseThrow().getScheduledDate()).isEqualTo(today.plusDays(1));

        mvc.perform(get(checkIns()).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content[0].note").value("Felt easy"))
                .andExpect(jsonPath("$.content[0].coachMessage").value("Great start! Tomorrow continues with the next topic."));
        assertThat(revisions.findAll()).anyMatch(r -> r.getSource() == RevisionSource.AI
                && FakeCoachWriter.MODEL.equals(r.getAiModel()));
    }

    @Test
    void skippedTaskGoesFirstTomorrowAndLaterTasksShift() throws Exception {
        Task todayTask = taskOn(today);
        Task tomorrowTask = taskOn(today.plusDays(1));
        coachWriter.willReturn("Redo it tomorrow; everything else moves back a day.");

        // Every day has 40 of 60 minutes and no extra time, so the 40-minute redo pushes each day back one
        mvc.perform(post(checkIns()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(today, todayTask.getId(), "SKIPPED", "Busy day")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replanSource").value("AI"));

        assertThat(pendingTitled(todayTask.getTitle())).singleElement()
                .satisfies(t -> assertThat(t.getScheduledDate()).isEqualTo(today.plusDays(1)));
        assertThat(tasks.findById(tomorrowTask.getId()).orElseThrow().getScheduledDate()).isEqualTo(today.plusDays(2));
        // The shifting stops at the buffer day: the task from today + 5 lands on it, the next week is untouched
        CatchUpPlan plan = coachWriter.plans.getFirst();
        assertThat(plan.shifted()).isEqualTo(5);
        assertThat(plan.bufferDaysUsed()).containsExactly(today.plusDays(6));
        assertThat(taskOn(today.plusDays(7)).getTitle()).isEqualTo("Study " + today.plusDays(7));
    }

    @Test
    void catchUpTaskIsListedFirstOnItsDay() throws Exception {
        Task todayTask = taskOn(today);
        LocalDate tomorrow = today.plusDays(1);
        String finish = "Finish: " + todayTask.getTitle();
        coachWriter.willReturn("Finish the rest first tomorrow.");

        // Half of 40 minutes left: 20 + tomorrow's 40 fits in 60, so both stay on tomorrow
        mvc.perform(post(checkIns()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(today, todayTask.getId(), "PARTIAL", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingTasks[0].title").value(finish))
                .andExpect(jsonPath("$.upcomingTasks[1].title").value("Study " + tomorrow));

        mvc.perform(get("/api/v1/goals/" + goalId + "/tasks?date=" + tomorrow).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].title").value(finish))
                .andExpect(jsonPath("$[0].sequence").value(todayTask.getSequence() + 1))
                .andExpect(jsonPath("$[1].title").value("Study " + tomorrow));
    }

    @Test
    void lateCheckInForYesterdayLeavesTodayAlone() throws Exception {
        Task todayTask = taskOn(today);
        Task yesterdayTask = taskOn(today.plusDays(1));
        yesterdayTask.setScheduledDate(today.minusDays(1));
        tasks.save(yesterdayTask);
        coachWriter.willReturn("Finish it tomorrow.");

        mvc.perform(post(checkIns()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(today.minusDays(1), yesterdayTask.getId(), "PARTIAL", null)))
                .andExpect(status().isOk());

        assertThat(coachWriter.requests.getFirst().from()).isEqualTo(today.plusDays(1));
        assertThat(tasks.findByGoalIdAndScheduledDateBetween(goalId, today, today)).singleElement()
                .satisfies(t -> assertThat(t.getId()).isEqualTo(todayTask.getId()));
        assertThat(pendingTitled("Finish: " + yesterdayTask.getTitle())).singleElement()
                .satisfies(t -> assertThat(t.getScheduledDate()).isEqualTo(today.plusDays(1)));
    }

    @Test
    void heavyDaysAlreadyPlannedCountTowardsTheExtraTimeLimit() throws Exception {
        // 60 min/day + 20 extra; yesterday and today are both planned at 70 minutes
        mvc.perform(patch("/api/v1/goals/" + goalId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"extraMinutesPerDay\":20}"))
                .andExpect(status().isOk());
        Task todayTask = taskOn(today);
        todayTask.setEstimatedMinutes(70);
        tasks.save(todayTask);
        Task yesterdayTask = taskOn(today.plusDays(5));
        yesterdayTask.setScheduledDate(today.minusDays(1));
        yesterdayTask.setEstimatedMinutes(70);
        tasks.save(yesterdayTask);
        Task tomorrowTask = taskOn(today.plusDays(1));
        coachWriter.willReturn("Take it steady.");

        // 35 left + tomorrow's 40 = 75 would fit with extra time, but that would be a third heavy day in a row
        mvc.perform(post(checkIns()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(today, todayTask.getId(), "PARTIAL", null)))
                .andExpect(status().isOk());

        assertThat(coachWriter.requests.getFirst().recentExtraDays()).containsExactly(today.minusDays(1), today);
        assertThat(coachWriter.plans.getFirst().extraDays()).doesNotContain(today.plusDays(1));
        assertThat(tasks.findById(tomorrowTask.getId()).orElseThrow().getScheduledDate()).isEqualTo(today.plusDays(2));
    }

    @Test
    void taskMovedByHandGoesLastOnItsNewDay() throws Exception {
        LocalDate tomorrow = today.plusDays(1);
        Task later = taskOn(today.plusDays(3));

        mvc.perform(patch("/api/v1/tasks/" + later.getId()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + tomorrow + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/goals/" + goalId + "/tasks?date=" + tomorrow).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].title").value("Study " + tomorrow))
                .andExpect(jsonPath("$[1].title").value(later.getTitle()));
        // Still ahead of the day after in the series, so future re-plans keep it on or before it
        assertThat(tasks.findById(later.getId()).orElseThrow().getSequence())
                .isLessThan(taskOn(today.plusDays(2)).getSequence());
    }

    @Test
    void aiDownStillSavesCheckInWithDefaultMessage() throws Exception {
        Task todayTask = taskOn(today);

        // 20-minute leftover + 40 planned fits in 60, so nothing else moves
        mvc.perform(post(checkIns()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(today, todayTask.getId(), "PARTIAL", "Ran out of time")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replanSource").value("FALLBACK"))
                .andExpect(jsonPath("$.coachMessage").value(startsWith("Added 1 catch-up task starting tomorrow (")))
                .andExpect(jsonPath("$.upcomingTasks[?(@.title == 'Finish: " + todayTask.getTitle() + "')].scheduledDate")
                        .value(today.plusDays(1).toString()));
    }

    @Test
    void resubmittingUpdatesCheckInWithoutCarryingTwice() throws Exception {
        Task todayTask = taskOn(today);
        String skipped = body(today, todayTask.getId(), "SKIPPED", "first");

        mvc.perform(post(checkIns()).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(skipped)).andExpect(status().isOk());
        mvc.perform(post(checkIns()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(skipped.replace("first", "second")))
                .andExpect(status().isOk());

        assertThat(pendingTitled(todayTask.getTitle())).hasSize(1);
        mvc.perform(get(checkIns()).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].note").value("second"));
    }

    @Test
    void rejectsInvalidCheckIns() throws Exception {
        Task todayTask = taskOn(today);
        Task tomorrowTask = taskOn(today.plusDays(1));

        expect400(body(today.minusDays(2), todayTask.getId(), "DONE", null));
        expect400(body(today, tomorrowTask.getId(), "DONE", null));
        expect400(body(today, todayTask.getId(), "PENDING", null));
        expect400(body(today, 999_999L, "DONE", null));

        String stranger = register(mvc, TZ);
        mvc.perform(post(checkIns()).header("Authorization", bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content(body(today, todayTask.getId(), "DONE", null)))
                .andExpect(status().isNotFound());
        assertThat(coachWriter.requests).isEmpty();
    }

    @Test
    void progressReflectsCheckIns() throws Exception {
        mvc.perform(post(checkIns()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(today, taskOn(today).getId(), "DONE", null)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/goals/" + goalId + "/progress").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(12))
                .andExpect(jsonPath("$.doneTasks").value(1))
                .andExpect(jsonPath("$.percentComplete").value(8))
                .andExpect(jsonPath("$.completionRate").value(1.0))
                .andExpect(jsonPath("$.nextMilestone.title").value("Basics"))
                .andExpect(jsonPath("$.healthPercent").value(100))
                .andExpect(jsonPath("$.minutesBehind").value(0))
                .andExpect(jsonPath("$.onTrack").value(true));
    }

    @Test
    void nightlyJobSkipsOverdueTasksAndReplans() {
        Task overdue = taskOn(today);
        overdue.setScheduledDate(today.minusDays(1));
        tasks.save(overdue);

        missedDayJob.run();

        assertThat(tasks.findById(overdue.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.SKIPPED);
        // Re-planned from tomorrow: the user's day may already have started
        assertThat(pendingTitled(overdue.getTitle())).hasSize(1)
                .allMatch(t -> t.getScheduledDate().isAfter(today));
        assertThat(revisions.findAll()).anyMatch(r -> r.getSource() == RevisionSource.NIGHTLY);
        assertThat(coachWriter.requests).as("nightly job never calls the AI").isEmpty();

        // Running again finds nothing new to carry
        missedDayJob.run();
        assertThat(pendingTitled(overdue.getTitle())).hasSize(1);
    }

    @Test
    void nightlyJobLeavesGoalAloneWhenUserCheckedInYesterday() throws Exception {
        Task task = taskOn(today);
        task.setScheduledDate(today.minusDays(1));
        tasks.save(task);
        Task other = taskOn(today.plusDays(1));
        other.setScheduledDate(today.minusDays(1));
        tasks.save(other);

        mvc.perform(post(checkIns()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(today.minusDays(1), task.getId(), "DONE", null)))
                .andExpect(status().isOk());

        missedDayJob.run();

        assertThat(tasks.findById(other.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    private void expect400(String body) throws Exception {
        mvc.perform(post(checkIns()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    private Task taskOn(LocalDate date) {
        return tasks.findByGoalIdAndScheduledDateBetween(goalId, date, date).getFirst();
    }

    private List<Task> pendingTitled(String title) {
        return tasks.findByGoalId(goalId).stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING && t.getTitle().equals(title))
                .toList();
    }

    private String checkIns() {
        return "/api/v1/goals/" + goalId + "/check-ins";
    }

    private static String body(LocalDate date, long taskId, String status, String note) {
        return "{\"date\":\"" + date + "\",\"tasks\":[{\"taskId\":" + taskId + ",\"status\":\"" + status + "\"}]"
                + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}";
    }
}

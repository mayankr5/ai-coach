package com.aicoach.plan;

import com.aicoach.TestcontainersConfiguration;
import com.aicoach.plan.GeneratedPlan.PlannedMilestone;
import com.aicoach.plan.GeneratedPlan.PlannedTask;
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

import com.aicoach.TestApi;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PlanFlowTest.FakeAiConfig.class})
class PlanFlowTest {

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
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    FakePlanGenerator fake;
    @Autowired
    PlanRevisionRepository revisions;

    @BeforeEach
    void resetFake() {
        fake.reset();
    }

    /** Today: "Install JDK"; then one 45-minute task per remaining window day, except buffer days. */
    static final Function<PlanRequest, GeneratedPlan> VALID = req -> {
        List<PlannedTask> tasks = new java.util.ArrayList<>();
        tasks.add(new PlannedTask("Install JDK", req.today(), 30));
        for (LocalDate d = req.today().plusDays(1); !d.isAfter(req.taskWindowEnd()); d = d.plusDays(1)) {
            if (req.isBufferDay(d)) continue;
            tasks.add(new PlannedTask("Practice " + d, d, 45));
        }
        return new GeneratedPlan(List.of(
                new PlannedMilestone("Basics", req.taskWindowEnd(), tasks),
                new PlannedMilestone("Projects", req.deadline(), List.of())));
    };

    static final Function<PlanRequest, GeneratedPlan> OVER_LIMIT = req -> new GeneratedPlan(List.of(
            new PlannedMilestone("Basics", req.taskWindowEnd(), List.of(
                    new PlannedTask("Huge task", req.today(), 500)))));

    @Test
    void generatesPlanAndTodaysTasksAppear() throws Exception {
        fake.willReturn(VALID);
        String token = register();
        long goalId = createGoal(token);

        mvc.perform(post(plan(goalId)).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.milestones.length()").value(2))
                // 14 window days minus the two buffer days
                .andExpect(jsonPath("$.milestones[0].tasks.length()").value(12))
                .andExpect(jsonPath("$.milestones[1].tasks").isEmpty());

        mvc.perform(get("/api/v1/goals/" + goalId + "/tasks").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Install JDK"));

        mvc.perform(get("/api/v1/goals/" + goalId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.milestones[0].title").value("Basics"));
        assertThat(revisions.findAll()).anyMatch(r -> FakePlanGenerator.MODEL.equals(r.getAiModel()));
    }

    @Test
    void seriesFollowsDatesThenTheAiListOrder() throws Exception {
        // The AI listed a second task for today after all the later days
        fake.willReturn(req -> {
            GeneratedPlan plan = VALID.apply(req);
            List<PlannedTask> tasks = new java.util.ArrayList<>(plan.milestones().getFirst().tasks());
            tasks.add(new PlannedTask("Read the docs", req.today(), 15));
            return new GeneratedPlan(List.of(new PlannedMilestone("Basics", req.taskWindowEnd(), tasks),
                    plan.milestones().getLast()));
        });
        String token = register();
        long goalId = createGoal(token);
        mvc.perform(post(plan(goalId)).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.milestones[0].tasks[1].title").value("Read the docs"))
                .andExpect(jsonPath("$.milestones[0].tasks[2].title").value(startsWith("Practice")));

        mvc.perform(get("/api/v1/goals/" + goalId + "/tasks").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].title").value("Install JDK"))
                .andExpect(jsonPath("$[0].sequence").value(1000))
                .andExpect(jsonPath("$[1].title").value("Read the docs"))
                .andExpect(jsonPath("$[1].sequence").value(2000));
    }

    @Test
    void invalidPlanIs502AndSavesNothing() throws Exception {
        // Retries happen inside the AI chain (AiPlanGeneratorTest); this is PlanService's final guard
        fake.willReturn(OVER_LIMIT);
        String token = register();
        long goalId = createGoal(token);

        mvc.perform(post(plan(goalId)).header("Authorization", bearer(token)))
                .andExpect(status().isBadGateway());

        mvc.perform(get("/api/v1/goals/" + goalId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.milestones").isEmpty());
    }

    @Test
    void regenerateReplacesPlanUntilProgressExists() throws Exception {
        fake.willReturn(VALID);
        fake.willReturn(VALID);
        String token = register();
        long goalId = createGoal(token);

        mvc.perform(post(plan(goalId)).header("Authorization", bearer(token))).andExpect(status().isOk());
        String body = mvc.perform(post(plan(goalId)).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.milestones.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        Integer taskId = JsonPath.read(body, "$.milestones[0].tasks[0].id");
        mvc.perform(patch("/api/v1/tasks/" + taskId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk());

        mvc.perform(post(plan(goalId)).header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void otherUsersGoalIsNotFound() throws Exception {
        long goalId = createGoal(register());

        mvc.perform(post(plan(goalId)).header("Authorization", bearer(register())))
                .andExpect(status().isNotFound());
        assertThat(fake.requests).isEmpty();
    }

    private String register() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + UUID.randomUUID() + "@example.com\",\"password\":\"password123\",\"displayName\":\"T\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private long createGoal(String token) throws Exception {
        String body = mvc.perform(post("/api/v1/goals").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Learn Java\",\"deadline\":\"" + LocalDate.now().plusMonths(2)
                                + "\",\"minutesPerDay\":60,\"experienceLevel\":\"BEGINNER\",\"bufferDay\":\""
                                + TestApi.bufferDaySixDaysFrom(ZoneOffset.UTC) + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String plan(long goalId) {
        return "/api/v1/goals/" + goalId + "/plan";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}

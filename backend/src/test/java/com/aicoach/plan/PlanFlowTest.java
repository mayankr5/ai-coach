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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
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
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    FakePlanGenerator fake;

    @BeforeEach
    void resetFake() {
        fake.reset();
    }

    /** Today: "Install JDK"; then one 45-minute task per remaining window day. */
    static final Function<PlanRequest, GeneratedPlan> VALID = req -> {
        List<PlannedTask> tasks = new java.util.ArrayList<>();
        tasks.add(new PlannedTask("Install JDK", req.today(), 30));
        for (LocalDate d = req.today().plusDays(1); !d.isAfter(req.taskWindowEnd()); d = d.plusDays(1)) {
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
                .andExpect(jsonPath("$.milestones[0].tasks.length()").value(14))
                .andExpect(jsonPath("$.milestones[1].tasks").isEmpty());

        mvc.perform(get("/api/v1/goals/" + goalId + "/tasks").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Install JDK"));

        mvc.perform(get("/api/v1/goals/" + goalId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.milestones[0].title").value("Basics"));
    }

    @Test
    void retriesWithErrorsThenSucceeds() throws Exception {
        fake.willReturn(OVER_LIMIT);
        fake.willReturn(VALID);
        String token = register();
        long goalId = createGoal(token);

        mvc.perform(post(plan(goalId)).header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        assertThat(fake.receivedErrors).hasSize(2);
        assertThat(fake.receivedErrors.get(0)).isEmpty();
        assertThat(fake.receivedErrors.get(1)).anyMatch(e -> e.contains("500 minutes"));
    }

    @Test
    void invalidTwiceIs502AndSavesNothing() throws Exception {
        fake.willReturn(OVER_LIMIT);
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
        assertThat(fake.receivedErrors).isEmpty();
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
                                + "\",\"minutesPerDay\":60,\"experienceLevel\":\"BEGINNER\"}"))
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

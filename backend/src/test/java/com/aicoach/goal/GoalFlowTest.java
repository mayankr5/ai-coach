package com.aicoach.goal;

import com.aicoach.TestcontainersConfiguration;
import com.aicoach.task.Task;
import com.aicoach.task.TaskRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GoalFlowTest {

    private static final String DEADLINE = LocalDate.now().plusMonths(2).toString();

    @Autowired
    MockMvc mvc;
    @Autowired
    GoalRepository goals;
    @Autowired
    TaskRepository tasks;

    @Test
    void createGetListUpdateDelete() throws Exception {
        String token = registerUser("Asia/Kolkata");
        long goalId = createGoal(token, "Learn Spring Boot");

        mvc.perform(get("/api/v1/goals/" + goalId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Learn Spring Boot"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.milestones").isEmpty());

        mvc.perform(get("/api/v1/goals").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(goalId));

        mvc.perform(patch("/api/v1/goals/" + goalId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.title").value("Learn Spring Boot"));

        mvc.perform(get("/api/v1/goals?status=ACTIVE").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(delete("/api/v1/goals/" + goalId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/goals/" + goalId).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherUsersGoalIsNotFound() throws Exception {
        String owner = registerUser(null);
        String stranger = registerUser(null);
        long goalId = createGoal(owner, "Private goal");

        mvc.perform(get("/api/v1/goals/" + goalId).header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/goals/" + goalId).header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidGoalIs400() throws Exception {
        String token = registerUser(null);
        mvc.perform(post("/api/v1/goals").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","deadline":"2000-01-01","minutesPerDay":5,"experienceLevel":"BEGINNER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.deadline").exists())
                .andExpect(jsonPath("$.errors.minutesPerDay").exists());
    }

    @Test
    void extraMinutesDefaultToZeroAndAreCappedAtHalf() throws Exception {
        String token = registerUser(null);
        long goalId = createGoal(token, "Learn Docker");
        mvc.perform(get("/api/v1/goals/" + goalId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.extraMinutesPerDay").value(0));

        mvc.perform(patch("/api/v1/goals/" + goalId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"extraMinutesPerDay\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraMinutesPerDay").value(30));
        mvc.perform(patch("/api/v1/goals/" + goalId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"extraMinutesPerDay\":31}"))
                .andExpect(status().isBadRequest());
        // Lowering daily time below twice the extra time is rejected too
        mvc.perform(patch("/api/v1/goals/" + goalId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minutesPerDay\":40}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownTimezoneIs400() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"tz@example.com","password":"password123","displayName":"Tz","timezone":"Mars/Base"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void todaysTasksUseUserTimezoneAndCanBeUpdated() throws Exception {
        String token = registerUser("Asia/Kolkata");
        long goalId = createGoal(token, "Read 5 books");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        long todayTask = saveTask(goalId, "Read chapter 1", today);
        saveTask(goalId, "Read chapter 2", today.plusDays(1));

        mvc.perform(get("/api/v1/goals/" + goalId + "/tasks").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Read chapter 1"));

        mvc.perform(get("/api/v1/goals/" + goalId + "/tasks?date=" + today.plusDays(1))
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].title").value("Read chapter 2"));

        mvc.perform(patch("/api/v1/tasks/" + todayTask).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        String stranger = registerUser(null);
        mvc.perform(patch("/api/v1/tasks/" + todayTask).header("Authorization", bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SKIPPED\"}"))
                .andExpect(status().isNotFound());
    }

    private String registerUser(String timezone) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String tz = timezone == null ? "" : ",\"timezone\":\"" + timezone + "\"";
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"displayName\":\"T\"" + tz + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private long createGoal(String token, String title) throws Exception {
        String body = mvc.perform(post("/api/v1/goals").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"deadline\":\"" + DEADLINE
                                + "\",\"minutesPerDay\":60,\"experienceLevel\":\"BEGINNER\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Tasks are created by the AI planner later; until then tests insert them directly. */
    private long saveTask(long goalId, String title, LocalDate date) {
        Task task = new Task();
        task.setGoal(goals.getReferenceById(goalId));
        task.setTitle(title);
        task.setScheduledDate(date);
        task.setEstimatedMinutes(30);
        return tasks.save(task).getId();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}

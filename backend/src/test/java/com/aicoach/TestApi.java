package com.aicoach;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shortcuts for integration tests that need a user, a goal or a plan as setup. */
public final class TestApi {

    private TestApi() {
    }

    public static String register(MockMvc mvc, String timezone) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + UUID.randomUUID() + "@example.com\",\"password\":\"password123\","
                                + "\"displayName\":\"T\",\"timezone\":\"" + timezone + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    /**
     * A buffer weekday six days after today, so tests can rely on today to today + 5 being working days;
     * the buffer days then fall on today + 6 and today + 13.
     */
    public static DayOfWeek bufferDaySixDaysFrom(ZoneId zone) {
        return LocalDate.now(zone).plusDays(6).getDayOfWeek();
    }

    /** A goal with 60 minutes a day whose buffer days are today + 6 and today + 13 in {@code zone}. */
    public static long createGoal(MockMvc mvc, String token, ZoneId zone) throws Exception {
        String body = mvc.perform(post("/api/v1/goals").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Learn Java\",\"deadline\":\"" + LocalDate.now().plusMonths(2)
                                + "\",\"minutesPerDay\":60,\"experienceLevel\":\"BEGINNER\",\"bufferDay\":\""
                                + bufferDaySixDaysFrom(zone) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    public static void generatePlan(MockMvc mvc, String token, long goalId) throws Exception {
        mvc.perform(post("/api/v1/goals/" + goalId + "/plan").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}

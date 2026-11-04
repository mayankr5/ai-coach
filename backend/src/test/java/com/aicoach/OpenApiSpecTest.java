package com.aicoach;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The web app's types are generated from this spec, so it must say exactly what can be missing or null. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiSpecTest {

    @Autowired
    MockMvc mvc;

    @Test
    void responseFieldsAreRequiredAndOnlyMarkedOnesNullable() throws Exception {
        String progress = "$.components.schemas.ProgressResponse";
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(progress + ".required", hasItems("healthPercent", "completionRate", "nextMilestone")))
                .andExpect(jsonPath(progress + ".properties.healthPercent.type").value("integer"))
                .andExpect(jsonPath(progress + ".properties.completionRate.type", hasItems("number", "null")))
                .andExpect(jsonPath(progress + ".properties.nextMilestone.oneOf[1].type").value("null"))
                .andExpect(jsonPath("$.components.schemas.UpdateGoalRequest.required").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
    }
}

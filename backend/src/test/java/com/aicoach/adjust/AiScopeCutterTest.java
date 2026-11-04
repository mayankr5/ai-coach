package com.aicoach.adjust;

import com.aicoach.adjust.CutRequest.Candidate;
import com.aicoach.adjust.ScopeSuggestion.Drop;
import com.aicoach.ai.AiChain;
import com.aicoach.ai.AiProperties;
import com.aicoach.ai.AiResult;
import com.aicoach.ai.ScriptedChatModel;
import com.aicoach.goal.ExperienceLevel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiScopeCutterTest {

    static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    /** 90 minutes must go; tasks of 60, 30, 45 and 20 minutes. */
    static final CutRequest REQUEST = new CutRequest("Learn Java", null, ExperienceLevel.BEGINNER, "Basics",
            DAY.plusDays(4), 90, List.of(
            new Candidate(11, "Generics deep dive", 60, DAY, "Basics"),
            new Candidate(12, "Extra loop exercises", 30, DAY, "Basics"),
            new Candidate(13, "Collections", 45, DAY.plusDays(1), "Basics"),
            new Candidate(14, "Read about JVM history", 20, DAY.plusDays(2), "Basics")),
            List.of("Install JDK"));

    final ScriptedChatModel model = new ScriptedChatModel();
    final AiScopeCutter cutter = new AiScopeCutter(new AiChain(List.of(model.named("test/model"))),
            new AiProperties(List.of(), Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofSeconds(5),
                    Duration.ofSeconds(10), 0.3, null, null, null, null));

    @Test
    void enoughTimeInAnyRankingPasses() {
        assertThat(problems(drop(1), drop(2))).isEmpty();
        // More than needed is fine: code only suggests as many as it takes
        assertThat(problems(drop(4), drop(2), drop(1), drop(3))).isEmpty();
    }

    @Test
    void tooLittleIsExplained() {
        assertThat(problems(drop(1))).containsExactly("Your picks free 60 minutes; they must free at least 90");
    }

    @Test
    void picksMustBeRealDistinctAndExplained() {
        assertThat(problems(drop(7))).containsExactly("Task 7 is not in the list; use numbers 1 to 4");
        assertThat(problems(drop(1), drop(1))).contains("Task 1 is picked twice");
        assertThat(problems(new Drop(1, " "), drop(2))).singleElement().asString().contains("needs a one-sentence reason");
    }

    @Test
    void promptNumbersTheTasksAndTheAnswerIsRetriedWithFeedback() {
        model.answers("{\"drops\":[{\"number\":4,\"reason\":\"Background only\"}]}")
                .answers("{\"drops\":[{\"number\":1,\"reason\":\"Advanced for now\"},"
                        + "{\"number\":2,\"reason\":\"More of the same practice\"}]}");

        AiResult<ScopeSuggestion> result = cutter.suggest(REQUEST);

        assertThat(model.prompts.getFirst()).contains("1. Generics deep dive (60 min, Basics)")
                .contains("4. Read about JVM history (20 min, Basics)").contains("add up to at least 90");
        assertThat(model.prompts.get(1)).contains("Your picks free 20 minutes; they must free at least 90");
        assertThat(result.value().drops()).extracting(Drop::number).containsExactly(1, 2);
    }

    private static Drop drop(int number) {
        return new Drop(number, "Not essential yet");
    }

    private static List<String> problems(Drop... drops) {
        return AiScopeCutter.problems(new ScopeSuggestion(List.of(drops)), REQUEST);
    }
}

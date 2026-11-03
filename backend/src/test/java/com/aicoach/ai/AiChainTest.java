package com.aicoach.ai;

import com.aicoach.plan.AiUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiChainTest {

    final ScriptedChatModel first = new ScriptedChatModel();
    final ScriptedChatModel second = new ScriptedChatModel();
    final List<List<String>> feedbackSeen = new ArrayList<>();

    @Test
    void firstModelAnswers() {
        first.answers("hello");

        AiResult<String> result = chain().call(task(Duration.ofSeconds(5), Duration.ofSeconds(10)));

        assertThat(result).isEqualTo(new AiResult<>("hello", "first/model"));
        assertThat(second.prompts).isEmpty();
    }

    @Test
    void temporaryErrorRetriesSameModelOnce() {
        first.fails(new TransientAiException("503 overloaded")).answers("hello");

        AiResult<String> result = chain().call(task(Duration.ofSeconds(5), Duration.ofSeconds(10)));

        assertThat(result.model()).isEqualTo("first/model");
        assertThat(first.prompts).hasSize(2);
    }

    @Test
    void rateLimitMovesStraightToNextModel() {
        first.fails(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                HttpHeaders.EMPTY, new byte[0], null));
        second.answers("hello");

        AiResult<String> result = chain().call(task(Duration.ofSeconds(5), Duration.ofSeconds(10)));

        assertThat(result.model()).isEqualTo("second/model");
        assertThat(first.prompts).hasSize(1);
    }

    @Test
    void unreachableProviderMovesStraightToNextModel() {
        first.fails(new ResourceAccessException("I/O error", new ConnectException("Connection refused")));
        second.answers("hello");

        assertThat(chain().call(task(Duration.ofSeconds(5), Duration.ofSeconds(10))).model()).isEqualTo("second/model");
        assertThat(first.prompts).hasSize(1);
    }

    @Test
    void hungModelTimesOutAndNextModelAnswers() {
        first.hangs();
        second.answers("hello");
        long start = System.nanoTime();

        AiResult<String> result = chain().call(task(Duration.ofMillis(300), Duration.ofSeconds(10)));

        assertThat(result.model()).isEqualTo("second/model");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void rejectedAnswerProblemsArePassedToTheNextModel() {
        first.answers("bad").answers("bad");
        second.answers("hello");

        AiResult<String> result = chain().call(task(Duration.ofSeconds(5), Duration.ofSeconds(10)));

        assertThat(result.model()).isEqualTo("second/model");
        assertThat(feedbackSeen).containsExactly(List.of(), List.of("'bad' is not allowed"),
                List.of("'bad' is not allowed"));
    }

    @Test
    void stopsWhenTimeBudgetIsUsedUp() {
        first.hangs();
        second.answers("hello");

        // After a 1s timeout only 2.5s are left, less than the 3s minimum for another attempt
        assertThatThrownBy(() -> chain().call(task(Duration.ofSeconds(1), Duration.ofMillis(3500))))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("time budget");
        assertThat(second.prompts).isEmpty();
    }

    @Test
    void allModelsFailingListsEveryReason() {
        first.fails(new ResourceAccessException("I/O error", new ConnectException("Connection refused")));
        second.fails(new IllegalArgumentException("model not found"));

        assertThatThrownBy(() -> chain().call(task(Duration.ofSeconds(5), Duration.ofSeconds(10))))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("first/model PERMANENT: provider unreachable")
                .hasMessageContaining("second/model PERMANENT: model not found");
    }

    private AiChain chain() {
        return new AiChain(List.of(first.named("first/model"), second.named("second/model")), Duration.ZERO);
    }

    private AiTask<String> task(Duration timeout, Duration budget) {
        return new AiTask<>("say hello", timeout, budget,
                (client, feedback) -> {
                    feedbackSeen.add(feedback);
                    return client.prompt().user("Say hello").call().content();
                },
                answer -> answer.equals("bad") ? List.of("'bad' is not allowed") : List.of());
    }
}

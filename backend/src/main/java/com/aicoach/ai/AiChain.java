package com.aicoach.ai;

import com.aicoach.plan.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Asks models in order until one gives a usable answer.
 * <ul>
 *   <li>Each attempt has a timeout, and all attempts together share a time budget.</li>
 *   <li>A model gets one more try after a temporary error or a bad answer; timeouts, quota limits and
 *       permanent errors move straight to the next model (see {@link AiFailure.Kind}).</li>
 *   <li>Problems with a bad answer are passed on, also to the next model, so it doesn't repeat them.</li>
 * </ul>
 * Prompts are rebuilt from the database for every call, so any model in the chain gets the full context.
 */
public class AiChain {

    private static final Logger log = LoggerFactory.getLogger(AiChain.class);
    static final int ATTEMPTS_PER_MODEL = 2;
    /** Not worth starting an attempt with less time than this left. */
    static final Duration MIN_ATTEMPT_TIME = Duration.ofSeconds(3);

    private final List<AiModel> models;
    private final Duration temporaryPause;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AiChain(List<AiModel> models) {
        this(models, Duration.ofSeconds(1));
    }

    AiChain(List<AiModel> models, Duration temporaryPause) {
        this.models = List.copyOf(models);
        this.temporaryPause = temporaryPause;
    }

    public List<String> modelNames() {
        return models.stream().map(AiModel::name).toList();
    }

    public <T> AiResult<T> call(AiTask<T> task) {
        long deadline = System.nanoTime() + task.budget().toNanos();
        List<String> feedback = List.of();
        List<String> failures = new ArrayList<>();

        for (AiModel model : models) {
            for (int attempt = 1; attempt <= ATTEMPTS_PER_MODEL; attempt++) {
                Duration remaining = Duration.ofNanos(deadline - System.nanoTime());
                if (remaining.compareTo(MIN_ATTEMPT_TIME) < 0) {
                    failures.add("time budget of " + task.budget().toSeconds() + "s used up");
                    throw unavailable(task, failures);
                }
                Duration timeout = task.timeout().compareTo(remaining) < 0 ? task.timeout() : remaining;

                AiFailure failure;
                try {
                    T value = attempt(model, task, feedback, timeout);
                    List<String> problems = value == null ? List.of("The answer was empty") : task.validate().apply(value);
                    if (problems.isEmpty()) {
                        if (!failures.isEmpty()) {
                            log.info("{}: {} answered after earlier failures {}", task.purpose(), model.name(), failures);
                        }
                        return new AiResult<>(value, model.name());
                    }
                    feedback = problems;
                    failure = new AiFailure(AiFailure.Kind.BAD_ANSWER, "rejected: " + String.join("; ", problems));
                } catch (TimeoutException e) {
                    failure = new AiFailure(AiFailure.Kind.TIMEOUT, "no answer within " + timeout.toSeconds() + "s");
                } catch (ExecutionException e) {
                    failure = AiFailure.classify(e.getCause());
                    if (failure.kind() == AiFailure.Kind.BAD_ANSWER) {
                        feedback = List.of("Your previous answer could not be read (" + failure.reason()
                                + "). Return valid JSON with real calendar dates.");
                    }
                }

                log.warn("{}: {} attempt {} failed ({}): {}", task.purpose(), model.name(), attempt,
                        failure.kind(), failure.reason());
                failures.add(model.name() + " " + failure.kind() + ": " + failure.reason());
                if (!failure.kind().retrySameModel) {
                    break;
                }
                if (failure.kind() == AiFailure.Kind.TEMPORARY) {
                    pause();
                }
            }
        }
        throw unavailable(task, failures);
    }

    private <T> T attempt(AiModel model, AiTask<T> task, List<String> feedback, Duration timeout)
            throws TimeoutException, ExecutionException {
        Future<T> future = executor.submit(() -> task.call().apply(model.client(), feedback));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Interrupting the virtual thread closes its connection, so a hung provider doesn't keep it forever
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Interrupted while waiting for " + model.name(), e);
        }
    }

    private void pause() {
        try {
            Thread.sleep(temporaryPause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static AiUnavailableException unavailable(AiTask<?> task, List<String> failures) {
        return new AiUnavailableException("No AI model could " + task.purpose() + ": "
                + (failures.isEmpty() ? "no models configured" : String.join(" | ", failures)));
    }
}

package com.aicoach.ai;

import org.springframework.ai.chat.client.ChatClient;

import java.time.Duration;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * One job for the chain.
 *
 * @param call     asks a model; gets the problems with earlier answers (empty on the first try) so it can fix them
 * @param validate problems with an answer; empty means usable
 * @param timeout  longest wait for one answer
 * @param budget   longest wait for all attempts together
 */
public record AiTask<T>(String purpose, Duration timeout, Duration budget,
                        BiFunction<ChatClient, List<String>, T> call, Function<T, List<String>> validate) {
}

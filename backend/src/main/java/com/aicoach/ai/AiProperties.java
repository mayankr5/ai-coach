package com.aicoach.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * @param chain models tried in order, each "provider/model", e.g. "ollama/qwen2.5:7b"
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(List<String> chain, Duration planTimeout, Duration planBudget, Duration messageTimeout,
                           Duration messageBudget, double temperature, String ollamaBaseUrl, String geminiApiKey,
                           String anthropicApiKey, String openaiApiKey) {
}

package com.aicoach.ai;

import com.anthropic.models.messages.Model;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds one ChatModel per entry of app.ai.chain. Provider SDK retries are turned off: {@link AiChain}
 * decides when to retry, so failures don't get retried twice with long hidden waits.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);
    private static final RetryTemplate NO_RETRY = new RetryTemplate(RetryPolicy.withMaxRetries(0));
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    AiChain aiChain(AiProperties props) {
        // Safety net below the chain's own timeouts, so a hung connection is eventually closed
        Duration httpTimeout = max(props.planTimeout(), props.messageTimeout()).plusSeconds(10);
        List<AiModel> models = new ArrayList<>();
        for (String entry : props.chain() == null ? List.<String>of() : props.chain()) {
            String[] parts = entry.strip().split("/", 2);
            if (parts.length != 2 || parts[1].isBlank()) {
                throw new IllegalStateException("app.ai.chain entry '" + entry + "' must look like provider/model");
            }
            ChatModel model = create(parts[0], parts[1], props, httpTimeout);
            if (model != null) {
                models.add(new AiModel(entry.strip(), ChatClient.builder(model)
                        // Logs prompts and responses at DEBUG: logging.level.org.springframework.ai.chat.client.advisor=DEBUG
                        .defaultAdvisors(new SimpleLoggerAdvisor())
                        .build()));
            }
        }
        if (models.isEmpty()) {
            log.warn("No AI models available; plan generation will fail and check-ins use fixed messages");
        } else {
            log.info("AI chain: {}", models.stream().map(AiModel::name).toList());
        }
        return new AiChain(models);
    }

    private static ChatModel create(String provider, String model, AiProperties props, Duration httpTimeout) {
        return switch (provider) {
            case "ollama" -> {
                HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
                JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
                requests.setReadTimeout(httpTimeout);
                yield OllamaChatModel.builder()
                        .ollamaApi(OllamaApi.builder().baseUrl(props.ollamaBaseUrl())
                                .restClientBuilder(RestClient.builder().requestFactory(requests)).build())
                        .options(OllamaChatOptions.builder().model(model).temperature(props.temperature()).build())
                        .retryTemplate(NO_RETRY)
                        .build();
            }
            case "google-genai" -> {
                if (missingKey(props.geminiApiKey(), provider, "GEMINI_API_KEY")) yield null;
                Client client = Client.builder().apiKey(props.geminiApiKey())
                        .httpOptions(HttpOptions.builder().timeout((int) httpTimeout.toMillis())
                                .retryOptions(HttpRetryOptions.builder().attempts(1)).build())
                        .build();
                yield GoogleGenAiChatModel.builder().genAiClient(client)
                        .options(GoogleGenAiChatOptions.builder().model(model).temperature(props.temperature()).build())
                        .retryTemplate(NO_RETRY)
                        .build();
            }
            case "anthropic" -> {
                if (missingKey(props.anthropicApiKey(), provider, "ANTHROPIC_API_KEY")) yield null;
                yield AnthropicChatModel.builder()
                        .options(AnthropicChatOptions.builder().apiKey(props.anthropicApiKey()).model(Model.of(model))
                                .temperature(props.temperature()).timeout(httpTimeout).maxRetries(0).build())
                        .build();
            }
            case "openai" -> {
                if (missingKey(props.openaiApiKey(), provider, "OPENAI_API_KEY")) yield null;
                yield OpenAiChatModel.builder()
                        .options(OpenAiChatOptions.builder().apiKey(props.openaiApiKey()).model(model)
                                .temperature(props.temperature()).timeout(httpTimeout).maxRetries(0).build())
                        .build();
            }
            default -> throw new IllegalStateException("Unknown AI provider '" + provider
                    + "'; use google-genai, ollama, anthropic or openai");
        };
    }

    private static boolean missingKey(String key, String provider, String variable) {
        if (key == null || key.isBlank()) {
            log.warn("Skipping {} in the AI chain: {} is not set", provider, variable);
            return true;
        }
        return false;
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}

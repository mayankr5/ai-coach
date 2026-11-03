package com.aicoach.ai;

import org.springframework.ai.chat.client.ChatClient;

/** One entry of the chain; name is "provider/model" for logs and plan history. */
public record AiModel(String name, ChatClient client) {
}

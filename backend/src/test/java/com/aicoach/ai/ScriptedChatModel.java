package com.aicoach.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** A fake provider: each call returns the next scripted answer or throws the next scripted error. */
public class ScriptedChatModel implements ChatModel {

    private final List<Supplier<String>> script = new ArrayList<>();
    public final List<String> prompts = new ArrayList<>();

    public ScriptedChatModel answers(String text) {
        script.add(() -> text);
        return this;
    }

    public ScriptedChatModel fails(RuntimeException error) {
        script.add(() -> {
            throw error;
        });
        return this;
    }

    public ScriptedChatModel hangs() {
        script.add(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "too late";
        });
        return this;
    }

    public AiModel named(String name) {
        return new AiModel(name, org.springframework.ai.chat.client.ChatClient.builder(this).build());
    }

    @Override
    public synchronized ChatResponse call(Prompt prompt) {
        prompts.add(prompt.getContents());
        if (script.isEmpty()) {
            throw new IllegalStateException("Unexpected call");
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(script.removeFirst().get()))));
    }
}

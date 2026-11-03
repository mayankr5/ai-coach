package com.aicoach.ai;

/** A usable answer and the model ("provider/model") that gave it. */
public record AiResult<T>(T value, String model) {
}

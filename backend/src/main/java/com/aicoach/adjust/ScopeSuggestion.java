package com.aicoach.adjust;

import java.util.List;

/** The AI's answer: candidate numbers (from 1) to drop, with a reason each. Field names double as the JSON schema. */
public record ScopeSuggestion(List<Drop> drops) {

    public record Drop(int number, String reason) {
    }
}

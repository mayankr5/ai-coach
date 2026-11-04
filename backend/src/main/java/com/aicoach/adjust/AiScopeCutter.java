package com.aicoach.adjust;

import com.aicoach.adjust.CutRequest.Candidate;
import com.aicoach.adjust.ScopeSuggestion.Drop;
import com.aicoach.ai.AiChain;
import com.aicoach.ai.AiProperties;
import com.aicoach.ai.AiResult;
import com.aicoach.ai.AiTask;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Asks the AI chain to rank upcoming tasks from least to most important, with reasons. The AI only judges
 * importance: code takes picks in that order until enough time is freed, and the user decides.
 */
@Component
public class AiScopeCutter implements ScopeCutter {

    static final int MAX_REASON = 200;

    private static final String SYSTEM_PROMPT = """
            You are a realistic coach helping a user who has fallen behind. List the upcoming tasks that could be
            dropped with the least harm to the goal, least important first. Prefer optional extras, repetition
            and nice-to-haves; keep foundations and anything later tasks depend on.
            Give each one a short, specific reason (one sentence) the user will read.
            """;

    private final AiChain chain;
    private final AiProperties props;

    public AiScopeCutter(AiChain chain, AiProperties props) {
        this.chain = chain;
        this.props = props;
    }

    @Override
    public AiResult<ScopeSuggestion> suggest(CutRequest req) {
        return chain.call(new AiTask<>("suggest work to drop", props.planTimeout(), props.planBudget(),
                (client, problems) -> client.prompt().system(SYSTEM_PROMPT).user(userPrompt(req, problems))
                        .call().entity(ScopeSuggestion.class),
                suggestion -> problems(suggestion, req)));
    }

    static String userPrompt(CutRequest req, List<String> previousProblems) {
        StringBuilder sb = new StringBuilder()
                .append("Goal: ").append(req.goalTitle()).append('\n')
                .append("Details: ").append(req.goalDescription() == null ? "none" : req.goalDescription()).append('\n')
                .append("Experience level: ").append(req.experienceLevel()).append('\n')
                .append("Milestone at risk: ").append(req.milestoneTitle()).append(" (due ").append(req.milestoneDate())
                .append(")\n\nAlready done, oldest first:\n");
        if (req.done().isEmpty()) {
            sb.append("- none\n");
        }
        req.done().forEach(t -> sb.append("- ").append(t).append('\n'));
        sb.append("\nUpcoming tasks:\n");
        for (int i = 0; i < req.candidates().size(); i++) {
            Candidate c = req.candidates().get(i);
            sb.append(i + 1).append(". ").append(c.title()).append(" (").append(c.minutes()).append(" min, ")
                    .append(c.milestoneTitle()).append(")\n");
        }
        sb.append("\nList, least important first, tasks whose minutes add up to at least ").append(req.minutesToFree())
                .append(". Listing a few more is fine; only as many as needed will be suggested.")
                .append(" Answer with their numbers from the list above.\n");
        if (!previousProblems.isEmpty()) {
            sb.append("\nYour previous answer was rejected. Fix these problems:\n");
            previousProblems.forEach(p -> sb.append("- ").append(p).append('\n'));
        }
        return sb.toString();
    }

    /** Real, distinct picks with reasons, that together free enough time. */
    static List<String> problems(ScopeSuggestion suggestion, CutRequest req) {
        if (suggestion == null || suggestion.drops() == null || suggestion.drops().isEmpty()) {
            return List.of("Pick at least one task");
        }
        List<String> problems = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        int freed = 0;
        for (Drop d : suggestion.drops()) {
            if (d == null) continue;
            if (d.number() < 1 || d.number() > req.candidates().size()) {
                problems.add("Task " + d.number() + " is not in the list; use numbers 1 to " + req.candidates().size());
                continue;
            }
            if (!seen.add(d.number())) {
                problems.add("Task " + d.number() + " is picked twice");
                continue;
            }
            if (d.reason() == null || d.reason().isBlank() || d.reason().length() > MAX_REASON) {
                problems.add("Task " + d.number() + " needs a one-sentence reason under " + MAX_REASON + " characters");
            }
            freed += req.candidates().get(d.number() - 1).minutes();
        }
        if (!problems.isEmpty()) {
            return problems;
        }
        if (freed < req.minutesToFree()) {
            problems.add("Your picks free " + freed + " minutes; they must free at least " + req.minutesToFree());
        }
        return problems;
    }
}

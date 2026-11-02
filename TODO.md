# TODO

## Plan quality (local models)
llama3 fails the stricter plan rules (window utilization, deadline coverage) and returns 502.
- [ ] Auto-repair AI plans before validating: drop tasks scheduled outside the task window
- [ ] Try a stronger local model for development: `ollama pull qwen2.5:7b`, then `OLLAMA_MODEL=qwen2.5:7b`
- [ ] Re-test real plan generation once both are done

## Re-planning
- [ ] The rule-based fallback and nightly job only re-schedule missed work; they don't fill new days as the
      14-day window moves. Only AI re-plans do. Consider a "fill window" step for long stretches without AI.
- [ ] Re-test check-in re-planning against a real model (blocked by the plan-quality items above)

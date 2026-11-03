# TODO

## Plan quality (local models)
- [x] Auto-repair AI plans before validating: drop tasks scheduled outside the task window
- [x] Try a stronger local model for development: `ollama pull qwen2.5:7b`, then `OLLAMA_MODEL=qwen2.5:7b`
- [x] Re-test real plan generation once both are done

## Re-planning redesign (agreed 2026-11-03)
Small local models get the minute arithmetic wrong, so re-plans fell back to the rule-based planner
and lost the personal coach message. New split: **code does all scheduling math, AI writes the words.**
Tasks are treated as a series, so leftover work must come first, not land weeks later.

- [ ] **Stage 1 — next day + shift forward**
  - Goal gets `extraMinutesPerDay` (user-set, capped at 50% of `minutesPerDay`)
  - Leftover goes on the next day as the first task if it fits in normal + extra time
  - Otherwise the following tasks shift forward in order, never over the limit
  - Extra time used at most 2 days in a row
  - AI only writes the coach message (responding to the user's note)
- [ ] **Stage 2 — weekly buffer day + health**
  - One catch-up day per week with no new tasks; shifts stop there
  - Health = remaining capacity / remaining work; show "X min behind"
- [ ] **Stage 3 — plan no longer fits (health < 100%)**
  - Offer choices: extend deadline / more daily time (code calculates) / cut scope (AI suggests) / re-plan
  - New endpoint to apply the chosen option
- [ ] **Stage 4 — pattern detection**
  - Missed ≥ 3 of the last 7 days → suggest a lighter plan
  - Repeatedly skipped heavy days → suggest lowering extra time

## Later
- [ ] Fill new days as the 14-day window moves (maybe covered by Stage 1 shifting; re-check after it)
- [ ] Optional: let AI pick a strategy (e.g. split a too-big task) that code then executes

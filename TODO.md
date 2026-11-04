# TODO

## Plan quality (local models)
- [x] Auto-repair AI plans before validating: drop tasks scheduled outside the task window
- [x] Try a stronger local model for development: `ollama pull qwen2.5:7b`, then `OLLAMA_MODEL=qwen2.5:7b`
- [x] Re-test real plan generation once both are done

## Re-planning redesign (agreed 2026-11-03)
Small local models get the minute arithmetic wrong, so re-plans fell back to the rule-based planner
and lost the personal coach message. New split: **code does all scheduling math, AI writes the words.**
Tasks are treated as a series, so leftover work must come first, not land weeks later.

- [x] **Stage 1 — next day + shift forward**
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

## Known problems (found 2026-11-03)
- [x] **Order within a day:** tasks on the same day were listed by creation id, so a catch-up task showed
      after that day's planned work, and later re-plans queued it behind them. Tasks now carry a series
      `sequence`; catch-up tasks get original + 1.
- [x] **Unreadable AI answers fail at once:** a plan with an impossible date ("2026-11-31") can't be parsed,
      which is treated as "provider down" (502) with no retry. Retry once and tell the model what was wrong.
- [x] **No timeout on AI calls:** `gemini-3.8-flash` never answered and the request hung for minutes.
      Set a read timeout so a slow provider fails fast (plan → 502, check-in → fixed message).
- [ ] **Free tier limits:** Gemini free quota can be very low and changes without notice; when it runs out,
      plan generation returns 502. Fine for personal testing, not for real users.
- [x] **Tests read the local `backend/.env`:** harmless today (AI is faked), but tests shouldn't depend on a
      developer's local config. Ignore `.env` in tests.
- [ ] **Extra-time streak only sees future days:** "max 2 extra days in a row" doesn't count catch-up days
      already in the past.
- [ ] **Small local models write inaccurate messages:** qwen 7B may describe changes that didn't happen.
      Gemini is much better; consider checking that the message mentions only real changes.

## Later
- [ ] Circuit breaker (Resilience4j): skip a model for a few minutes after repeated failures instead of
      waiting for its timeout on every request
- [ ] Fill new days as the 14-day window moves (maybe covered by Stage 1 shifting; re-check after it)
- [ ] Today is not frozen for a late check-in: checking in for yesterday re-plans from today
- [ ] Optional: one task row per piece of work (`remainingMinutes` + a `task_log` history table)
      instead of "Finish: X" copies
- [ ] Optional: let AI pick a strategy (e.g. split a too-big task) that code then executes

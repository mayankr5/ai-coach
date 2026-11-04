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
- [x] **Stage 2 — weekly buffer day + health**
  - Goal `bufferDay` (default Sunday): no planned tasks that day; catch-up work flows in and shifts stop there
  - Working days planned to 90% instead of 80%, so weekly planned time stays about the same
  - Progress shows `healthPercent` and `minutesBehind` for the next milestone (buffer days count, extra time doesn't)
- [ ] **Stage 3 — plan no longer fits (health < 100%)**
  - [x] `GET /adjustments` previews; extend moves the milestone, later ones and the deadline; more time picks the
        smallest daily time at which a re-pack fits the milestone's tasks, then re-packs from tomorrow
  - [ ] Cut scope: AI suggests tasks to drop with reasons, user confirms, code re-packs
  - [ ] Web: "Get back on track" panel
  - [ ] Later: start a fresh plan from tomorrow
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
- [x] **Extra-time streak only sees future days:** "max 2 extra days in a row" didn't count heavy days
      already planned before the re-plan. Now the 2 days before it are counted from their task minutes.
- [x] **Inaccurate coach messages:** models invented dates or called tomorrow "today". The prompt now says
      what today is and labels every date ("tomorrow (Thu 5 Nov)"); `MessageDates` rejects messages with
      dates not in the facts or "today" next to another date, and the model retries with the problem.
- [ ] **Wording beyond dates isn't checked:** a model could still describe a change that didn't happen
      without naming a date. Fine with Gemini so far; revisit if it shows up.
- [ ] **qwen 7B can miss the 60s plan timeout** on a cold start (seen 2026-11-04); fine as a fallback model,
      or raise `app.ai.plan-timeout` for local-only use.

## Later
- [ ] Tasks left unmarked on a day that has a check-in stay PENDING forever (the nightly job skips goals
      checked in yesterday). The web app requires every task to be marked; the API should handle it too
- [ ] Security: move the web app's refresh token from localStorage to an httpOnly cookie (needs CSRF protection)
- [ ] Circuit breaker (Resilience4j): skip a model for a few minutes after repeated failures instead of
      waiting for its timeout on every request
- [x] Fill new days as the 14-day window moves: the nightly job plans the next week when < 7 days are left
      (also `POST /goals/{id}/plan/extend`)
- [x] Today is not frozen for a late check-in: re-plans (check-ins and the nightly job) now start tomorrow
- [ ] Optional: one task row per piece of work (`remainingMinutes` + a `task_log` history table)
      instead of "Finish: X" copies
- [ ] Optional: let AI pick a strategy (e.g. split a too-big task) that code then executes

# AI Coach

A to-do app where an AI coach turns a goal into a realistic plan and adjusts it daily based on what you actually did.

```
ai-coach/
├── backend/   # Spring Boot 4 REST API (Java 21)
├── web/       # React + TypeScript (coming next)
└── mobile/    # Expo / React Native (later)
```

## Run the backend

```bash
docker compose up -d          # PostgreSQL on :5433 (avoids clashing with a local Postgres)
cd backend && ./mvnw spring-boot:run
```

- API docs: http://localhost:8080/swagger-ui.html
- Health:   http://localhost:8080/actuator/health

Tests use Testcontainers, so Docker must be running: `cd backend && ./mvnw test`.

## API (v1)

| Method | Path | Auth |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | public |
| POST | `/api/v1/auth/login` | public |
| POST | `/api/v1/auth/refresh` | public |
| POST | `/api/v1/auth/logout` | public |
| GET  | `/api/v1/users/me` | Bearer token |
| POST | `/api/v1/goals` | Bearer token |
| GET  | `/api/v1/goals?status=&page=&size=` | Bearer token |
| GET  | `/api/v1/goals/{id}` | Bearer token |
| PATCH | `/api/v1/goals/{id}` | Bearer token |
| DELETE | `/api/v1/goals/{id}` | Bearer token |
| GET  | `/api/v1/goals/{id}/tasks?date=YYYY-MM-DD` (default: today in the user's timezone) | Bearer token |
| PATCH | `/api/v1/tasks/{id}` | Bearer token |
| POST | `/api/v1/goals/{id}/check-ins` (record today's/yesterday's results, then re-plan) | Bearer token |
| GET  | `/api/v1/goals/{id}/check-ins` | Bearer token |
| GET  | `/api/v1/goals/{id}/progress` | Bearer token |
| POST | `/api/v1/goals/{id}/plan` (AI-generated; 409 once progress exists, 502 on AI failure) | Bearer token |

## AI provider

Plans are generated through Spring AI, so any supported model works. Pick one with environment variables:

| Provider | Settings |
| --- | --- |
| Ollama (default, local, free) | `AI_PROVIDER=ollama`, `OLLAMA_MODEL=llama3` |
| Anthropic | `AI_PROVIDER=anthropic`, `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL` |
| OpenAI | `AI_PROVIDER=openai`, `OPENAI_API_KEY` |
| Gemini | `AI_PROVIDER=google-genai`, `GEMINI_API_KEY`, `GEMINI_MODEL` (default `gemini-3.6-flash`) |

For local development, put these in `backend/.env` (git-ignored, loaded automatically), e.g.:

```
AI_PROVIDER=google-genai
GEMINI_API_KEY=your-key
```

After each check-in, `CatchUpScheduler` (plain code) puts leftover work first on the next day and shifts later tasks forward in order, using the goal's `extraMinutesPerDay` on catch-up days. The AI only writes the coach message; if it is down, a fixed message is used, so check-ins never fail. A nightly job (`MISSED_DAY_CRON`, default 03:00 UTC) marks overdue tasks as skipped for goals with no check-in and re-plans them without calling the AI.

Every plan is checked by `PlanValidator` (dates, daily minute limit, milestone order) whatever the model.

Set `JWT_SECRET` (at least 32 bytes) outside local development.

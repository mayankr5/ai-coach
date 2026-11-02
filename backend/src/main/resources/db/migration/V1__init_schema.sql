CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    timezone      VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE goals (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title             VARCHAR(200) NOT NULL,
    description       TEXT,
    deadline          DATE         NOT NULL,
    minutes_per_day   INT          NOT NULL CHECK (minutes_per_day > 0),
    experience_level  VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE milestones (
    id          BIGSERIAL PRIMARY KEY,
    goal_id     BIGINT       NOT NULL REFERENCES goals (id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    target_date DATE         NOT NULL,
    position    INT          NOT NULL
);

CREATE TABLE tasks (
    id                BIGSERIAL PRIMARY KEY,
    goal_id           BIGINT       NOT NULL REFERENCES goals (id) ON DELETE CASCADE,
    milestone_id      BIGINT       REFERENCES milestones (id) ON DELETE SET NULL,
    title             VARCHAR(200) NOT NULL,
    scheduled_date    DATE         NOT NULL,
    estimated_minutes INT          NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
);
CREATE INDEX idx_tasks_goal_date ON tasks (goal_id, scheduled_date);

CREATE TABLE check_ins (
    id           BIGSERIAL PRIMARY KEY,
    goal_id      BIGINT      NOT NULL REFERENCES goals (id) ON DELETE CASCADE,
    check_in_date DATE       NOT NULL,
    note         TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (goal_id, check_in_date)
);

CREATE TABLE plan_revisions (
    id         BIGSERIAL PRIMARY KEY,
    goal_id    BIGINT      NOT NULL REFERENCES goals (id) ON DELETE CASCADE,
    summary    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

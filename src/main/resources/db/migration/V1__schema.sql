-- Timesheet schema. The database enforces what must never be wrong;
-- the application enforces workflow rules (submitted weeks, locks, daily cap).

create table app_user (
    id            bigint generated always as identity primary key,
    email         varchar(254) not null unique,
    display_name  varchar(100) not null,
    password_hash varchar(100) not null,
    role          varchar(10)  not null check (role in ('USER', 'ADMIN')),
    active        boolean      not null default true,
    created_at    timestamptz  not null default now()
);

-- Projects and tasks are archived (active = false), never deleted,
-- so historical entries always point at something real.
create table project (
    id      bigint generated always as identity primary key,
    code    varchar(20)  not null unique,
    name    varchar(100) not null,
    client  varchar(100) not null,
    active  boolean      not null default true
);

create table task (
    id         bigint generated always as identity primary key,
    project_id bigint       not null references project (id),
    name       varchar(100) not null,
    billable   boolean      not null default true,
    active     boolean      not null default true,
    unique (project_id, name)
);

-- Time is stored as exact whole minutes. No floats, no rounding.
-- start_time is optional: present when the user typed a clock range or used the timer.
-- The end time is derived (start_time + minutes), so the two can never disagree.
create table time_entry (
    id         bigint generated always as identity primary key,
    user_id    bigint       not null references app_user (id),
    task_id    bigint       not null references task (id),
    work_date  date         not null,
    minutes    integer      not null check (minutes between 1 and 1440),
    start_time time,
    note       varchar(500),
    version    integer      not null default 0,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    -- a range may end at midnight but never cross it
    check (start_time is null
           or extract(epoch from start_time) / 60 + minutes <= 1440)
);

create index time_entry_user_date on time_entry (user_id, work_date);
create index time_entry_date on time_entry (work_date);

-- A week is DRAFT unless a row exists here. Reopening deletes the row (and is audited).
create table week_submission (
    user_id      bigint      not null references app_user (id),
    week_start   date        not null check (extract(isodow from week_start) = 1),
    submitted_at timestamptz not null default now(),
    primary key (user_id, week_start)
);

-- Single-row watermark: nothing on or before locked_through can change.
create table period_lock (
    id             smallint    primary key default 1 check (id = 1),
    locked_through date        not null,
    locked_by      bigint      not null references app_user (id),
    locked_at      timestamptz not null default now()
);

-- At most one running timer per user; lives on the server so it survives
-- closing the tab or switching devices.
create table running_timer (
    user_id    bigint      primary key references app_user (id),
    task_id    bigint      not null references task (id),
    started_at timestamptz not null,
    note       varchar(500)
);

-- Append-only change log, written in the same transaction as the change.
create table audit_event (
    id          bigint generated always as identity primary key,
    occurred_at timestamptz not null default now(),
    actor_id    bigint      not null references app_user (id),
    entity_type varchar(30) not null,
    entity_id   varchar(60) not null,
    action      varchar(20) not null,
    before      jsonb,
    after       jsonb
);

create index audit_event_entity on audit_event (entity_type, entity_id);

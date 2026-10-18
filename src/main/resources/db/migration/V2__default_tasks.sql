-- A project can be logged against directly, Clockify-style, without picking a
-- task. Every entry still has a task (the spec's "project + task"): it's the
-- project's default task, which the UI doesn't show.
alter table task add column is_default boolean not null default false;

-- at most one default task per project
create unique index task_one_default_per_project on task (project_id) where is_default;

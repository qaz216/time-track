-- Time is stored as exact whole seconds (it was whole minutes), so a timer
-- records exactly how long it ran. Existing entries convert exactly.
alter table time_entry add column seconds integer;
update time_entry set seconds = minutes * 60;
alter table time_entry alter column seconds set not null;

-- dropping the column also drops the old checks that mention it
alter table time_entry drop column minutes;

alter table time_entry add constraint time_entry_seconds_in_a_day
    check (seconds between 1 and 86400);
-- a range may end at midnight but never cross it
alter table time_entry add constraint time_entry_range_within_day
    check (start_time is null or extract(epoch from start_time) + seconds <= 86400);

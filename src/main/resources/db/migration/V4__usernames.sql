-- People sign in with a short username ("demo", "sam") instead of their email.
-- Existing people get their email as username, which is already unique; signing
-- in with an email keeps working either way.
alter table app_user add column username varchar(100);
update app_user set username = lower(email);
alter table app_user alter column username set not null;
alter table app_user add constraint app_user_username_unique unique (username);

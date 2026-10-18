-- A project doesn't have to belong to a client (Clockify's "no client"): internal
-- work, or a company where the project is the customer. The picker then lists
-- it at the top level instead of under a client heading.
alter table project alter column client drop not null;

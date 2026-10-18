# Timesheet

A small timesheet app for a 40-person consultancy that bills by the hour, built to replace the shared spreadsheet that breaks every Friday. Java 21, Spring Boot 4, PostgreSQL, server-rendered HTML.

## Run it

```bash
docker compose up --build
```

Then open http://localhost:8080 (port taken? `APP_PORT=8090 docker compose up --build`) and sign in with a demo account (password `demo1234`):

| Email | Role |
|---|---|
| `admin@example.com` | Admin |
| `sam@example.com` | User |
| `jo@example.com` | User |

The demo data includes three weeks of time for Sam and Jo, with one of Jo's weeks left unsubmitted, so the admin view has something to show. To start fresh: `docker compose down -v`.

Run the tests (needs Docker running, for Testcontainers):

```bash
./mvnw verify
```

## A two-minute tour

1. **Sign in as Sam.** Focus is already on the task picker. Type `a`, Tab, Tab, `9:05-10:42`, Tab, a note, Enter. Then type `1.5` and Enter: the second entry reuses the same task and day. Now try `1.33`.
2. Press `t` to start a timer; the header chip ticks on every page. Press `t` again to stop and log it.
3. Click **Submit week**. The week goes read-only; **Reopen** brings it back.
4. **Sign in as the admin** and open **Team weeks**: everyone × every week, ✓ for submitted, with Jo's missing week easy to spot. Click a cell to see that week, then **Export CSV**.
5. Open **Close period** and lock through last month. Now nobody, admins included, can change those days.

## Scope

**Built:** everything in the must-haves, plus all three nice-to-haves: keyboard-first entry, a running timer, and period locks.

**Cut, on purpose:**
- Manager approval (see below), invoicing and billing rates.
- Admin screens for managing users, projects and tasks. They're seeded instead. This is the first thing I'd add next, and the schema already supports archiving.
- SSO, password reset and login throttling.
- A UI for browsing the audit log, i18n, and a JSON API.

**What I sweated:**
- That time is stored **exactly**.
- That every rule lives in **one place** and the UI can't disagree with it.
- That **nothing changes without an audit row**.
- That nobody can touch someone else's hours by editing a URL.
- That entering a day's time is fast from the keyboard.

### Where I pushed back on the spec

| Spec | What I did | Why |
|---|---|---|
| "Increments of your choosing" | **One minute, no rounding** | Rounding is a billing rule, and billing is out of scope. Rounded data can't be un-rounded. |
| "Edit and delete; audit trail is your call" | **Hard delete plus an append-only audit log** | Soft delete puts a `where deleted = false` trap in every query that sums billable hours. |
| "Weekly submit step" | **Submit and reopen, no approval** | For 40 people the control that matters is finance closing the books, and that's the lock. |
| (implied) admin power | **Admins can't edit other people's hours** | A timesheet is that person's statement. Admins send a week back instead. |
| "Export totals as CSV" | **One flat row per person × week × task** | Finance can pivot it any way they like; any pre-totalled shape would be wrong for someone. |

## Data model

Eight tables, all in one readable migration: [`V1__schema.sql`](src/main/resources/db/migration/V1__schema.sql).

| Table | Holds | Notes |
|---|---|---|
| `app_user` | people, `USER` or `ADMIN` | Deactivated, never deleted |
| `project`, `task` | what time is logged against | Archived, never deleted, so old entries always resolve |
| `time_entry` | user, task, `work_date`, `minutes`, optional `start_time`, note, `version` | The only time data |
| `week_submission` | (user, Monday) | A row means submitted; no row means draft |
| `period_lock` | one `locked_through` date | A single row (`id = 1`, check-constrained) |
| `running_timer` | one per user | The primary key is the "one timer each" rule |
| `audit_event` | who, what, when, JSON before/after | Append-only |

**The database enforces what must never be wrong:**
- `minutes` between 1 and 1440.
- A clock range can't cross midnight.
- A week starts on a Monday.
- One lock, one timer per person.
- Foreign keys everywhere.

**The application enforces workflow:** submitted weeks, locks, ownership and the 24-hour daily cap.

`work_date` is a plain `date`, not a timestamp. The day you worked is a calendar fact, so there's no time-zone arithmetic on the main path. The one place that needs a zone (the timer and "today") uses the office zone from config.

## The shape of the code

A modular monolith, packaged by feature, not by layer:

```
identity    sign-in, CurrentUser, Actor
catalog     projects and tasks
tracking    time entries, weeks, submit/reopen, timer   ← the core
  domain      pure Java rules: TimeInput, WorkWeek, EditPolicy, TimerSplit, DailyLimit
  web         controllers and forms
period      the books-closed line
reporting   team overview and CSV
audit       the append-only log
shared      cross-cutting bits (exceptions, display formats, clock)
```

The seams worth looking at:

- **Rules are plain Java, with no Spring.** [`EditPolicy`](src/main/java/com/aryehgolob/timesheet/tracking/domain/EditPolicy.java) answers every "may this person change this?" question from simple inputs (`Actor`, owner, date, submitted?, lock). Services ask it; they never decide. The week page asks it too, to decide which buttons to show, so the UI and the server can't disagree.
- **One write path.** [`TimesheetService`](src/main/java/com/aryehgolob/timesheet/tracking/TimesheetService.java) is the only code that changes entries. Each use case does the same five things in one transaction: load, ask the policy, validate, save, audit. The timer reuses it rather than writing entries itself.
- **Writes go through JPA, reads through SQL.** Entries are written as JPA entities. Pages and reports are built with plain SQL into view records ([`WeekQueries`](src/main/java/com/aryehgolob/timesheet/tracking/WeekQueries.java), [`TeamReports`](src/main/java/com/aryehgolob/timesheet/reporting/TeamReports.java)). Each tool does what it's good at.
- **The domain never sees Spring Security.** Controllers turn the logged-in user into an `Actor(userId, admin)`, and that's all the rules know about who's asking. Swapping in SSO touches only `SecurityConfig`.
- **Controllers are thin.** They translate HTTP to a service call and back. Bad input comes back to the form with a message; rule violations become 403/404.

## Decisions in detail

### Time is exact minutes

- Stored as an `integer` number of minutes. Floats never touch the database.
- You can type `1:07`, `67m`, `1h 7m`, `1.5h` or a clock range like `9:05-10:42` ([`TimeInput`](src/main/java/com/aryehgolob/timesheet/tracking/domain/TimeInput.java)). A range also keeps its start time. The end is derived from start plus minutes, so the two can't disagree.
- Decimal hours that aren't a whole minute are rejected, not silently rounded: `1.33` → "79.8 minutes, not a whole minute. Did you mean 1:20?"
- A day can hold at most 24:00 across all your entries. Anything more is a typo.
- When invoicing exists, client-specific rounding ("6-minute increments") should happen there, once, from these exact minutes.

### Who can change what

- **A lock beats everything.** Nobody can change anything on or before the closed date, admins included.
- **Only the owner logs or edits their time.** Admins can view anyone's week and reopen it, but not rewrite it.
- **A submitted week is read-only** until it's reopened.
- **Guessing another user's entry id gets a 403**, and there are tests that try it over HTTP.
- **Two tabs editing the same entry:** the second save is refused using a version number (optimistic locking), instead of silently overwriting the first.

### Audit trail: hard delete plus an append-only log

Every create, update and delete of an entry, every submit and reopen, and every lock change writes an `audit_event` row recording who did it, along with JSON `before` and `after` snapshots.

- **Why not soft delete?** Every query would need `where deleted = false` forever, and forgetting it once double-counts billable hours. History belongs in the log, not the live table.
- **Why not event sourcing?** Too much machinery for 40 people. The question actually asked is "who changed this, and from what?", and one table answers it.
- **The audit row can't be skipped or orphaned.** [`AuditLog`](src/main/java/com/aryehgolob/timesheet/audit/AuditLog.java) requires an existing transaction (`Propagation.MANDATORY`), so a change and its audit row commit together or not at all. A test calls it outside a transaction and expects it to fail.
- An edit that changes nothing writes nothing and doesn't bump the version.
- The submit record stores the week's total at that moment, so "what did they submit?" stays answerable later.

### Submitting a week

A week is a **draft** until you press *Submit week*; then it's read-only. You can reopen your own week to fix something, and an admin can send anyone's week back.

- A week is submitted if it has a row in `week_submission`, and reopening deletes the row. There's no status column that can drift out of sync with reality.
- Submitting an empty week is allowed (holiday, sick week), after a confirm.

### Locking periods: one "closed through" date

- Closing the books is always "everything up to here", so a single date can't have holes and is trivial to reason about. Per-week or per-month flags can.
- **A week that straddles the line** (say the month ends on a Wednesday) stays editable on its open days. Submit and reopen are blocked only when the whole week is locked.
- An admin can move the line back or remove it (mistakes happen), and that's audited with before and after dates. You can't lock days that haven't happened yet.
- **Admin-only twice:** `/admin/**` is restricted by URL, *and* [`PeriodLockService`](src/main/java/com/aryehgolob/timesheet/period/PeriodLockService.java) and `TeamReports` check the role themselves, so a future endpoint that forgets can't open a hole.

### Admin: team weeks and the CSV export

**Team weeks** answers the Friday question, "who hasn't submitted?", at a glance. People who've left appear only if they logged time in the period. Periods are whole weeks (a mid-week start widens to the Monday), capped at a year.

**Export CSV:**

```
email,name,week_start,week_status,client,project_code,project,task,billable,minutes,hours
jo@example.com,Jo Chen,2026-09-28,submitted,Globex,GLOBEX-DATA,Data migration,Migration,yes,2025,33.75
```

- **`minutes` is exact and is the column to reconcile on.** `hours` is the same value to two decimals, for reading.
- **`week_status` is on every row**, so draft hours can't sneak into an invoice unnoticed.
- **Escaping:** quoting is done by Apache Commons CSV, not hand-rolled. Cells starting with `= + - @` get a leading `'`, so a project called `=HYPERLINK(...)` can't become a live formula in someone's spreadsheet (formula injection).

### The week page and keyboard entry

Entries are the source of truth; the task × day grid is a read-only rollup.

- The task picker has focus on load. Pick a task, Tab, time, Tab, note, Enter. After adding, the cursor sits in the time field with the same task and day.
- `n` new entry · `[` `]` previous/next week · `t` start or stop the timer · `Esc` cancel an edit.
- Bad input comes back with a message and exactly what you typed.
- Plain HTML forms, post/redirect/get, and about 70 lines of JavaScript. No front-end build, no SPA, and it works at phone width.

### Timer

- **It lives on the server**, one `running_timer` row per person. Closing the tab, restarting the laptop or switching devices doesn't lose it.
- **Stopping isn't special.** The time is cut at midnight ([`TimerSplit`](src/main/java/com/aryehgolob/timesheet/tracking/domain/TimerSplit.java)), and each piece goes through the same `TimesheetService.addEntry` as typed time, so the same rules, cap and audit apply. Times are the wall clock truncated to the minute, exactly what you'd write down.
- **Starting another task while one runs** stops and logs the first, so switching is one action.
- **Forgotten timers:** after 10 hours the chip says *Review*, and the week page asks when you really stopped before logging anything, or lets you discard it.
- **If the time can't be logged** (the week was submitted or locked while it ran), the stop is refused and the timer keeps running, so nothing is silently lost.

### Authentication: form login and server sessions

Email and password, BCrypt, a session cookie, CSRF protection on every form.

- **Why not JWT?** It's server-rendered, so there's no SPA or API client to hand a token to. Sessions are simpler and can be revoked.
- **What I'd really ship:** company SSO (Google Workspace or Microsoft 365 over OIDC). A 40-person consultancy already has one, and nobody wants another password. That's a swap of `formLogin` for `oauth2Login` in [`SecurityConfig`](src/main/java/com/aryehgolob/timesheet/identity/SecurityConfig.java); the rest of the app only sees `CurrentUser` and `Actor`.
- **Two roles,** `USER` and `ADMIN`, and that's all.
- **People who leave** are deactivated: their hours stay on the books and they can't sign in.

## Tests

About 150 tests, aimed at the parts that would hurt to get wrong:

- **Domain rules, plain JUnit, milliseconds to run:**
  - Parameterized tables for every input format and every rejection ([`TimeInputTest`](src/test/java/com/aryehgolob/timesheet/tracking/domain/TimeInputTest.java)).
  - The full owner/admin × submitted × locked matrix ([`EditPolicyTest`](src/test/java/com/aryehgolob/timesheet/tracking/domain/EditPolicyTest.java)).
  - Week boundaries across year ends and leap days.
  - Midnight splits.
- **Against real Postgres (Testcontainers), through the services:**
  - The daily cap across entries.
  - Stale edits.
  - Audit rows for every change, and none for refused or no-op ones.
  - Submit and reopen permissions.
  - Locks beating admins.
  - Timer splits and rollback.
- **Over HTTP (MockMvc):**
  - Sign-in edge cases.
  - Admin-only pages for a regular user.
  - CSRF.
  - Editing or deleting someone else's entry by id.
  - CSV quoting and formula injection, checked with a real CSV parser.
  - Pages rendering in each state.

All integration tests share one Spring context and one container, and isolate themselves by creating their own users rather than wiping tables, so the suite stays fast and safe to run in any order. CI runs `./mvnw verify` on every push.

## Known limitations and what I'd do next

1. **Admin screens for users, projects and tasks.** These are seeded today; this is the biggest real gap.
2. **SSO** in place of passwords.
3. **Reminders:** a Friday-afternoon nudge to people who haven't submitted, since the team page already knows who they are.
4. **An audit log viewer** per entry and per week. The data is there.
5. **Enforce the module boundaries** with an ArchUnit test, so the seams above can't erode.
6. **Small edges I know about and accepted:**
   - The 24-hour cap is checked in the application, so two simultaneous saves could in theory exceed it.
   - On the two daylight-saving days a year, a timer counts wall-clock time, not elapsed time.
   - The CSV streams straight from one query, which is plenty for 40 people and a year of data. A much bigger company would want it generated in the background.

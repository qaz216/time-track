# Timesheet

A small timesheet app for a 40-person consultancy that bills by the hour.

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

## Decisions

_Work in progress. This section walks through what I built, what I cut and why._

### Time is exact minutes. No increments, no rounding.

The spec says "increments of your choosing." I chose **one minute**, and I push back on rounding at all. A timesheet should record what happened. Billing increments ("round up to 6 minutes for this client") are an invoicing rule, invoicing is out of scope, and when it exists it should round once, per client contract, at billing time. If rounding were baked into the data, you could never undo it.

- Stored as an `integer` number of minutes. Floats never touch the database.
- You can type `1:07`, `67m`, `1h 7m`, `1.5h` or a clock range like `9:05-10:42` ([`TimeInput`](src/main/java/com/aryehgolob/timesheet/tracking/domain/TimeInput.java)). A range also keeps its start time; the end is derived, so the two can't disagree.
- Decimal hours that aren't a whole minute are rejected, not silently rounded: `1.33` → "79.8 minutes, not a whole minute. Did you mean 1:20?"
- A day can hold at most 24:00 across all your entries. Anything more is a typo.

### One place for the rules

Every "may I change this?" question goes to [`EditPolicy`](src/main/java/com/aryehgolob/timesheet/tracking/domain/EditPolicy.java), which is plain Java with a test table for each rule. [`TimesheetService`](src/main/java/com/aryehgolob/timesheet/tracking/TimesheetService.java) is the only code that writes entries, and every use case does the same five things in one transaction: load, ask the policy, validate, save, audit. The week page asks the same policy which days to make editable, so the UI and the server can't disagree.

- **Only you can change your hours, admins included.** A timesheet is that person's statement. An admin who spots a mistake sends the week back (see submit) rather than editing someone else's time.
- **Guessing another user's entry id gets you a 403**, and there's a test that tries it.
- **Two tabs editing the same entry:** the second save is refused, using a version number (optimistic locking), instead of silently overwriting the first.

### Audit trail: hard delete plus an append-only log

Entries are really deleted, and every create, update and delete writes an `audit_event` row with JSON `before` and `after` snapshots and who did it.

- **Why not soft delete?** Every query would need `where deleted = false` forever, and forgetting it once double-counts billable hours. The history lives in the log, not in the live table.
- **Why not event sourcing?** Too much machinery for 40 people. The question finance actually asks is "who changed this, and from what?", and one table answers it.
- **The audit row can't be skipped or orphaned.** [`AuditLog`](src/main/java/com/aryehgolob/timesheet/audit/AuditLog.java) requires an existing transaction (`Propagation.MANDATORY`), so the change and its audit row commit together or not at all. A test calls it outside a transaction and expects it to fail.
- An edit that changes nothing writes nothing.
- **Cut:** a UI for browsing the log. The data is there, and it's one query away.

### Submitting a week, and why there's no approval step

A week is a **draft** until you press *Submit week*, and then it's read-only. You can **reopen** your own week to fix something, and an admin can send anyone's week back. Both are audited, and the submit record keeps the week's total at that moment.

I pushed back on adding a manager approval workflow. The spec doesn't ask for one, and for 40 people it adds a state machine, a reviewer role and a queue that someone must clear every Friday. The control finance actually needs is "these numbers can't change any more", and that's the period lock. Submission means "I'm done"; the lock means "the books are closed."

- **Storage:** a week is submitted if it has a row in `week_submission`. Reopening deletes the row. There's no status column that can drift out of sync.
- **Submitting an empty week is allowed** (holiday, sick week), after a confirm.

### Locking periods: a single "closed through" date

Finance closes the books by setting one date. Nothing dated on or before it can change, for anyone, admins included. That covers entries, submits and reopens.

- **Why one date instead of per-week or per-month flags?** Closing is always "everything up to here." A single watermark (one row, `id = 1` enforced by a check constraint) can't have holes and is trivial to reason about.
- **A week that straddles the line** (say the month ends on a Wednesday) stays editable on the open days. Submit and reopen are blocked only when the whole week is locked.
- **Mistakes happen:** an admin can move the line back or remove it, and that's audited with the before and after dates. You can't lock days that haven't happened yet.
- **Admin-only twice:** `/admin/**` is restricted by URL, *and* [`PeriodLockService`](src/main/java/com/aryehgolob/timesheet/period/PeriodLockService.java) checks the role itself, so a future endpoint that forgets can't open a hole.

### Admin: team weeks and the CSV export

**Team weeks** shows everyone × every week in a period, with each cell's total and a ✓ when submitted. It answers the Friday question, "who hasn't submitted?", at a glance. Click a cell to open that person's week read-only, with a *Reopen* button to send it back.

- **People who've left** appear only if they logged time in the period, so old hours are never hidden but the list doesn't fill up with ghosts.
- **Periods are whole weeks.** A range that starts mid-week widens to the Monday, because weeks are what people submit. There's a cap of one year.

**Export CSV** produces one row per person × week × project × task:

```
email,name,week_start,week_status,client,project_code,project,task,billable,minutes,hours
jo@example.com,Jo Chen,2026-09-28,submitted,Globex,GLOBEX-DATA,Data migration,Migration,yes,2025,33.75
```

- **Flat on purpose.** Finance can pivot it by client, person or week in Excel. Any pre-aggregated shape I picked would be the wrong one for someone.
- **`minutes` is exact and is the column to reconcile on.** `hours` is the same value to two decimals, for reading.
- **`week_status`** is on every row, so totals from weeks that are still drafts can't sneak into an invoice unnoticed.
- **Escaping:** quoting is done by Apache Commons CSV, not hand-rolled. Cells starting with `= + - @` get a leading `'`, so a project called `=HYPERLINK(...)` can't become a live formula in someone's spreadsheet (formula injection). Both behaviours have tests.

### The week page

Entries are the source of truth; the grid (task × day) is a read-only rollup. Entry is keyboard-first:

- The task picker has focus when the page loads. Type a letter, Tab, time, Tab, note, Enter. After adding, the cursor sits in the time field with the same task and day, ready for the next entry.
- `n` jumps to a new entry, `[` and `]` move between weeks, and `Esc` cancels an edit.
- Bad input comes back with a message and exactly what you typed.
- Plain HTML forms, post/redirect/get, and under 100 lines of JavaScript. No front-end build, no SPA.

### Timer

Pick a task and press *Start timer* (or `t`). A chip in the header shows it ticking on every page, and `t` or *Stop* logs it.

- **It lives on the server**, as one `running_timer` row per person (the primary key enforces that). Closing the tab, restarting the laptop or switching devices doesn't lose it.
- **Stopping isn't special.** The time is cut at midnight ([`TimerSplit`](src/main/java/com/aryehgolob/timesheet/tracking/domain/TimerSplit.java)) and each piece goes through the same `TimesheetService.addEntry` as typed time, so the same rules, daily cap and audit apply. Times are taken from the wall clock, truncated to the minute, exactly as you'd write them down.
- **Starting a different task while one is running** stops and logs the first, so switching is one action.
- **Forgotten timers:** after 10 hours the chip changes to *Review*. The week page then asks when you actually stopped, before logging anything, or lets you discard it. A timer left overnight becomes one entry per day.
- **If the time can't be logged** (that week got submitted or locked while it ran), the stop is refused and the timer keeps running, so nothing is silently lost. You can then discard it.
- Timer starts and stops aren't audited themselves. What gets audited is the entries they create, because that's the data that matters.

### Authentication: form login and server sessions

Email and password, BCrypt hashes, a session cookie, CSRF protection on every form.

- **Why not JWT?** The app is server-rendered, so there's no SPA or API client to hand a token to. Sessions are simpler and can be revoked; tokens would add refresh and revocation problems for no benefit.
- **What I'd really ship:** company SSO (Google Workspace or Microsoft 365 over OIDC). A 40-person consultancy already has one, and nobody wants another password. Spring Security makes that a swap of `formLogin` for `oauth2Login` in [`SecurityConfig`](src/main/java/com/aryehgolob/timesheet/identity/SecurityConfig.java). The rest of the app only sees `CurrentUser` and doesn't care how you signed in.
- **Roles:** `USER` and `ADMIN`, and that's all. `/admin/**` is admin-only at the URL level, and ownership checks happen in the domain rules, not in controllers.
- **People who leave** are deactivated, not deleted. Their hours stay on the books, and they can't sign in.
- **Cut:** self-signup, password reset and login throttling. With SSO you don't build those; with this stopgap, an admin seeds users.

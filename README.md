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

Run the tests (needs Docker running, for Testcontainers):

```bash
./mvnw verify
```

## Decisions

_Work in progress. This section walks through what I built, what I cut and why._

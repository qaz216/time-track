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

### Authentication: form login and server sessions

Email and password, BCrypt hashes, a session cookie, CSRF protection on every form.

- **Why not JWT?** The app is server-rendered, so there's no SPA or API client to hand a token to. Sessions are simpler and can be revoked; tokens would add refresh and revocation problems for no benefit.
- **What I'd really ship:** company SSO (Google Workspace or Microsoft 365 over OIDC). A 40-person consultancy already has one, and nobody wants another password. Spring Security makes that a swap of `formLogin` for `oauth2Login` in [`SecurityConfig`](src/main/java/com/aryehgolob/timesheet/identity/SecurityConfig.java). The rest of the app only sees `CurrentUser` and doesn't care how you signed in.
- **Roles:** `USER` and `ADMIN`, and that's all. `/admin/**` is admin-only at the URL level, and ownership checks happen in the domain rules, not in controllers.
- **People who leave** are deactivated, not deleted. Their hours stay on the books, and they can't sign in.
- **Cut:** self-signup, password reset and login throttling. With SSO you don't build those; with this stopgap, an admin seeds users.

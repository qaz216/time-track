# Deploying to AWS (single EC2 instance)

One small EC2 instance runs the whole stack with Docker Compose. Caddy in front provides automatic
HTTPS; nothing else is reachable from the internet. The app has its own sign-in (demo / demo).

```
Internet ──443/80──▶ Caddy (HTTPS) ──▶ app (Spring Boot, :8080) ──▶ PostgreSQL
                     (only Caddy publishes ports; the app and database are on Docker's internal network)
```

| | |
|---|---|
| Instance | `t3.small` (2 vCPU, 2 GB RAM + 2 GB swap), Ubuntu 24.04, 20 GB disk |
| Cost | ≈ $0.025/hour running (≈ $20/month 24/7); stopped: only the disk, ≈ $1.60/month |
| Address | `https://<public-ip-with-dashes>.sslip.io`: a free hostname with a real Let's Encrypt certificate |
| Sign in | `demo` / `demo` (admin), plus the sample team. The demo data is seeded on first start |

## 0. Create a read-only GitHub token (the repository is private)

The server clones the code from GitHub, and this repository is private, so it needs a token that
can only **read** this one repository.

1. GitHub → your picture → **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**.
2. **Token name:** `timesheet-ec2`. **Expiration:** 7 days is plenty.
3. **Resource owner:** `ctgready`. **Repository access:** *Only select repositories* → `ready-cnd-aryehgolob`.
4. **Permissions → Repository permissions → Contents: Read-only.** Nothing else.
5. **Generate token** and copy it (it starts with `github_pat_`).

If the `ctgready` organization doesn't allow tokens, or the token needs an approval you can't get,
use [Plan B](#plan-b-copy-the-code-from-your-pc) instead.

## 1. Launch the instance (AWS console, ~5 minutes)

1. Open **EC2 → Launch instance** (pick a region near you, top right).
2. **Name:** `timesheet-demo`.
3. **Application and OS image:** *Ubuntu Server 24.04 LTS*, architecture **64-bit (x86)**.
4. **Instance type:** `t3.small`.
5. **Key pair:** your existing key (e.g. `aryeh-ec2-key`), or *Proceed without a key pair* and use
   **EC2 Instance Connect** in the browser.
6. **Network settings → Edit:**
   - Auto-assign public IP: **Enable**
   - Security group: create one, named `timesheet-demo`, with inbound rules:
     - **HTTP (80)** from *Anywhere*: needed for the HTTPS certificate and the redirect
     - **HTTPS (443)** from *Anywhere*
     - **SSH (22)** from *My IP*: only for Instance Connect / SSH
7. **Configure storage:** 20 GiB, gp3.
8. **Advanced details:**
   - **Metadata version:** *V2 only (token required)*
   - **Metadata response hop limit:** **1**, so containers can't reach the instance metadata.
   - **User data:** paste the whole of [`user-data.sh`](user-data.sh), then put your token between the
     quotes on the `GITHUB_TOKEN=""` line.
9. **Launch instance.**

## 2. Wait for it (~10 minutes) and open it

The first boot installs Docker, builds the app image with Maven and seeds the demo data. To find the address:

- **EC2 → Instances → your instance → Public IPv4 address**, e.g. `3.91.12.34`
- The app is at **`https://3-91-12-34.sslip.io`** (dots become dashes). Sign in with **demo / demo**.

To watch progress, connect via **Instance Connect** (or SSH) and run:

```bash
sudo tail -f /var/log/timesheet-setup.log
```

```bash
cat /opt/timesheet/SITE_URL
```

## 3. Day to day

- **Stop** the instance when you're not demoing (EC2 → Instance state → Stop): you only pay for the disk.
  **Start** it again later; the stack comes back by itself, with its data. The public IP changes after a
  stop, so look up the new address (same rule: `https://<new-ip-with-dashes>.sslip.io`). For a fixed
  address, attach an **Elastic IP** (small hourly charge while allocated).
- **Deploy a new version** after pushing to GitHub (the token isn't stored on the server, so pass it again):

  ```bash
  cd /opt/timesheet && sudo git pull "https://x-access-token:YOUR-TOKEN@github.com/ctgready/ready-cnd-aryehgolob.git" main && sudo systemctl restart timesheet
  ```

- **Logs** of the app:

  ```bash
  cd /opt/timesheet && sudo docker compose -f compose.yaml -f compose.prod.yaml logs -f app
  ```

- **Start over with fresh demo data** (deletes everything entered on the server):

  ```bash
  cd /opt/timesheet && sudo docker compose -f compose.yaml -f compose.prod.yaml down -v && sudo systemctl restart timesheet
  ```

- **Your own domain:** point a DNS A record at the instance, then add `SITE_ADDRESS=timesheet.example.com`
  to `/opt/timesheet/.env` and restart (`sudo systemctl restart timesheet`).

## Plan B: copy the code from your PC

If you can't use a GitHub token, launch the instance exactly as in step 1 but **leave User data empty**
and **pick your key pair**. Then, from the project folder on your PC (PowerShell or WSL), replacing
`3.91.12.34` with the instance's public IP:

1. Package the code that's on `main`:

   ```bash
   git archive --format=tar.gz -o timesheet.tar.gz main
   ```

2. Copy it and the setup script to the server:

   ```bash
   scp -i C:/dev/git/aryeh-ec2-key.pem timesheet.tar.gz deploy/aws/user-data.sh ubuntu@3.91.12.34:~
   ```

3. Unpack it and run the setup (it sees the code is already there and skips the clone):

   ```bash
   ssh -i C:/dev/git/aryeh-ec2-key.pem ubuntu@3.91.12.34 "sudo mkdir -p /opt/timesheet && sudo tar -xzf timesheet.tar.gz -C /opt/timesheet && sudo bash user-data.sh"
   ```

To deploy a new version later, repeat steps 1 and 2 (just the `.tar.gz`), then:

```bash
ssh -i C:/dev/git/aryeh-ec2-key.pem ubuntu@3.91.12.34 "sudo tar -xzf timesheet.tar.gz -C /opt/timesheet && sudo systemctl restart timesheet"
```

## Troubleshooting

**The setup log stops at `git clone` with "Authentication failed" or "not found".** The token is
missing, mistyped, expired, or lacks *Contents: Read-only* on this repository. Terminate the instance
and launch a new one with a fixed token, or use Plan B on this one.

**"Refused to connect" in the browser, and `timesheet.service failed` in the setup log.** Look at the cause:

```bash
sudo journalctl -u timesheet.service --no-pager | tail -40
```

- **`no space left on device`**: the disk is the 8 GB default. In the console: instance → **Storage** tab →
  volume → **Actions → Modify volume → 20 GiB**. Then **Stop** the instance and **Start** it.
- **The build is killed or hangs**: the instance is a t3.micro (1 GB). **Stop → Actions → Instance settings →
  Change instance type → t3.small → Start.**

**A certificate warning in the browser.** Use the **dashed** address (`3-91-12-34.sslip.io`): the HTTPS
certificate is issued for it. Right after the first start, give Caddy about 30 seconds to get it.

## 4. Tear down

**EC2 → Instances → Terminate.** That deletes the instance, its disk and all the data. If you created
an Elastic IP, release it too (EC2 → Elastic IPs), delete the security group, and delete the GitHub token.

## What's protected

- **Only Caddy is exposed** (80/443). Postgres and the app publish no ports (`compose.prod.yaml`).
- **HTTPS everywhere**, with HSTS. The app trusts Caddy's forwarded headers and marks the session cookie *Secure*.
- **A random database password**, generated on first boot into `/opt/timesheet/.env` (mode 600).
- **The GitHub token is used once and not kept in the repository config.** It's still visible in the
  instance's user data to anyone with AWS console access to the instance, so give it a short expiry,
  read-only access to one repository, and delete it when you're done.
- **Demo sign-ins are public knowledge** (demo / demo). For a demo only you and your reviewers can
  reach, limit the security group's HTTP/HTTPS rules to those IP addresses.

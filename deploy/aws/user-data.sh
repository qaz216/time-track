#!/bin/bash
# EC2 user data for a single-instance demo of the Time Track Application (Ubuntu 24.04, t3.small).
# Paste this whole file into "Advanced details → User data" when launching the instance.
# Setup log on the instance: /var/log/timesheet-setup.log   (see deploy/aws/README.md)

# ─────────── Settings ───────────
# The server clones the public copy of the code; no token needed. To clone a private
# repository instead, point REPO at it and set a read-only GitHub token (see the README).
REPO="github.com/qaz216/time-track.git"
GITHUB_TOKEN=""
# ────────────────────────────────

APP_DIR="/opt/timesheet"

set -euo pipefail
exec > >(tee -a /var/log/timesheet-setup.log) 2>&1
echo "=== Time Track setup started $(date -u) ==="

# Building the image needs ~5 GB of disk and ~2 GB of RAM; the AWS defaults (8 GB, t3.micro) are tight.
DISK_GB=$(( $(df --output=size -k / | tail -1) / 1024 / 1024 ))
MEM_MB=$(( $(grep MemTotal /proc/meminfo | awk '{print $2}') / 1024 ))
if [ "$DISK_GB" -lt 15 ] || [ "$MEM_MB" -lt 1800 ]; then
  echo "WARNING: this instance has ${DISK_GB} GB disk and ${MEM_MB} MB RAM; 20 GB and a t3.small (2 GB) are recommended."
fi

# Docker Engine + Compose from Docker's official installer.
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl git openssl
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker

# 2 GB swap: the Maven build plus Postgres can briefly need more than 2 GB of RAM.
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

if [ -d "$APP_DIR/.git" ] || [ -f "$APP_DIR/compose.yaml" ]; then
  echo "Code already in $APP_DIR (copied over by hand), not cloning."
elif [ -n "$GITHUB_TOKEN" ]; then
  git clone --depth 1 "https://x-access-token:${GITHUB_TOKEN}@${REPO}" "$APP_DIR"
  # Keep the token out of .git/config; updates pass it again (see the README).
  git -C "$APP_DIR" remote set-url origin "https://${REPO}"
else
  git clone --depth 1 "https://${REPO}" "$APP_DIR"
fi
cd "$APP_DIR"

# Secret: a random database password, readable by root only. Kept if it already exists,
# because the database was created with it.
if [ ! -f .env ]; then
  echo "POSTGRES_PASSWORD=$(openssl rand -hex 24)" > .env
fi
chmod 600 .env

# Start on every boot (the public IP, and so the sslip.io address, can change after stop/start).
chmod +x deploy/aws/start.sh
cp deploy/aws/timesheet.service /etc/systemd/system/timesheet.service
systemctl daemon-reload
systemctl enable timesheet.service
systemctl start timesheet.service

echo "=== Setup finished $(date -u). Open: $(cat "$APP_DIR/SITE_URL" 2>/dev/null || echo 'see start log') ==="

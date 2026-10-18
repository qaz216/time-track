#!/bin/bash
# Starts (or updates) the stack. Run at boot by timesheet.service; safe to run again any time.
# Uses the instance's current public IP to build an sslip.io hostname, so Caddy can get a real
# HTTPS certificate without buying a domain: 3.91.12.34 → https://3-91-12-34.sslip.io
# Set SITE_ADDRESS in .env to use your own domain instead.

set -euo pipefail
APP_DIR="/opt/timesheet"
cd "$APP_DIR"

if ! grep -q '^SITE_ADDRESS=' .env; then
  # IMDSv2: the instance metadata service needs a session token.
  TOKEN="$(curl -fsS -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')"
  PUBLIC_IP="$(curl -fsS -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)"
  export SITE_ADDRESS="${PUBLIC_IP//./-}.sslip.io"
fi

docker compose -f compose.yaml -f compose.prod.yaml up -d --build --remove-orphans

SITE="$(grep '^SITE_ADDRESS=' .env | cut -d= -f2- || true)"
echo "https://${SITE:-$SITE_ADDRESS}" > SITE_URL
echo "Time Track is starting at $(cat SITE_URL)  (the app takes ~30 s, the first HTTPS certificate ~30 s)"

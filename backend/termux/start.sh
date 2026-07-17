#!/data/data/com.termux/files/usr/bin/bash
# Canonical way to bring the backend up manually in Termux.
# Always works regardless of Termux:Boot / RUN_COMMAND status.
set -euo pipefail

cd "$(dirname "$0")/.."

# Creates .venv if absent, and rebuilds it if a Termux python bump left it
# unusable — a missing venv and a version-broken one look different but need
# the same fix, so both live in ensure-venv.sh. Exits non-zero if it can't,
# and `set -e` then stops us before a doomed uvicorn start.
./termux/ensure-venv.sh

exec .venv/bin/uvicorn main:app --host 127.0.0.1 --port 8420

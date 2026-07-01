#!/data/data/com.termux/files/usr/bin/bash
# Canonical way to bring the backend up manually in Termux.
# Always works regardless of Termux:Boot / RUN_COMMAND status.
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -d .venv ]; then
  python -m venv .venv
fi

source .venv/bin/activate
pip install -q -r requirements.txt

exec uvicorn main:app --host 127.0.0.1 --port 8420

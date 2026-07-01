#!/data/data/com.termux/files/usr/bin/bash
# Termux:Boot script — auto-starts the backend at device boot.
# Deployed to ~/.termux/boot/ on the device (Termux:Boot must be opened once
# after install to arm the boot receiver). The wakelock keeps Android from
# freezing/killing the server in the background; drop it if you prefer battery
# over always-on. The pgrep guard avoids a second copy (port 8420 conflict).
termux-wake-lock 2>/dev/null
cd ~/flashcard-companion/backend || exit 1
. .venv/bin/activate
if ! pgrep -f "bin/uvicorn main:app" >/dev/null 2>&1; then
  exec uvicorn main:app --host 127.0.0.1 --port 8420 >~/server.log 2>&1
fi

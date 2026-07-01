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
  # Backgrounded (was `exec`) so the script continues past this point to
  # also re-arm the notification job below, even on a cold first boot.
  uvicorn main:app --host 127.0.0.1 --port 8420 >~/server.log 2>&1 &
fi

# Re-arm the daily due-card notification job (Batch 7): termux-job-scheduler
# registrations don't survive a reboot, so re-register on every boot here,
# alongside the backend autostart. Best-effort period (~1/day); actual
# firing hour drifts under Doze (settled decision C3 — no strict-accuracy
# guarantee). See notify-due.sh's header for the full scheduling caveat
# (job-scheduler has no native wall-clock-hour trigger — this just ensures
# it stays armed once/reboot).
termux-job-scheduler \
  --job-id 1001 \
  --script ~/flashcard-companion/backend/termux/notify-due.sh \
  --period-ms 86400000 \
  --persisted true \
  2>>~/server.log || true

# Same re-arm for the exam-results reminder job (Batch 8) — independent
# job-id so both jobs coexist without clobbering each other.
termux-job-scheduler \
  --job-id 1002 \
  --script ~/flashcard-companion/backend/termux/notify-exam-results.sh \
  --period-ms 86400000 \
  --persisted true \
  2>>~/server.log || true

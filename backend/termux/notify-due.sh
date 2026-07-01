#!/data/data/com.termux/files/usr/bin/bash
# Daily due-card reminder (Batch 7). Queries the local backend's
# GET /api/due/count and fires a single termux-notification if due > 0.
#
# IMPORTANT: uses python3 urllib for the HTTP call, NEVER curl — curl is
# broken on this Termux (openssl/ngtcp2 version skew, see
# docs/plans/settings-notifications-stats.md "Known pitfalls").
#
# Registration (on-device, one-time + after every boot):
#   termux-job-scheduler --script ~/flashcard-companion/backend/termux/notify-due.sh \
#     --period-ms 86400000 --persisted true
# `--period-ms 86400000` = once/day; exact firing time is best-effort under
# Doze (settled decision C3 — no strict-accuracy mechanism). The notify hour
# itself is set from Réglages (`notify_hour` in the `settings` table,
# default 9) — termux-job-scheduler doesn't take a wall-clock hour, so on
# device this is normally paired with `termux-job-scheduler ... --period-ms`
# started once at boot near the configured hour, or re-armed via a small
# wrapper/cron-like loop; wire the exact on-device scheduling call during a
# phone session (this repo only ships the script + the registration
# reminder — see backend/termux/boot/start-flashcard-backend.sh for the
# re-registration hook added alongside the backend autostart).
#
# Fails silently on any error (backend not running, network hiccup, etc.)
# so a scheduled run never surfaces a crash — it just logs and exits 0.

set -uo pipefail

LOG="$HOME/notify-due.log"
BACKEND_URL="http://127.0.0.1:8420/api/due/count"

due_count="$(python3 - "$BACKEND_URL" <<'PYEOF' 2>>"$LOG"
import json
import sys
import urllib.request

url = sys.argv[1]
try:
    with urllib.request.urlopen(url, timeout=5) as resp:
        data = json.load(resp)
    print(int(data.get("due", 0)))
except Exception as exc:
    print(f"notify-due: backend unreachable ({exc})", file=sys.stderr)
    print(-1)
PYEOF
)"

if [ -z "$due_count" ] || [ "$due_count" -lt 0 ] 2>/dev/null; then
  echo "$(date -Iseconds) notify-due: skipped (due_count=$due_count)" >>"$LOG"
  exit 0
fi

if [ "$due_count" -gt 0 ] 2>/dev/null; then
  termux-notification \
    --title "Flashcards" \
    --content "$due_count cartes à réviser" \
    --action "am start -n com.matheo.flashcardcompanion/.MainActivity" \
    2>>"$LOG" || echo "$(date -Iseconds) notify-due: termux-notification failed" >>"$LOG"
fi

exit 0

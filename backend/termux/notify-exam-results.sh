#!/data/data/com.termux/files/usr/bin/bash
# Exam-results reminder (Batch 8). Queries the local backend's GET /api/exams
# and fires a single termux-notification if any subject's expected results
# date has passed and the grade hasn't been entered yet. Same mechanism as
# notify-due.sh (Batch 7): python3 urllib (never curl — broken on this
# Termux, see docs/plans/settings-notifications-stats.md "Known pitfalls"),
# termux-job-scheduler for scheduling, tap opens the app. The notification
# never contains a data-entry form — it just points back to the app; the
# grade is still entered manually on the Examens screen (settled Batch 8
# scope).
#
# Registration (on-device, one-time + after every boot):
#   termux-job-scheduler --script ~/flashcard-companion/backend/termux/notify-exam-results.sh \
#     --period-ms 86400000 --persisted true
# Same best-effort/Doze caveat as notify-due.sh (C3) — no strict-accuracy
# mechanism, wire the exact on-device scheduling call during a phone session.
#
# Fails silently on any error (backend not running, network hiccup, etc.)
# so a scheduled run never surfaces a crash — it just logs and exits 0.

set -uo pipefail

LOG="$HOME/notify-exam-results.log"
BACKEND_URL="http://127.0.0.1:8420/api/exams"

due_subjects="$(python3 - "$BACKEND_URL" <<'PYEOF' 2>>"$LOG"
import datetime
import json
import sys
import urllib.request

url = sys.argv[1]
try:
    with urllib.request.urlopen(url, timeout=5) as resp:
        exams = json.load(resp)
    today = datetime.date.today().isoformat()
    due = [
        e["deck_path"]
        for e in exams
        if e.get("grade") is None and str(e.get("expected_results_date", "")) <= today
    ]
    print(json.dumps(due))
except Exception as exc:
    print(f"notify-exam-results: backend unreachable ({exc})", file=sys.stderr)
    print("null")
PYEOF
)"

if [ -z "$due_subjects" ] || [ "$due_subjects" = "null" ]; then
  echo "$(date -Iseconds) notify-exam-results: skipped (backend unreachable)" >>"$LOG"
  exit 0
fi

count="$(python3 -c "import json,sys; print(len(json.loads(sys.argv[1])))" "$due_subjects" 2>>"$LOG" || echo 0)"

if [ "$count" -gt 0 ] 2>/dev/null; then
  subjects_list="$(python3 -c "import json,sys; print(', '.join(json.loads(sys.argv[1])))" "$due_subjects" 2>>"$LOG")"
  termux-notification \
    --title "Flashcards" \
    --content "Résultats à renseigner : $subjects_list" \
    --action "am start -n com.matheo.flashcardcompanion/.MainActivity" \
    2>>"$LOG" || echo "$(date -Iseconds) notify-exam-results: termux-notification failed" >>"$LOG"
fi

exit 0

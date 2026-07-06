# Plan — SM-2 Settings, Notifications, Advanced Statistics
> Executor: Sonnet + subagents. Apply the decisions, do not re-litigate them. Unresolved doubt → stop and ask the user.

## Goal & scope
Add three features to `flashcard-companion` (companion Android/WebView + backend FastAPI on Termux, degoogled):
- **B — SM-2 Settings**: make intervals/scores configurable, fix the degenerate "1 day" case, and make `/api/health` actually meaningful.
- **C — Notifications / daily reminders**: 1 notification/day at a fixed time if cards are due, via Termux (no FCM).
- **D — Advanced statistics**: log every review (append-only) to analyze behavior (time/card, success % per card, history), with export.

Out of scope: chantier A (app starts the backend) is **already done** (MainActivity auto-start + Termux:Boot). Do not touch it.

## Current context (verified this session — do not re-audit)
- **Real SRS**: `backend/srs.py` implements a "lite" SM-2. `review()` (srs.py:24-51) computes `due_at = now + interval_days`. Persisted in SQLite by `srs_store.py` (schema `card_state` at srs_store.py:12-21: `reps, interval_days, ease_factor, due_at, last_reviewed_at, created_at`).
- **Due filtering = real time**, no cron: each route recomputes `due_at <= now` (routes_decks.py `/api/tree`, `/api/due`; routes_review.py). UTC timezones correct everywhere.
- **Hardcoded parameters** in srs.py: `QUALITY_AGAIN=1/HARD=3/GOOD=4/EASY=5` (:9-12), 1st interval `1.0` (:28,:32), 2nd `6.0` (:34), following `interval*ease` (:36), initial ease `2.5` (:19), ease formula (:38-40), floor `1.3` (:41). No config/API surface exposes them.
- **Degenerate "1 day" bug**: on a new card (`reps=0`), Again AND Hard/Good/Easy all fall to `interval_days=1.0` → all 4 previews show "1 day". `_rating_previews` (routes_decks.py) faithfully reflects this real but misleading behavior.
- **Fake `/api/health`**: `main.py:35-37` hardcodes `{"status":"ok"}` — checks neither the database nor the Infercom key.
- **No notification/reminder anywhere** (neither Android, nor backend, nor Termux). "Due" is purely passive (pull).
- **No review log exists**: `srs_store.save_state` **overwrites** the current state (INSERT … ON CONFLICT DO UPDATE, srs_store.py:77-98). No history of past reviews is kept → needs to be created for stats.
- **Review POST**: `routes_review.py:42-60`, body `ReviewBody { quality:int 0-5 }` only. `app.js` `rate(quality)` POSTs `{quality}`. Time spent is neither measured nor sent.
- **Config**: `backend/config.yaml` (paths, infercom, server, explain) loaded by `config.py`, exposed via `app.state.cfg` (main.py:22-23). Store at `app.state.store`.
- **Front**: UI served from `backend/static/` — `index.html` (drill-down tree), `review.html` + `app.js` (session), `common.js` (theme/health/markdown + KaTeX `renderMath`), `style.css` (light/dark tokens). KaTeX self-hosted at `static/vendor/katex`, fonts `static/fonts`.
- **Constraints**: degoogled (never FCM/Play Services); backend runs on Termux; `termux-api` installed (termux-notification, termux-job-scheduler available); deck hierarchy derived from Anki `::` names; never a `Co-Authored-By` trailer.

## Settled decisions
- **C (notifs)**: **Termux** path (not FCM). **1 notification/day at a fixed time** if `due_count > 0`. Tap on the notification → **opens the app** (launcher intent `com.matheo.flashcardcompanion`).
- **B (fix 1 day)**: the 4 ratings must give **distinct intervals from the 1st review onward** (via configurable graduated intervals).
- **B (health)**: `/api/health` must actually check (at minimum: SQLite database openable + presence of `INFERCOM_API_KEY`), not a hardcoded `ok`.
- **D (stats)**: **append-only** log of every review (never overwritten), separate from `card_state`. Desired metrics (explicitly requested by the user): **time spent per card**, **whether the card was passed**, **success % per card**, full queryable history + **export** ("I'm my own Google").
- **D (time)**: the front must **measure time per card** and send it in the review POST.
- **Execution target**: Sonnet + subagents; UI following existing patterns (CSS tokens, `common.js` helpers, no framework).
- **B1**: intervals **in days**, graduated from `reps=0` onward (no sub-day learning steps). E.g. Again=0d/Hard=1d/Good=3d/Easy=7d on the 1st pass — `interval_days`/`due_at` schema unchanged.
- **B2**: Settings knobs = **the 4 graduated intervals** + **Easy bonus** (multiplier on mature cards). No global interval modifier, no starting ease/floor exposed.
- **B3**: **global** settings (all decks share the same values), no per-folder/subject scope.
- **B4**: persistence in a SQLite **`settings` table** (live-reload via `PUT`, no uvicorn restart). `config.yaml` keeps only what doesn't change via the UI (paths, infercom, server).
- **C1**: notification time **adjustable** from Settings (new field in the `settings` table), default **9am**.
- **C2**: notification content = **simple due total** ("N cards to review"), no per-subject detail.
- **C3**: scheduling **best-effort** via `termux-job-scheduler` (tolerant of Doze/battery drift), no strict accuracy mechanism.
- **D1**: timer started **when the front is displayed**, **no pause/resume handling** (background app) — simple, best-effort measurement.
- **D2**: **no purging** — `review_log` grows indefinitely (consistent with the "be your own Google" goal).
- **D3**: Stats views **minimal + calendar heatmap** (review days, highest/lowest day) + sortable per-card table. Export **CSV and JSON** (same endpoint, `?format=`).

## Batch 8 — Exam tracking & subject correlation (new, outside initial D scope, to be done after Batch 1-7)
- **Scope**: per subject (= root `::` folder), the user enters **in advance** an approximate exam results date. When the day comes, a notification (same mechanism as Batch 7) reminds them to enter the grade — **the notification doesn't contain an input field**, it just points to the app; entry stays manual in the Settings/Stats screen.
- **Data model**: new table `subject_grades(deck_path, expected_results_date, grade nullable)`, created by the user via a small form (subject + approximate date).
- **Reminder job**: reuses the Termux mechanism from Batch 7 (`termux-job-scheduler`, tap → opens the app); trigger condition = rows where `expected_results_date <= now` AND `grade IS NULL`.
- **Correlation displayed**: **no composite score**. Per subject, side-by-side display of 3 independent metrics — **grade obtained**, **review success %**, **total time invested** — ranking/comparison left to the user's eye, no invented weighting formula.
- **Dependencies**: requires Batch 5 (stats aggregated per card/deck) and Batch 7 (notification mechanism) already in place.
- **Non-scope**: no external grade import ("compare with my grades" stays out of scope for v1, cf. former D3).

## Batches (independent)

### Batch 1 — real `/api/health`  (independent, small)
- Files: `backend/main.py`.
- Actions: replace the `health()` handler (main.py:35-37) with an actual check: open the database (`app.state.store`) for reading (small `SELECT 1`), check `os.environ.get("INFERCOM_API_KEY")` is non-empty. Return `{"status":"ok"}` if all good, otherwise `{"status":"degraded","checks":{...}}` with a 200 status code (the UI only blocks on absence of response). Do NOT make an Infercom network call (just key presence).
- Constraints: keep the `{"status": ...}` shape (the `common.js` UI tests `res.ok`).
- Verify: `curl -s 127.0.0.1:8420/api/health`; remove the key → `degraded`.
- Done when: health reflects database+key state, the UI stays "Online" as long as FastAPI responds.

### Batch 2 — SM-2 Settings (backend)  (depends on B1-B4 decided)
- Files: `backend/srs.py`, `backend/srs_store.py` (or `config.yaml`), new `backend/api/routes_settings.py`, `backend/main.py` (include router).
- Actions: (1) move the SM-2 constants out of srs.py into a single source of truth (`settings` table recommended, cf. B4); `review()` takes the params as an argument or reads a settings object. (2) Implement the **graduated intervals** (cf. B1) so Again/Hard/Good/Easy diverge from `reps=0` onward → fixes the "1 day" bug. (3) `GET /api/settings` + `PUT /api/settings`. (4) `_rating_previews` (routes_decks.py) must use the same params.
- Constraints: no duplication of the SM-2 logic (a single function); soft migration (default values = current behavior except the graduation fix).
- Verify: `pytest backend/tests/` (adapt `test_srs.py`); `curl` GET/PUT settings; previews for a new card → 4 distinct values.
- Done when: changing an interval via PUT changes the previews and the next `due_at`, without editing code.

### Batch 3 — Settings screen (UI)  (depends on Batch 2)
- Files: `backend/static/` — new `settings.html` + JS, link from `index.html` (gear icon in the header), styles in `style.css` (reuse the tokens).
- Actions: screen listing the knobs (cf. B2), reads `GET /api/settings`, saves via `PUT`. Follow the existing style (mono labels, surface cards, dark mode).
- Verify: open `/settings.html`, change something, reload review → previews updated.
- Done when: the user can adjust intervals from the app.

### Batch 4 — Append-only review log + time measurement  (foundation for stats)
- Files: `backend/srs_store.py` (new `review_log` table), `backend/api/routes_review.py` (widen `ReviewBody`), `backend/static/app.js` (timer + sending).
- Actions: (1) table `review_log(id, guid, reviewed_at, quality, time_spent_ms, prev_interval_days, new_interval_days, prev_reps, new_reps)` — **never overwritten** (INSERT only). (2) `post_review` inserts a row on every rating (in addition to `save_state`). (3) `ReviewBody` accepts `time_spent_ms:int|None`. (4) `app.js`: measure the time (cf. D1) and send it.
- Constraints: append-only, don't touch the existing `card_state`; backward compat (time_spent optional).
- Verify: rate a few cards → `SELECT count(*) FROM review_log` grows; `time_spent_ms` non-null.
- Done when: every review leaves an immutable trace with the time spent.

### Batch 5 — Analytics (backend)  (depends on Batch 4)
- Files: new `backend/api/routes_stats.py`, `backend/main.py`.
- Actions: read-only aggregate endpoints from `review_log`: `GET /api/stats/overview` (total reviews, total time, overall success rate, per-day streak), `GET /api/stats/cards` (per card: number of reviews, success %, average/total time, last grade), `GET /api/stats/export?format=csv|json` (raw log dump). Success % = (Good+Easy)/total per the convention to confirm (D3).
- Verify: `curl` each endpoint after a few reviews.
- Done when: the requested metrics (time/card, success %/card, history) are queryable + export works.

### Batch 6 — Statistics (UI)  (depends on Batch 5)
- Files: `backend/static/` — `stats.html` + JS, link from `index.html`, styles.
- Actions: views (cf. D3): overview, **calendar heatmap** of review days (+ highest/lowest day), sortable per-card table (success %, time), CSV/JSON export button. Homegrown SVG charts or a self-hosted lightweight lib (no Google CDN). Follow tokens + dark mode.
- Verify: open `/stats.html`, data consistent with the log.
- Done when: the user can view and export their review data.

### Batch 7 — Daily notifications (Termux)  (depends on C1-C3; can default)
- Files: new `backend/api/` endpoint `GET /api/due/count` (total due right now); new script `backend/termux/notify-due.sh`; registration via `termux-job-scheduler` (add to the Termux:Boot script `~/.termux/boot/start-flashcard-backend.sh`, already deployed).
- Actions: (1) `/api/due/count` → `{ "due": N }`. (2) `notify-due.sh`: query the endpoint (via `python3 urllib`, **not `curl`** — broken on this Termux), if `due>0` → `termux-notification --title "Flashcards" --content "N cards to review" --action "am start -n com.matheo.flashcardcompanion/.MainActivity"`. (3) schedule 1x/day at the chosen time (C1) via `termux-job-scheduler --script ... --period-ms 86400000` (or `at`/loop); re-register on boot. Battery: Termux/Termux:Boot already whitelisted.
- Constraints: degoogled (termux-notification is local, OK); `curl` broken → use Python for local HTTP requests.
- Verify: run `notify-due.sh` by hand → notification appears, tap opens the app.
- Done when: one notification/day at the fixed time reminds about due cards.

## Execution
- One subagent per batch; **commit + `/clear` between each batch**.
- Recommended order: 1 → 2 → 3 (settings); 4 → 5 → 6 (stats); 7 (notifs); 8 (exams, depends on 5 and 7) last.
- After batches touching the backend: **deploy to the phone** (scp to `~/flashcard-companion/backend/`, restart uvicorn) and verify via `adb forward tcp:8420` + browser/app. The PC repo is the source; the phone is the execution target.
- Tests: `pytest backend/tests/`; the UI is tested by opening `http://127.0.0.1:8420/...` (via `adb forward`).

## Known pitfalls
- **`curl` is broken on this Termux** (openssl/ngtcp2 skew) → any HTTP request on the Termux side must use **Python `urllib`**, never curl.
- **Termux cannot install manylinux wheels** → any new native dependency must be prebuilt for Termux (`pkg`) or pure-python; `requirements.txt` is already reduced to what builds (pdfplumber optional/lazy).
- **Don't duplicate the SM-2 logic**: previews (`_rating_previews`) and `review()` must share the same params source, otherwise UI/actual behavior diverge.
- **Append-only log**: never do UPDATE/DELETE on `review_log`; `card_state` stays the separate current state.
- **`/api/health`** keeps the `{"status": ...}` shape and **200 status code** even when `degraded` (the UI only blocks if FastAPI doesn't respond).
- **Notification**: the tap action path must target `com.matheo.flashcardcompanion/.MainActivity` (exported).
- **No CDN** (Google or otherwise) for any charts: self-host.
- **Never a `Co-Authored-By` trailer** (project rule).

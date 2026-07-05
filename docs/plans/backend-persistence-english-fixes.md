# Plan — Backend persistence + English taxonomy + pipeline cleanup
> Executor: Sonnet + subagents. Apply the decisions below, do not re-litigate them. Any unresolved doubt → stop and ask Matheo.

## Goal & scope
Three independent workstreams on two repos:
- **flashcard-companion** (this repo, the Android WebView app + FastAPI backend).
- **flashcard-pipeline** (`../flashcard-pipeline`) — **now editable** (the old "read-only" rule was removed; only its *data* `.apkg`/`themes.json`/`processed_files.json`/`.env` must never be corrupted, its *source* can be edited).

Priority order: **Batch 1 (backend persistence) first** — it's the app's biggest reliability gap. Batches 2–3 are pipeline quality fixes.

Context — the environment (verified this session):
- The pipeline runs **on the PC** (systemd units + `flashcard-pipeline/.env` holds the Infercom key). Reproduce runs on the PC with `.venv/bin/python pipeline.py [config]`.
- The backend runs **on the phone in Termux**, served at `127.0.0.1:8420`; the app is a thin WebView pointing there.
- Phone reachable by `adb` (device `9a10240`). Rooted Magisk but **`su` is DENIED from adb** — no root over adb. Termux private sandbox not readable via adb.
- Static-file changes deploy without an APK rebuild (thin WebView). Kotlin changes need an APK rebuild via `~/android-build` (JDK 17 — see memory `project-android-build-toolchain-missing`).

Already committed this session — **do NOT redo**:
- pipeline `7ce6338` (0-cards fix: analyst `max_tokens=8192`, `import json` in builder, output-aware state) + `9df186d` (matière = source folder).
- companion `7533e22` (CLAUDE.md) + `caee800` (home auto-refresh on `visibilitychange`).

## Settled decisions
- All courses are in **English** → generated cards AND taxonomy (theme names) must be English. Matière = folder name (Matheo controls it). See memory `project-all-courses-english`.
- Matière = source folder path relative to `~/Sync/Cours`, not the analyst subject. See memory `project-matiere-is-source-folder`. Don't touch this.
- Backend must come up **unattended** — at phone reboot (Termux:Boot) and/or app launch — and survive in background with a wakelock. No manual `start.sh`.
- No `Co-Authored-By: Claude` trailer anywhere (project rule).

## Open questions (do NOT decide — ask Matheo)
- **Batch-5 grounding sign-off**: the "besoin d'aide" button was implemented autonomously as *whole-PDF grounding, stateless, cache keyed on `(rel_path, question)`*, mirroring `explain.py`. Matheo never confirmed this. Before touching it, ask: keep as-is, or change grounding granularity / add multi-turn? (memory `project-pdf-reader-execution`). This is not a code batch until answered.
- If Batch 1 diagnosis shows the app-launch autostart needs a **Kotlin change**, confirm with Matheo before an APK rebuild (bigger blast radius than a script fix).

## Batches (independent)

### Batch 1 — Backend persistence (priority; device-dependent)
- Files:
  - `backend/termux/start.sh` — manual start; `set -euo pipefail`, venv, `pip install -q`, then `exec uvicorn main:app --host 127.0.0.1 --port 8420` (foreground, **no wakelock**).
  - `backend/termux/boot/start-flashcard-backend.sh` — Termux:Boot script; `termux-wake-lock`, `cd`, activate venv, `pgrep` guard, backgrounds `uvicorn … >~/server.log 2>&1 &`, then re-arms two `termux-job-scheduler` jobs.
  - `android/app/src/main/java/com/matheo/flashcardcompanion/MainActivity.kt` (~L38–41 requestPermissions, ~L114 the `bash ~/flashcard-companion/backend/termux/start.sh` command, ~L126–131 the RunCommandService intent) — the app-launch autostart.
- Diagnosis to run first (don't assume the fix):
  1. Reboot the phone (`adb reboot`), wait for boot, poll `adb forward tcp:8420 tcp:8420 && curl -s --retry 20 --retry-delay 3 --retry-all-errors http://127.0.0.1:8420/api/health`. Does Termux:Boot bring it up? Termux:Boot must have been opened once to arm the receiver — verify the boot script is actually at `~/.termux/boot/` on-device (can't read via adb without root; infer from behavior, or check `dumpsys` / logs). Read `~/server.log` is blocked (Termux private) — capture output to `/sdcard` instead if you need it (write a wrapper that redirects to `/storage/emulated/0/Download/…`).
  2. App-launch path: force-stop + relaunch the app, then check port 8420. The app fires `RunCommandService` (confirmed via `dumpsys activity services com.termux` — `lastActivity` updates) but uvicorn does **not** reliably bind. Likely cause to investigate: `start.sh` does `exec uvicorn` (foreground) and RunCommandService may run it without a persistent session / without background flag, so it dies; OR `pip install` on every launch stalls/fails offline. Compare with the boot script which backgrounds with `&` + wakelock.
- Actions (candidate fixes — pick based on diagnosis, keep minimal):
  - Make the app-launch command robust: point MainActivity's RunCommand at a **single canonical launcher** that backgrounds uvicorn + takes a wakelock + has a pgrep no-double-start guard (i.e. converge `start.sh` and the boot script onto one idempotent script). Skipping `pip install` when deps are already present avoids an offline stall.
  - Ensure a wakelock is held whenever the server runs in background (Android freezes Termux under Doze otherwise).
  - If the RunCommandService invocation needs `RUN_COMMAND_BACKGROUND=true` or a workdir/session flag, adjust MainActivity.kt (→ APK rebuild, confirm first per Open questions).
- Constraints: no root over adb; deploy scripts by getting them into the Termux home (adb can only write `/sdcard` — land the file there then it must be `cp`'d into `~/flashcard-companion/…` from a Termux shell; a static-only path avoids Termux gymnastics but scripts live in the private sandbox). Follow the existing pgrep-guard / wakelock idiom already in the boot script.
- Verify: after `adb reboot`, `curl http://127.0.0.1:8420/api/health` returns `{"status":"ok"}` with **zero manual steps**; and after force-stop + relaunch of the app, same. Confirm the process survives ~2 min backgrounded (screen off) — health still ok.
- Done when: a cold reboot of the phone yields a working app (deck list loads) without anyone opening Termux, and the wakelock keeps it alive in the background.

### Batch 2 — English taxonomy (aggregator)
- Files: `../flashcard-pipeline/agents/aggregator.py` (`SYSTEM_PROMPT` + `aggregate_themes`; the `SYSTEM_PROMPT` is currently written in French and yields French canonical theme names like "Intervalles de confiance", "Quantités pivots").
- Actions: rewrite the theme-aggregation system prompt so canonical theme names are produced **in English** (the fusion rules/examples stay, just switch the output language to English and add an explicit "canonical names in English only" rule). Grep the pipeline for any other prompt that forces or emits French in generated deck content; the builder prompts are already "English only" (`agents/builder.py`) — leave those. Matière comes from the folder name, out of scope here.
- Constraints: mirror the existing prompt structure; don't change the `{resolved, new_themes}` JSON contract (pipeline depends on it). **Scope = generated deck content ONLY.** The app UI stays bilingual — do NOT touch `backend/static/i18n.js` or the FR/EN toggle; English applies to cards + theme names, not the interface.
- Verify: sandbox run (see Execution → pipeline sandbox) on `~/Sync/Cours/<some English course>` → the logged theme/deck names are English (`[Matière::<English theme>] +N cards`), no French.
- Done when: a fresh run produces only English theme names.

### Batch 3 — Pipeline robustness cleanup (broutilles)
- Files: `../flashcard-pipeline/json_utils.py`, `../flashcard-pipeline/agents/aggregator.py` (determinism), `../flashcard-pipeline/pipeline.py` (annotation).
- Actions:
  - **(a) JSON `Invalid \escape` hardening** — `parse_json_response` in `json_utils.py` already retries once by escaping invalid backslashes (`re.sub(r'\\(?!["\\/bfnrtu])', …)`), but a problem-card batch still failed this session (`Invalid \escape: line 5 column 71`, theme "Intervalles de confiance") → the whole batch was silently dropped (caught in `pipeline.py build_theme` → `[]`). Harden the LaTeX-backslash handling so realistic builder output parses (e.g. apply the escape-fix also inside the first attempt, or make the regex robust to `\\` sequences and `\(`/`\[` math delimiters). **Add `../flashcard-pipeline/tests/test_json_utils.py`** (no tests dir exists yet) with a payload containing raw LaTeX backslashes (`\frac`, `\alpha`, `\(`, `\sigma^2`) that currently throws, asserting it now parses.
  - **(b) Aggregator determinism** — the theme aggregator sometimes collapses all proposed themes into one canonical across runs (saw 5 themes → 1 one run, 2–3 another). Tighten the prompt (e.g. "do not over-merge; merge only near-identical themes; keep distinct chapters distinct") and/or lower temperature if the API supports it, to make theme granularity stable. Keep it a prompt/param change, not an architecture change.
  - **(c) Annotation cleanup** — `pipeline.py` `process_group(client: anthropic.Anthropic, …)` references `anthropic.Anthropic` with no `import anthropic` (leftover from an Anthropic→OpenAI migration; harmless under PEP 649). Change the annotation to `OpenAI` (already imported).
- Constraints: minimal diffs; don't alter the `{resolved,new_themes}` contract; (b) must not regress (a) or Batch 2.
- Verify: `../flashcard-pipeline/.venv/bin/python -m pytest tests/` for (a); a sandbox run for (b)/(c) completes with no `Builder [...]` errors and stable theme count across two consecutive runs of the same input.
- Done when: the new JSON test passes, and two back-to-back sandbox runs on the same course produce the same set of canonical themes with no dropped card batches.

## Execution
- One subagent per batch; **commit + `/clear` between batches**.
- Model tiers: Batch 1 needs careful reasoning + device iteration → keep on the **main Sonnet** loop (don't fan out blindly; device state is shared). Batches 2 & 3 are small, well-scoped edits → a **Sonnet subagent each** is fine; Batch 3(a) test-writing is mechanical. No Opus needed.
- Pipeline sandbox (how to verify pipeline changes without touching real state): write a temp `sandbox_config.yaml` that copies `../flashcard-pipeline/config.yaml` but points `output_dir`, `state_file`, `themes_file` to a scratch dir and `input_dir` to a real English course under `/home/matheo/Sync/Cours/<X>` (use an **absolute** path — `load_config` expands `~` now, but be explicit). Run `../flashcard-pipeline/.venv/bin/python pipeline.py <sandbox_config.yaml>`. This hits the real Infercom API (key in `../flashcard-pipeline/.env`) but writes nothing to `~/Sync/Flashcards` or the real state. Clear the scratch `state.json` between runs to force reprocessing.
- Backend companion tests (if you touch backend Python): `cd backend && pytest tests/`.
- Commits: no `Co-Authored-By: Claude` trailer.

## Known pitfalls
- **No root over adb** — you cannot read Termux's private home (`~/server.log`, the venv) nor `pm clear`. To see a script's output, redirect it to `/storage/emulated/0/Download/…` and `adb pull` it. To run a Termux command when uvicorn holds the interactive session: send Ctrl-C via the on-screen CTRL extra-key (tap it, then `adb shell input text c`), or open a new Termux session — `am startservice`/`start-foreground-service` for RunCommandService from adb shell **fails** with a Binder "Failed transaction" error, so drive Termux via the UI (`adb shell input`) + verify with `screencap`.
- **WebView caches hard** — after any static change, the running app keeps the old page until it reloads; backend already sends `Cache-Control: no-store` (memory `project-app-webview-deploy-model`).
- **Marking-processed trap is already fixed** but state now stores the produced `.apkg`; if you reset/regenerate, delete the stale `.apkg` (PC + phone) by **exact name** (wildcard `rm` in `~/Sync/Flashcards` is blocked by the safety classifier).
- The phone's own Syncthing device was `connected:false` this session — new `.apkg`s were delivered by `adb push` to `/storage/emulated/0/syncthing/Flashcards/`, not by sync. Don't assume Syncthing propagates.
- Pipeline has **no existing test suite**; Batch 3(a) introduces the first `tests/`.
</content>

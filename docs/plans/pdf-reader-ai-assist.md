# Plan — Integrated PDF reader + grounded AI help

> Executor: Sonnet + subagents. Apply the decisions, do not re-litigate them. Unresolved doubt → stop and ask the user.

## Goal & scope

Add a PDF reader built into the app (WebView), to consult source course materials directly, with a floating "need help" button (sparkle icon, Gemini-style visually, DeepSeek-V3.1 via Infercom on the engine side) allowing a grounded question to be asked about the PDF currently displayed.

**Decision context**: the original idea was a generic chatbot hooked up to DeepSeek. It was dropped in favor of this PDF reader + grounded question approach, judged superior because the grounding becomes exact (the displayed PDF) instead of the current fragile heuristic (`source_matcher.py`, matching by file/folder name, `difflib` threshold at 0.6).

This is a **completely new need** — it does not replace any existing way of consulting course materials.

## Current context (repo `flashcard-companion`)

- **No PDF reader in the app** today — source PDFs live in `pdf_dir` (configured in `backend/config.yaml`), used only for text extraction for `explain.py`'s grounding (on-demand card explanation), never displayed as-is.
- **`backend/source_matcher.py`**: heuristic matching of card subject → PDF file(s) by name (difflib, threshold 0.6), no real card→document traceability (limitation documented at the top of the file).
- **`backend/pdf_context.py`**: builds a truncated text context (`max_pdf_context_chars`) from matched PDFs, to include it in the Infercom prompt.
- **`backend/explain.py`**: Infercom call pattern already in place (system prompt, `client.chat.completions.create`, DeepSeek-V3.1 model) — direct reference for the new help button.
- **Deck hierarchy** derived from Anki `::` names in the `.apkg` files (see `docs/plans/settings-notifications-stats.md` for the full SRS context, not relevant here).
- **Header of `backend/static/index.html`**: icons already present for Settings (gear), Stats (bar-chart), Exams — new icon pattern to follow for the "Courses" entry.
- **No CDN allowed** (project rule) — all rendering (PDF, icons) must be self-hosted, as KaTeX already is for formulas.

## Settled decisions

- **Two access points**, not three (the card-only level is judged redundant):
  1. **Header icon "Courses"** (peer of Settings/Stats/Exams) → list of PDFs organized using the same `::` folder hierarchy as the decks → opens the chosen PDF in the reader.
  2. **Contextual shortcut on the review screen**: a "view source course" link on the card, using the existing `source_matcher.py` matching (subject → PDF), directly opens the same reader on the right PDF.
- **Floating "need help" button** at the bottom right of the PDF reader, sparkle icon (Gemini visual reference), asks a grounded question about the displayed PDF — DeepSeek-V3.1 engine via Infercom, same infra as `explain.py`.
- **No CDN** — self-hosted PDF rendering.

## Open questions (to be settled before/during implementation — do not decide on the user's behalf)

- **Grounding granularity** for the help button: the whole PDF, or only the page/section currently visible on screen? (Question raised in conversation, never answered.)
- **Interaction model**: a single question with no history (stateless, like `explain_card`) or a multi-turn conversation within a PDF reading session?
- **Response caching**: are the help button's responses cached (by PDF + question, like `explain.py` caches by `card.guid`) or always regenerated?
- **Front-end PDF rendering library**: to be chosen while respecting the no-CDN constraint (obvious candidate: self-hosted PDF.js, as KaTeX already is — to be confirmed, not yet settled).
- **A deck folder can have several PDFs** (e.g. annotated version + raw version coexist, cf. dedup by file hash in `flashcard-pipeline/state.py`): should the "Courses" screen list both separately, or only show the most recent/annotated one?
- **PDF serving endpoint**: `pdf_dir` is today only used for text extraction on the backend side — a new endpoint is needed to stream/serve the raw PDF to the frontend (route name, path handling, file access security — to be defined).

## Batches (independent)

### Batch 1 — PDF reader technical choice (blocking, to be settled with the user before any code)
- Files: none yet.
- Actions: propose 1-2 self-hosted PDF rendering options (weight, Android WebView compatibility, absence of CDN dependency), get the user's decision.
- Constraints: no CDN.
- Done when: library chosen and confirmed by the user.

### Batch 2 — PDF serving endpoint (backend)
- Files: new `backend/api/routes_pdf.py`, `backend/main.py` (include_router).
- Actions: endpoint serving the raw content of a PDF from `pdf_dir` (to be secured against path traversal), plus a listing endpoint for available PDFs organized by `::` folder (reuse the hierarchy logic from `routes_decks.py`, do not duplicate it).
- Constraints: do not duplicate the deck tree logic.
- Verify: `curl` the listing endpoint + download a test PDF.
- Done when: a PDF can be fetched and listed via the API.

### Batch 3 — "Courses" screen (UI)
- Files: new `backend/static/courses.html` + JS, icon in `common.js` (ICONS), link in `index.html` header (follow the Stats/Exams pattern exactly).
- Actions: list of PDFs by `::` folder, click → opens the reader (Batch 4).
- Constraints: follow existing CSS tokens, dark mode, no CDN.
- Verify: navigate the list, open a test PDF.
- Done when: the user can browse their courses and open one.

### Batch 4 — Integrated PDF reader
- Files: new `backend/static/pdf-viewer.html` + JS (depends on the Batch 1 choice).
- Actions: PDF display (chosen library), page navigation, called from Batch 3 and from the card shortcut (Batch 6).
- Constraints: self-hosted, respects dark mode if the library allows it.
- Verify: open a real multi-page PDF, navigate through it.
- Done when: a complete PDF is readable in the app.

### Batch 5 — "Need help" button (backend + UI)
- Files: new `backend/api/routes_pdf_help.py` (or extension of `routes_pdf.py`), `backend/static/pdf-viewer.html`/JS (floating button + question/answer modal).
- Actions: reuse the `explain.py` pattern (system prompt, Infercom DeepSeek-V3.1 call, grounding via `pdf_context.py`) for a question asked about the displayed PDF — granularity and caching **depend on the Open Questions above, to be settled before writing this batch**.
- Constraints: do not duplicate `explain.py`'s Infercom call pattern — factor it out if relevant.
- Verify: ask a question about a test PDF, grounded answer received.
- Done when: the user can ask a question about the open PDF and receive a relevant answer.

### Batch 6 — Shortcut from the review screen
- Files: `backend/static/review.html` + `app.js`.
- Actions: "view source course" link on the card, using `source_matcher.py` (already existing, do not duplicate), opens the Batch 4 reader on the right PDF.
- Constraints: do not create a 3rd distinct access point — this link opens the same reader as Batch 3/4.
- Verify: from a card during review, click the link → correct PDF opens.
- Done when: one-click access from review to the source course.

## Execution
- One subagent per batch; **commit + `/clear` between each batch**.
- Batch 1 is blocking: do not launch Batch 2+ without the user's answer on the library choice.
- Batch 5 depends on the answers to the Open Questions (granularity, caching, history) — stop and ask if not settled before this batch.
- Recommended order: 1 (blocking) → 2 → 3 → 4 → 6 → 5 (can follow 4 in parallel once the open questions are settled).

## Known pitfalls
- **No CDN** (project rule) — all PDF/icon rendering must be self-hosted, as KaTeX (`backend/static/vendor/katex`) already is.
- **Do not duplicate**: the `::` hierarchy (already in `routes_decks.py`), the subject→PDF matching (`source_matcher.py`), the Infercom call pattern (`explain.py`) — all to be reused, never rewritten.
- **`curl` broken on the phone's Termux** — not relevant here (no Termux script in this plan), but a reminder if a future batch adds one.
- **Never a `Co-Authored-By` trailer** (project rule).

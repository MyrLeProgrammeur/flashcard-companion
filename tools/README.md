# tools/

Code generators. Each one derives a Kotlin source file from the legacy backend,
so the app and the backend cannot drift apart on the things that must match
exactly. All three are idempotent — running them on a clean tree produces no
diff.

| script | generates | from |
|---|---|---|
| `gen_strings.js` | `ui/Strings.kt` | `backend/static/i18n.js` (FR/EN table) |
| `gen_prompts.py` | `ai/Prompts.kt` | `backend/explain.py`, `backend/api/routes_pdf_help.py` |
| `gen_srs_tests.py` | `SchedulerParityTest.kt` | the real output of `backend/srs.py` and `difflib` |

```bash
node tools/gen_strings.js
python tools/gen_prompts.py
cd backend && python ../tools/gen_srs_tests.py   # needs the backend's deps on sys.path
```

`gen_prompts.py` exists because the prompts are the product: they are long,
accented French with LaTeX backslashes, and hand-transcribing them silently
degrades the model's instructions.

`gen_srs_tests.py` exists because two details of the Python original do not
survive a naive port — `round()` is round-half-to-**even**, and
`difflib.ratio()` is Ratcliff/Obershelp rather than Levenshtein. Both would
otherwise fail quietly, as a card scheduled a day off or a source PDF that stops
matching.

"""Generate a Kotlin differential test from the Python scheduler's real output.

Every expected value below is produced by running the actual backend `srs.py`
and `difflib`, so the Kotlin port is checked against the original rather than
against my reading of it.
"""
import sys
import pathlib
import difflib
from datetime import datetime, timezone

BACKEND = pathlib.Path(__file__).resolve().parent.parent / "backend"
sys.path.insert(0, str(BACKEND))

import srs  # noqa: E402

# `_interval_label` lives with the deck routes, not in srs.py.
_ast_src = (BACKEND / "api" / "routes_decks.py").read_text(encoding="utf-8")
_ns: dict = {}
exec(compile(_ast_src.split("def _rating_previews")[0], "routes_decks", "exec"), _ns)
interval_label = _ns["_interval_label"]

OUT = (
    pathlib.Path(__file__).resolve().parent.parent
    / "android/app/src/test/java/com/matheo/flashcardcompanion/SchedulerParityTest.kt"
)

NOW = datetime(2026, 1, 1, 12, 0, 0, tzinfo=timezone.utc)

# (reps, interval_days, ease_factor) x quality, over the interesting shapes:
# fresh cards, the reps==2 hard-coded 6.0 step, mature cards, and lapses.
STATES = [
    (0, 0.0, 2.5),
    (1, 1.0, 2.5),
    (1, 3.0, 2.5),
    (1, 7.0, 2.5),
    (2, 6.0, 2.5),
    (2, 6.0, 1.3),
    (3, 6.0, 2.5),
    (3, 10.0, 2.36),
    (5, 30.0, 2.9),
    (8, 200.0, 3.1),
    (2, 1.0, 2.5),   # 1.0 * 2.5 = 2.5 -> banker's rounding gives 2, not 3
    (4, 0.5, 1.3),
    (0, 0.0, 1.3),
]
QUALITIES = [0, 1, 2, 3, 4, 5]

SETTINGS = [
    ("default", srs.SrsSettings()),
    ("tuned", srs.SrsSettings(again_days=0.5, hard_days=2.0, good_days=4.0,
                              easy_days=10.0, easy_bonus=1.5)),
]

rows = []
for sname, settings in SETTINGS:
    for reps, ivl, ef in STATES:
        for q in QUALITIES:
            state = srs.CardState(reps=reps, interval_days=ivl, ease_factor=ef, due_at=NOW)
            out = srs.review(state, q, NOW, settings)
            rows.append((sname, reps, ivl, ef, q, out.reps, out.interval_days, out.ease_factor))

labels = []
for days in [0.0, 0.4, 0.5, 1.0, 2.5, 3.0, 7.0, 29.0, 30.0, 44.0, 45.0, 75.0,
             364.0, 365.0, 547.0, 730.0, 1000.0]:
    labels.append((days, interval_label(days)))

# difflib parity: real deck segments against real course filenames.
TERMS = ["Functional Analysis", "Math ML DL Pauwels", "Time Series", "M1",
         "Topological and Metric Spaces", "Foundations of ML", "Martingale"]
CANDS = ["Chapter1", "functional_analysis", "Functional Analysis", "syllabus",
         "Foundations_of_ML", "Time_Series_2024", "martingale-notes", "Cours",
         "TD1", "Final_Exam_2022"]
sims = [(a, b, difflib.SequenceMatcher(None, a.lower(), b.lower()).ratio())
        for a in TERMS for b in CANDS]

print(f"{len(rows)} scheduler cases, {len(labels)} labels, {len(sims)} similarity pairs")


def kd(x):
    return repr(float(x))


lines = [
    "package com.matheo.flashcardcompanion",
    "",
    "import com.matheo.flashcardcompanion.data.Similarity",
    "import com.matheo.flashcardcompanion.srs.CardState",
    "import com.matheo.flashcardcompanion.srs.SrsSettings",
    "import com.matheo.flashcardcompanion.srs.intervalLabel",
    "import com.matheo.flashcardcompanion.srs.review",
    "import org.junit.Assert.assertEquals",
    "import org.junit.Test",
    "import java.time.Instant",
    "",
    "/**",
    " * Differential test against the Python backend this app replaces.",
    " *",
    " * Every expected value here was produced by running the original `srs.py`",
    " * and `difflib` (see tools/gen_srs_tests.py), not written by hand, so a",
    " * drift in the port shows up as a failure rather than as a card scheduled a",
    " * day off forever.",
    " */",
    "class SchedulerParityTest {",
    "",
    "    private val now: Instant = Instant.parse(\"2026-01-01T12:00:00Z\")",
    "",
    "    private val defaultSettings = SrsSettings()",
    "    private val tunedSettings = SrsSettings(",
    "        againDays = 0.5, hardDays = 2.0, goodDays = 4.0,",
    "        easyDays = 10.0, easyBonus = 1.5,",
    "    )",
    "",
    "    @Test",
    "    fun `scheduler matches the Python implementation`() {",
]

for sname, reps, ivl, ef, q, ereps, eivl, eef in rows:
    settings_ref = "defaultSettings" if sname == "default" else "tunedSettings"
    label = f"{sname} reps={reps} ivl={ivl} ef={ef} q={q}"
    lines.append(
        f"        review(CardState({reps}, {kd(ivl)}, {kd(ef)}, now), {q}, now, {settings_ref})"
        f".let {{"
    )
    lines.append(f"            assertEquals(\"reps [{label}]\", {ereps}, it.reps)")
    lines.append(
        f"            assertEquals(\"interval [{label}]\", {kd(eivl)}, it.intervalDays, 1e-9)"
    )
    lines.append(
        f"            assertEquals(\"ease [{label}]\", {kd(eef)}, it.easeFactor, 1e-9)"
    )
    lines.append("        }")

lines += [
    "    }",
    "",
    "    @Test",
    "    fun `interval labels match the Python implementation`() {",
]
for days, label in labels:
    lines.append(
        f"        assertEquals(\"days={days}\", \"{label}\", intervalLabel({kd(days)}))"
    )
lines += [
    "    }",
    "",
    "    @Test",
    "    fun `similarity matches Python difflib ratio`() {",
]
for a, b, r in sims:
    lines.append(
        f"        assertEquals(\"{a} vs {b}\", {repr(r)}, Similarity.ratio(\"{a}\", \"{b}\"), 1e-12)"
    )
lines += ["    }", "}", ""]

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text("\n".join(lines), encoding="utf-8")
print(f"wrote {OUT} ({len(lines)} lines)")

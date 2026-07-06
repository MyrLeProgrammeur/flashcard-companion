"""
Minimal SM-2 spaced-repetition scheduler. Pure functions, no I/O — state
lives in srs_store.py, keyed by the flashcard-pipeline's stable card GUID.

Configurable knobs (see docs/plans/settings-notifications-stats.md, B2) are
the 4 graduated first-pass intervals (Again/Hard/Good/Easy) plus an Easy
bonus multiplier for mature cards. They are persisted in the `settings`
table (srs_store.py) and injected via SrsSettings — never hardcoded here.
"""
from dataclasses import dataclass, fields
from datetime import datetime, timedelta

# UI rating buttons -> SM-2 quality scale (0-5)
QUALITY_AGAIN = 1
QUALITY_HARD = 3
QUALITY_GOOD = 4
QUALITY_EASY = 5

# Single source of truth for defaults, shared by srs_store.get_settings()
# so a fresh DB (no `settings` rows yet) reproduces this exact behavior.
# NOTE: this dict also backs non-SM-2 knobs stored in the same `settings`
# table (e.g. `notify_hour`, Batch 7) — not every key here is an SrsSettings
# field, see settings_from_dict below.
DEFAULT_SETTINGS = {
    "again_days": 0.0,
    "hard_days": 1.0,
    "good_days": 3.0,
    "easy_days": 7.0,
    "easy_bonus": 1.3,
    "notify_hour": 9.0,
}


@dataclass
class SrsSettings:
    again_days: float = DEFAULT_SETTINGS["again_days"]
    hard_days: float = DEFAULT_SETTINGS["hard_days"]
    good_days: float = DEFAULT_SETTINGS["good_days"]
    easy_days: float = DEFAULT_SETTINGS["easy_days"]
    easy_bonus: float = DEFAULT_SETTINGS["easy_bonus"]


def settings_from_dict(d: dict) -> SrsSettings:
    """Build the SM-2-only settings object from the full settings dict.
    Filters by SrsSettings' own fields (not DEFAULT_SETTINGS' keys), since
    the `settings` table also holds non-SM-2 knobs (e.g. notify_hour) that
    SrsSettings doesn't accept as constructor arguments."""
    field_names = {f.name for f in fields(SrsSettings)}
    return SrsSettings(**{k: d[k] for k in field_names if k in d})


@dataclass
class CardState:
    reps: int = 0
    interval_days: float = 0.0
    ease_factor: float = 2.5
    due_at: datetime | None = None
    last_reviewed_at: datetime | None = None


def review(
    card_state: CardState,
    quality: int,
    now: datetime,
    settings: SrsSettings | None = None,
) -> CardState:
    """quality in 0..5 (Again=1, Hard=3, Good=4, Easy=5).

    Intervals for the very first pass (reps 0 -> 1) are graduated per rating
    (settings.hard_days/good_days/easy_days) so Again/Hard/Good/Easy diverge
    on a fresh card instead of all collapsing to "1 day" (see plan's
    "degenerate '1 day' bug"). Beyond that, the SM-2 shape (fixed 2nd interval,
    interval*ease afterwards) is unchanged, with an Easy bonus multiplier
    applied on mature cards.
    """
    settings = settings or SrsSettings()

    if quality < 3:
        reps = 0
        interval_days = settings.again_days
    else:
        reps = card_state.reps + 1
        if reps == 1:
            if quality == QUALITY_HARD:
                interval_days = settings.hard_days
            elif quality == QUALITY_EASY:
                interval_days = settings.easy_days
            else:  # QUALITY_GOOD (and any other quality >= 3 that isn't Hard/Easy)
                interval_days = settings.good_days
        elif reps == 2:
            interval_days = 6.0
            if quality == QUALITY_EASY:
                interval_days *= settings.easy_bonus
        else:
            interval_days = card_state.interval_days * card_state.ease_factor
            if quality == QUALITY_EASY:
                interval_days *= settings.easy_bonus
        interval_days = round(interval_days)

    ease_factor = card_state.ease_factor + (
        0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)
    )
    ease_factor = max(1.3, ease_factor)

    due_at = now + timedelta(days=interval_days)

    return CardState(
        reps=reps,
        interval_days=interval_days,
        ease_factor=ease_factor,
        due_at=due_at,
        last_reviewed_at=now,
    )

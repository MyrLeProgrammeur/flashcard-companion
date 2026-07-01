"""
Minimal SM-2 spaced-repetition scheduler. Pure functions, no I/O — state
lives in srs_store.py, keyed by the flashcard-pipeline's stable card GUID.
"""
from dataclasses import dataclass
from datetime import datetime, timedelta

# UI rating buttons -> SM-2 quality scale (0-5)
QUALITY_AGAIN = 1
QUALITY_HARD = 3
QUALITY_GOOD = 4
QUALITY_EASY = 5


@dataclass
class CardState:
    reps: int = 0
    interval_days: float = 0.0
    ease_factor: float = 2.5
    due_at: datetime | None = None
    last_reviewed_at: datetime | None = None


def review(card_state: CardState, quality: int, now: datetime) -> CardState:
    """quality in 0..5 (Again=1, Hard=3, Good=4, Easy=5)."""
    if quality < 3:
        reps = 0
        interval_days = 1.0
    else:
        reps = card_state.reps + 1
        if reps == 1:
            interval_days = 1.0
        elif reps == 2:
            interval_days = 6.0
        else:
            interval_days = round(card_state.interval_days * card_state.ease_factor)

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

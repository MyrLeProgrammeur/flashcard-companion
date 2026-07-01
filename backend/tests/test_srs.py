import sys
from datetime import datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from srs import CardState, SrsSettings, review  # noqa: E402


def test_fresh_card_again_uses_again_days_default():
    state = CardState()
    now = datetime(2026, 1, 1)
    new_state = review(state, quality=1, now=now)
    assert new_state.reps == 0
    assert new_state.interval_days == 0.0
    assert new_state.due_at == now + timedelta(days=0)


def test_fresh_card_hard_good_easy_diverge_on_first_pass():
    """Fixes the degenerate '1 j for everything' bug: Again/Hard/Good/Easy
    must give 4 distinct intervals on a brand new card (reps=0)."""
    now = datetime(2026, 1, 1)
    again = review(CardState(), quality=1, now=now).interval_days
    hard = review(CardState(), quality=3, now=now).interval_days
    good = review(CardState(), quality=4, now=now).interval_days
    easy = review(CardState(), quality=5, now=now).interval_days

    assert len({again, hard, good, easy}) == 4
    assert (again, hard, good, easy) == (0.0, 1.0, 3.0, 7.0)


def test_second_review_good_sets_six_day_interval():
    state = CardState(reps=1, interval_days=1.0, ease_factor=2.5)
    now = datetime(2026, 1, 2)
    new_state = review(state, quality=4, now=now)
    assert new_state.reps == 2
    assert new_state.interval_days == 6.0


def test_third_review_multiplies_by_ease_factor():
    state = CardState(reps=2, interval_days=6.0, ease_factor=2.5)
    now = datetime(2026, 1, 8)
    new_state = review(state, quality=4, now=now)
    assert new_state.reps == 3
    assert new_state.interval_days == round(6.0 * 2.5)


def test_easy_bonus_multiplies_mature_interval():
    state = CardState(reps=2, interval_days=6.0, ease_factor=2.5)
    now = datetime(2026, 1, 8)
    plain = review(state, quality=4, now=now).interval_days
    with_bonus = review(state, quality=5, now=now).interval_days
    assert with_bonus > plain
    assert with_bonus == round(6.0 * 2.5 * 1.3)


def test_again_resets_reps_and_interval():
    state = CardState(reps=5, interval_days=30.0, ease_factor=2.8)
    now = datetime(2026, 1, 1)
    new_state = review(state, quality=1, now=now)
    assert new_state.reps == 0
    assert new_state.interval_days == 0.0
    assert new_state.due_at == now


def test_ease_factor_never_drops_below_1_3():
    state = CardState(reps=3, interval_days=10.0, ease_factor=1.3)
    now = datetime(2026, 1, 1)
    new_state = review(state, quality=1, now=now)
    assert new_state.ease_factor >= 1.3


def test_easy_increases_ease_factor():
    state = CardState(reps=3, interval_days=10.0, ease_factor=2.5)
    now = datetime(2026, 1, 1)
    new_state = review(state, quality=5, now=now)
    assert new_state.ease_factor > 2.5


def test_custom_settings_change_first_pass_intervals():
    settings = SrsSettings(again_days=0.0, hard_days=2.0, good_days=4.0, easy_days=10.0, easy_bonus=1.5)
    now = datetime(2026, 1, 1)
    hard = review(CardState(), quality=3, now=now, settings=settings).interval_days
    good = review(CardState(), quality=4, now=now, settings=settings).interval_days
    easy = review(CardState(), quality=5, now=now, settings=settings).interval_days
    assert (hard, good, easy) == (2.0, 4.0, 10.0)

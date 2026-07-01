import sys
from datetime import datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from srs import CardState, review  # noqa: E402


def test_first_review_good_sets_one_day_interval():
    state = CardState()
    now = datetime(2026, 1, 1)
    new_state = review(state, quality=4, now=now)
    assert new_state.reps == 1
    assert new_state.interval_days == 1.0
    assert new_state.due_at == now + timedelta(days=1)


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


def test_again_resets_reps_and_interval():
    state = CardState(reps=5, interval_days=30.0, ease_factor=2.8)
    now = datetime(2026, 1, 1)
    new_state = review(state, quality=1, now=now)
    assert new_state.reps == 0
    assert new_state.interval_days == 1.0
    assert new_state.due_at == now + timedelta(days=1)


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

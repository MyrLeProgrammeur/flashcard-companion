package com.matheo.flashcardcompanion.srs

import java.time.Instant

/**
 * SM-2 scheduler — a deliberate 1:1 port of the Python backend's `srs.py`.
 *
 * Pure functions over [CardState]; nothing here touches storage. Two details
 * are easy to get wrong and are load-bearing, so they are spelled out:
 *
 *  - Python's `round()` is round-half-to-EVEN, not half-away-from-zero. An
 *    interval of 2.5 days rounds to 2, not 3. [bankersRound] is `Math.rint`,
 *    which matches; `roundToInt()` would silently drift a card's whole future.
 *  - the Again branch stores the configured interval *unrounded*, so a
 *    fractional `againDays` (e.g. 0.5 = come back in 12 h) survives.
 */

const val QUALITY_AGAIN = 1
const val QUALITY_HARD = 3
const val QUALITY_GOOD = 4
const val QUALITY_EASY = 5

data class SrsSettings(
    val againDays: Double = 0.0,
    val hardDays: Double = 1.0,
    val goodDays: Double = 3.0,
    val easyDays: Double = 7.0,
    val easyBonus: Double = 1.3,
)

data class CardState(
    val reps: Int = 0,
    val intervalDays: Double = 0.0,
    val easeFactor: Double = 2.5,
    /**
     * Epoch, not `null` and not "now", for a card that has never been seen:
     * it must read as due whatever instant the caller captured, and it must
     * sort ahead of every card that has a real due date.
     */
    val dueAt: Instant? = Instant.EPOCH,
    val lastReviewedAt: Instant? = null,
)

/** Python `round()`: half-to-even. */
private fun bankersRound(x: Double): Double = Math.rint(x)

fun review(
    state: CardState,
    quality: Int,
    now: Instant,
    settings: SrsSettings = SrsSettings(),
): CardState {
    val reps: Int
    val intervalDays: Double

    if (quality < 3) {
        // A lapse resets the card completely; the previous interval is dropped
        // rather than scaled, and the configured value is kept unrounded.
        reps = 0
        intervalDays = settings.againDays
    } else {
        reps = state.reps + 1
        val raw = when {
            reps == 1 -> when (quality) {
                QUALITY_HARD -> settings.hardDays
                QUALITY_EASY -> settings.easyDays
                else -> settings.goodDays
            }
            reps == 2 -> 6.0 * if (quality == QUALITY_EASY) settings.easyBonus else 1.0
            // Note: the *old* ease factor drives the interval — the updated one
            // below only applies from the next review on.
            else -> state.intervalDays * state.easeFactor *
                if (quality == QUALITY_EASY) settings.easyBonus else 1.0
        }
        intervalDays = bankersRound(raw)
    }

    // Applied on every path, lapses included. Floored at 1.3, deliberately
    // unbounded above.
    val easeFactor = maxOf(
        1.3,
        state.easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)),
    )

    return CardState(
        reps = reps,
        intervalDays = intervalDays,
        easeFactor = easeFactor,
        dueAt = now.plusMillis((intervalDays * 86_400_000.0).toLong()),
        lastReviewedAt = now,
    )
}

/** French interval label shown on a rating button ("<1 j", "3 j", "2 mois", "2 ans"). */
fun intervalLabel(days: Double): String = when {
    days < 1 -> "<1 j"
    days < 30 -> "${bankersRound(days).toLong()} j"
    days < 365 -> "${bankersRound(days / 30).toLong()} mois"
    else -> {
        val years = bankersRound(days / 365).toLong()
        "$years an" + if (years > 1) "s" else ""
    }
}

/**
 * The four button labels, derived by running the real [review] against the
 * card's current state — never by a parallel formula, so they cannot drift
 * out of step with what the button actually does.
 */
fun ratingPreviews(state: CardState, now: Instant, settings: SrsSettings): Map<String, String> = mapOf(
    "again" to intervalLabel(review(state, QUALITY_AGAIN, now, settings).intervalDays),
    "hard" to intervalLabel(review(state, QUALITY_HARD, now, settings).intervalDays),
    "good" to intervalLabel(review(state, QUALITY_GOOD, now, settings).intervalDays),
    "easy" to intervalLabel(review(state, QUALITY_EASY, now, settings).intervalDays),
)

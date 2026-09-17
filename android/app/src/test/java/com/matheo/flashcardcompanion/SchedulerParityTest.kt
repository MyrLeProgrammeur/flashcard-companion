package com.matheo.flashcardcompanion

import com.matheo.flashcardcompanion.data.Similarity
import com.matheo.flashcardcompanion.srs.CardState
import com.matheo.flashcardcompanion.srs.SrsSettings
import com.matheo.flashcardcompanion.srs.intervalLabel
import com.matheo.flashcardcompanion.srs.review
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Differential test against the Python backend this app replaces.
 *
 * Every expected value here was produced by running the original `srs.py`
 * and `difflib` (see tools/gen_srs_tests.py), not written by hand, so a
 * drift in the port shows up as a failure rather than as a card scheduled a
 * day off forever.
 */
class SchedulerParityTest {

    private val now: Instant = Instant.parse("2026-01-01T12:00:00Z")

    private val defaultSettings = SrsSettings()
    private val tunedSettings = SrsSettings(
        againDays = 0.5, hardDays = 2.0, goodDays = 4.0,
        easyDays = 10.0, easyBonus = 1.5,
    )

    @Test
    fun `scheduler matches the Python implementation`() {
        review(CardState(0, 0.0, 2.5, now), 0, now, defaultSettings).let {
            assertEquals("reps [default reps=0 ivl=0.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [default reps=0 ivl=0.0 ef=2.5 q=0]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=0 ivl=0.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 2.5, now), 1, now, defaultSettings).let {
            assertEquals("reps [default reps=0 ivl=0.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [default reps=0 ivl=0.0 ef=2.5 q=1]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=0 ivl=0.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 2.5, now), 2, now, defaultSettings).let {
            assertEquals("reps [default reps=0 ivl=0.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [default reps=0 ivl=0.0 ef=2.5 q=2]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=0 ivl=0.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 2.5, now), 3, now, defaultSettings).let {
            assertEquals("reps [default reps=0 ivl=0.0 ef=2.5 q=3]", 1, it.reps)
            assertEquals("interval [default reps=0 ivl=0.0 ef=2.5 q=3]", 1.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=0 ivl=0.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 2.5, now), 4, now, defaultSettings).let {
            assertEquals("reps [default reps=0 ivl=0.0 ef=2.5 q=4]", 1, it.reps)
            assertEquals("interval [default reps=0 ivl=0.0 ef=2.5 q=4]", 3.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=0 ivl=0.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 2.5, now), 5, now, defaultSettings).let {
            assertEquals("reps [default reps=0 ivl=0.0 ef=2.5 q=5]", 1, it.reps)
            assertEquals("interval [default reps=0 ivl=0.0 ef=2.5 q=5]", 7.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=0 ivl=0.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(1, 1.0, 2.5, now), 0, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=1.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [default reps=1 ivl=1.0 ef=2.5 q=0]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=1.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(1, 1.0, 2.5, now), 1, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=1.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [default reps=1 ivl=1.0 ef=2.5 q=1]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=1.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(1, 1.0, 2.5, now), 2, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=1.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [default reps=1 ivl=1.0 ef=2.5 q=2]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=1.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(1, 1.0, 2.5, now), 3, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=1.0 ef=2.5 q=3]", 2, it.reps)
            assertEquals("interval [default reps=1 ivl=1.0 ef=2.5 q=3]", 6.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=1.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(1, 1.0, 2.5, now), 4, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=1.0 ef=2.5 q=4]", 2, it.reps)
            assertEquals("interval [default reps=1 ivl=1.0 ef=2.5 q=4]", 6.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=1.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(1, 1.0, 2.5, now), 5, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=1.0 ef=2.5 q=5]", 2, it.reps)
            assertEquals("interval [default reps=1 ivl=1.0 ef=2.5 q=5]", 8.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=1.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(1, 3.0, 2.5, now), 0, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=3.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [default reps=1 ivl=3.0 ef=2.5 q=0]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=3.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(1, 3.0, 2.5, now), 1, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=3.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [default reps=1 ivl=3.0 ef=2.5 q=1]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=3.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(1, 3.0, 2.5, now), 2, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=3.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [default reps=1 ivl=3.0 ef=2.5 q=2]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=3.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(1, 3.0, 2.5, now), 3, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=3.0 ef=2.5 q=3]", 2, it.reps)
            assertEquals("interval [default reps=1 ivl=3.0 ef=2.5 q=3]", 6.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=3.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(1, 3.0, 2.5, now), 4, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=3.0 ef=2.5 q=4]", 2, it.reps)
            assertEquals("interval [default reps=1 ivl=3.0 ef=2.5 q=4]", 6.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=3.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(1, 3.0, 2.5, now), 5, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=3.0 ef=2.5 q=5]", 2, it.reps)
            assertEquals("interval [default reps=1 ivl=3.0 ef=2.5 q=5]", 8.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=3.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(1, 7.0, 2.5, now), 0, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=7.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [default reps=1 ivl=7.0 ef=2.5 q=0]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=7.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(1, 7.0, 2.5, now), 1, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=7.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [default reps=1 ivl=7.0 ef=2.5 q=1]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=7.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(1, 7.0, 2.5, now), 2, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=7.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [default reps=1 ivl=7.0 ef=2.5 q=2]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=7.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(1, 7.0, 2.5, now), 3, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=7.0 ef=2.5 q=3]", 2, it.reps)
            assertEquals("interval [default reps=1 ivl=7.0 ef=2.5 q=3]", 6.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=7.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(1, 7.0, 2.5, now), 4, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=7.0 ef=2.5 q=4]", 2, it.reps)
            assertEquals("interval [default reps=1 ivl=7.0 ef=2.5 q=4]", 6.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=7.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(1, 7.0, 2.5, now), 5, now, defaultSettings).let {
            assertEquals("reps [default reps=1 ivl=7.0 ef=2.5 q=5]", 2, it.reps)
            assertEquals("interval [default reps=1 ivl=7.0 ef=2.5 q=5]", 8.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=1 ivl=7.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 2.5, now), 0, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=6.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [default reps=2 ivl=6.0 ef=2.5 q=0]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=6.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 2.5, now), 1, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=6.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [default reps=2 ivl=6.0 ef=2.5 q=1]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=6.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 2.5, now), 2, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=6.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [default reps=2 ivl=6.0 ef=2.5 q=2]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=6.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 2.5, now), 3, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=6.0 ef=2.5 q=3]", 3, it.reps)
            assertEquals("interval [default reps=2 ivl=6.0 ef=2.5 q=3]", 15.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=6.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 2.5, now), 4, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=6.0 ef=2.5 q=4]", 3, it.reps)
            assertEquals("interval [default reps=2 ivl=6.0 ef=2.5 q=4]", 15.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=6.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 2.5, now), 5, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=6.0 ef=2.5 q=5]", 3, it.reps)
            assertEquals("interval [default reps=2 ivl=6.0 ef=2.5 q=5]", 20.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=6.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 1.3, now), 0, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=6.0 ef=1.3 q=0]", 0, it.reps)
            assertEquals("interval [default reps=2 ivl=6.0 ef=1.3 q=0]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=6.0 ef=1.3 q=0]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 1.3, now), 1, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=6.0 ef=1.3 q=1]", 0, it.reps)
            assertEquals("interval [default reps=2 ivl=6.0 ef=1.3 q=1]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=6.0 ef=1.3 q=1]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 1.3, now), 2, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=6.0 ef=1.3 q=2]", 0, it.reps)
            assertEquals("interval [default reps=2 ivl=6.0 ef=1.3 q=2]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=6.0 ef=1.3 q=2]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 1.3, now), 3, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=6.0 ef=1.3 q=3]", 3, it.reps)
            assertEquals("interval [default reps=2 ivl=6.0 ef=1.3 q=3]", 8.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=6.0 ef=1.3 q=3]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 1.3, now), 4, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=6.0 ef=1.3 q=4]", 3, it.reps)
            assertEquals("interval [default reps=2 ivl=6.0 ef=1.3 q=4]", 8.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=6.0 ef=1.3 q=4]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 1.3, now), 5, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=6.0 ef=1.3 q=5]", 3, it.reps)
            assertEquals("interval [default reps=2 ivl=6.0 ef=1.3 q=5]", 10.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=6.0 ef=1.3 q=5]", 1.4000000000000001, it.easeFactor, 1e-9)
        }
        review(CardState(3, 6.0, 2.5, now), 0, now, defaultSettings).let {
            assertEquals("reps [default reps=3 ivl=6.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [default reps=3 ivl=6.0 ef=2.5 q=0]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=3 ivl=6.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(3, 6.0, 2.5, now), 1, now, defaultSettings).let {
            assertEquals("reps [default reps=3 ivl=6.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [default reps=3 ivl=6.0 ef=2.5 q=1]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=3 ivl=6.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(3, 6.0, 2.5, now), 2, now, defaultSettings).let {
            assertEquals("reps [default reps=3 ivl=6.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [default reps=3 ivl=6.0 ef=2.5 q=2]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=3 ivl=6.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(3, 6.0, 2.5, now), 3, now, defaultSettings).let {
            assertEquals("reps [default reps=3 ivl=6.0 ef=2.5 q=3]", 4, it.reps)
            assertEquals("interval [default reps=3 ivl=6.0 ef=2.5 q=3]", 15.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=3 ivl=6.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(3, 6.0, 2.5, now), 4, now, defaultSettings).let {
            assertEquals("reps [default reps=3 ivl=6.0 ef=2.5 q=4]", 4, it.reps)
            assertEquals("interval [default reps=3 ivl=6.0 ef=2.5 q=4]", 15.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=3 ivl=6.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(3, 6.0, 2.5, now), 5, now, defaultSettings).let {
            assertEquals("reps [default reps=3 ivl=6.0 ef=2.5 q=5]", 4, it.reps)
            assertEquals("interval [default reps=3 ivl=6.0 ef=2.5 q=5]", 20.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=3 ivl=6.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(3, 10.0, 2.36, now), 0, now, defaultSettings).let {
            assertEquals("reps [default reps=3 ivl=10.0 ef=2.36 q=0]", 0, it.reps)
            assertEquals("interval [default reps=3 ivl=10.0 ef=2.36 q=0]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=3 ivl=10.0 ef=2.36 q=0]", 1.56, it.easeFactor, 1e-9)
        }
        review(CardState(3, 10.0, 2.36, now), 1, now, defaultSettings).let {
            assertEquals("reps [default reps=3 ivl=10.0 ef=2.36 q=1]", 0, it.reps)
            assertEquals("interval [default reps=3 ivl=10.0 ef=2.36 q=1]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=3 ivl=10.0 ef=2.36 q=1]", 1.8199999999999998, it.easeFactor, 1e-9)
        }
        review(CardState(3, 10.0, 2.36, now), 2, now, defaultSettings).let {
            assertEquals("reps [default reps=3 ivl=10.0 ef=2.36 q=2]", 0, it.reps)
            assertEquals("interval [default reps=3 ivl=10.0 ef=2.36 q=2]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=3 ivl=10.0 ef=2.36 q=2]", 2.04, it.easeFactor, 1e-9)
        }
        review(CardState(3, 10.0, 2.36, now), 3, now, defaultSettings).let {
            assertEquals("reps [default reps=3 ivl=10.0 ef=2.36 q=3]", 4, it.reps)
            assertEquals("interval [default reps=3 ivl=10.0 ef=2.36 q=3]", 24.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=3 ivl=10.0 ef=2.36 q=3]", 2.2199999999999998, it.easeFactor, 1e-9)
        }
        review(CardState(3, 10.0, 2.36, now), 4, now, defaultSettings).let {
            assertEquals("reps [default reps=3 ivl=10.0 ef=2.36 q=4]", 4, it.reps)
            assertEquals("interval [default reps=3 ivl=10.0 ef=2.36 q=4]", 24.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=3 ivl=10.0 ef=2.36 q=4]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(3, 10.0, 2.36, now), 5, now, defaultSettings).let {
            assertEquals("reps [default reps=3 ivl=10.0 ef=2.36 q=5]", 4, it.reps)
            assertEquals("interval [default reps=3 ivl=10.0 ef=2.36 q=5]", 31.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=3 ivl=10.0 ef=2.36 q=5]", 2.46, it.easeFactor, 1e-9)
        }
        review(CardState(5, 30.0, 2.9, now), 0, now, defaultSettings).let {
            assertEquals("reps [default reps=5 ivl=30.0 ef=2.9 q=0]", 0, it.reps)
            assertEquals("interval [default reps=5 ivl=30.0 ef=2.9 q=0]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=5 ivl=30.0 ef=2.9 q=0]", 2.1, it.easeFactor, 1e-9)
        }
        review(CardState(5, 30.0, 2.9, now), 1, now, defaultSettings).let {
            assertEquals("reps [default reps=5 ivl=30.0 ef=2.9 q=1]", 0, it.reps)
            assertEquals("interval [default reps=5 ivl=30.0 ef=2.9 q=1]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=5 ivl=30.0 ef=2.9 q=1]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(5, 30.0, 2.9, now), 2, now, defaultSettings).let {
            assertEquals("reps [default reps=5 ivl=30.0 ef=2.9 q=2]", 0, it.reps)
            assertEquals("interval [default reps=5 ivl=30.0 ef=2.9 q=2]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=5 ivl=30.0 ef=2.9 q=2]", 2.58, it.easeFactor, 1e-9)
        }
        review(CardState(5, 30.0, 2.9, now), 3, now, defaultSettings).let {
            assertEquals("reps [default reps=5 ivl=30.0 ef=2.9 q=3]", 6, it.reps)
            assertEquals("interval [default reps=5 ivl=30.0 ef=2.9 q=3]", 87.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=5 ivl=30.0 ef=2.9 q=3]", 2.76, it.easeFactor, 1e-9)
        }
        review(CardState(5, 30.0, 2.9, now), 4, now, defaultSettings).let {
            assertEquals("reps [default reps=5 ivl=30.0 ef=2.9 q=4]", 6, it.reps)
            assertEquals("interval [default reps=5 ivl=30.0 ef=2.9 q=4]", 87.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=5 ivl=30.0 ef=2.9 q=4]", 2.9, it.easeFactor, 1e-9)
        }
        review(CardState(5, 30.0, 2.9, now), 5, now, defaultSettings).let {
            assertEquals("reps [default reps=5 ivl=30.0 ef=2.9 q=5]", 6, it.reps)
            assertEquals("interval [default reps=5 ivl=30.0 ef=2.9 q=5]", 113.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=5 ivl=30.0 ef=2.9 q=5]", 3.0, it.easeFactor, 1e-9)
        }
        review(CardState(8, 200.0, 3.1, now), 0, now, defaultSettings).let {
            assertEquals("reps [default reps=8 ivl=200.0 ef=3.1 q=0]", 0, it.reps)
            assertEquals("interval [default reps=8 ivl=200.0 ef=3.1 q=0]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=8 ivl=200.0 ef=3.1 q=0]", 2.3000000000000003, it.easeFactor, 1e-9)
        }
        review(CardState(8, 200.0, 3.1, now), 1, now, defaultSettings).let {
            assertEquals("reps [default reps=8 ivl=200.0 ef=3.1 q=1]", 0, it.reps)
            assertEquals("interval [default reps=8 ivl=200.0 ef=3.1 q=1]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=8 ivl=200.0 ef=3.1 q=1]", 2.56, it.easeFactor, 1e-9)
        }
        review(CardState(8, 200.0, 3.1, now), 2, now, defaultSettings).let {
            assertEquals("reps [default reps=8 ivl=200.0 ef=3.1 q=2]", 0, it.reps)
            assertEquals("interval [default reps=8 ivl=200.0 ef=3.1 q=2]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=8 ivl=200.0 ef=3.1 q=2]", 2.7800000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(8, 200.0, 3.1, now), 3, now, defaultSettings).let {
            assertEquals("reps [default reps=8 ivl=200.0 ef=3.1 q=3]", 9, it.reps)
            assertEquals("interval [default reps=8 ivl=200.0 ef=3.1 q=3]", 620.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=8 ivl=200.0 ef=3.1 q=3]", 2.96, it.easeFactor, 1e-9)
        }
        review(CardState(8, 200.0, 3.1, now), 4, now, defaultSettings).let {
            assertEquals("reps [default reps=8 ivl=200.0 ef=3.1 q=4]", 9, it.reps)
            assertEquals("interval [default reps=8 ivl=200.0 ef=3.1 q=4]", 620.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=8 ivl=200.0 ef=3.1 q=4]", 3.1, it.easeFactor, 1e-9)
        }
        review(CardState(8, 200.0, 3.1, now), 5, now, defaultSettings).let {
            assertEquals("reps [default reps=8 ivl=200.0 ef=3.1 q=5]", 9, it.reps)
            assertEquals("interval [default reps=8 ivl=200.0 ef=3.1 q=5]", 806.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=8 ivl=200.0 ef=3.1 q=5]", 3.2, it.easeFactor, 1e-9)
        }
        review(CardState(2, 1.0, 2.5, now), 0, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=1.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [default reps=2 ivl=1.0 ef=2.5 q=0]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=1.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(2, 1.0, 2.5, now), 1, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=1.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [default reps=2 ivl=1.0 ef=2.5 q=1]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=1.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(2, 1.0, 2.5, now), 2, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=1.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [default reps=2 ivl=1.0 ef=2.5 q=2]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=1.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(2, 1.0, 2.5, now), 3, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=1.0 ef=2.5 q=3]", 3, it.reps)
            assertEquals("interval [default reps=2 ivl=1.0 ef=2.5 q=3]", 2.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=1.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(2, 1.0, 2.5, now), 4, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=1.0 ef=2.5 q=4]", 3, it.reps)
            assertEquals("interval [default reps=2 ivl=1.0 ef=2.5 q=4]", 2.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=1.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(2, 1.0, 2.5, now), 5, now, defaultSettings).let {
            assertEquals("reps [default reps=2 ivl=1.0 ef=2.5 q=5]", 3, it.reps)
            assertEquals("interval [default reps=2 ivl=1.0 ef=2.5 q=5]", 3.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=2 ivl=1.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(4, 0.5, 1.3, now), 0, now, defaultSettings).let {
            assertEquals("reps [default reps=4 ivl=0.5 ef=1.3 q=0]", 0, it.reps)
            assertEquals("interval [default reps=4 ivl=0.5 ef=1.3 q=0]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=4 ivl=0.5 ef=1.3 q=0]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(4, 0.5, 1.3, now), 1, now, defaultSettings).let {
            assertEquals("reps [default reps=4 ivl=0.5 ef=1.3 q=1]", 0, it.reps)
            assertEquals("interval [default reps=4 ivl=0.5 ef=1.3 q=1]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=4 ivl=0.5 ef=1.3 q=1]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(4, 0.5, 1.3, now), 2, now, defaultSettings).let {
            assertEquals("reps [default reps=4 ivl=0.5 ef=1.3 q=2]", 0, it.reps)
            assertEquals("interval [default reps=4 ivl=0.5 ef=1.3 q=2]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=4 ivl=0.5 ef=1.3 q=2]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(4, 0.5, 1.3, now), 3, now, defaultSettings).let {
            assertEquals("reps [default reps=4 ivl=0.5 ef=1.3 q=3]", 5, it.reps)
            assertEquals("interval [default reps=4 ivl=0.5 ef=1.3 q=3]", 1.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=4 ivl=0.5 ef=1.3 q=3]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(4, 0.5, 1.3, now), 4, now, defaultSettings).let {
            assertEquals("reps [default reps=4 ivl=0.5 ef=1.3 q=4]", 5, it.reps)
            assertEquals("interval [default reps=4 ivl=0.5 ef=1.3 q=4]", 1.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=4 ivl=0.5 ef=1.3 q=4]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(4, 0.5, 1.3, now), 5, now, defaultSettings).let {
            assertEquals("reps [default reps=4 ivl=0.5 ef=1.3 q=5]", 5, it.reps)
            assertEquals("interval [default reps=4 ivl=0.5 ef=1.3 q=5]", 1.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=4 ivl=0.5 ef=1.3 q=5]", 1.4000000000000001, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 1.3, now), 0, now, defaultSettings).let {
            assertEquals("reps [default reps=0 ivl=0.0 ef=1.3 q=0]", 0, it.reps)
            assertEquals("interval [default reps=0 ivl=0.0 ef=1.3 q=0]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=0 ivl=0.0 ef=1.3 q=0]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 1.3, now), 1, now, defaultSettings).let {
            assertEquals("reps [default reps=0 ivl=0.0 ef=1.3 q=1]", 0, it.reps)
            assertEquals("interval [default reps=0 ivl=0.0 ef=1.3 q=1]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=0 ivl=0.0 ef=1.3 q=1]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 1.3, now), 2, now, defaultSettings).let {
            assertEquals("reps [default reps=0 ivl=0.0 ef=1.3 q=2]", 0, it.reps)
            assertEquals("interval [default reps=0 ivl=0.0 ef=1.3 q=2]", 0.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=0 ivl=0.0 ef=1.3 q=2]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 1.3, now), 3, now, defaultSettings).let {
            assertEquals("reps [default reps=0 ivl=0.0 ef=1.3 q=3]", 1, it.reps)
            assertEquals("interval [default reps=0 ivl=0.0 ef=1.3 q=3]", 1.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=0 ivl=0.0 ef=1.3 q=3]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 1.3, now), 4, now, defaultSettings).let {
            assertEquals("reps [default reps=0 ivl=0.0 ef=1.3 q=4]", 1, it.reps)
            assertEquals("interval [default reps=0 ivl=0.0 ef=1.3 q=4]", 3.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=0 ivl=0.0 ef=1.3 q=4]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 1.3, now), 5, now, defaultSettings).let {
            assertEquals("reps [default reps=0 ivl=0.0 ef=1.3 q=5]", 1, it.reps)
            assertEquals("interval [default reps=0 ivl=0.0 ef=1.3 q=5]", 7.0, it.intervalDays, 1e-9)
            assertEquals("ease [default reps=0 ivl=0.0 ef=1.3 q=5]", 1.4000000000000001, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 2.5, now), 0, now, tunedSettings).let {
            assertEquals("reps [tuned reps=0 ivl=0.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [tuned reps=0 ivl=0.0 ef=2.5 q=0]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=0 ivl=0.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 2.5, now), 1, now, tunedSettings).let {
            assertEquals("reps [tuned reps=0 ivl=0.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [tuned reps=0 ivl=0.0 ef=2.5 q=1]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=0 ivl=0.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 2.5, now), 2, now, tunedSettings).let {
            assertEquals("reps [tuned reps=0 ivl=0.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [tuned reps=0 ivl=0.0 ef=2.5 q=2]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=0 ivl=0.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 2.5, now), 3, now, tunedSettings).let {
            assertEquals("reps [tuned reps=0 ivl=0.0 ef=2.5 q=3]", 1, it.reps)
            assertEquals("interval [tuned reps=0 ivl=0.0 ef=2.5 q=3]", 2.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=0 ivl=0.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 2.5, now), 4, now, tunedSettings).let {
            assertEquals("reps [tuned reps=0 ivl=0.0 ef=2.5 q=4]", 1, it.reps)
            assertEquals("interval [tuned reps=0 ivl=0.0 ef=2.5 q=4]", 4.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=0 ivl=0.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 2.5, now), 5, now, tunedSettings).let {
            assertEquals("reps [tuned reps=0 ivl=0.0 ef=2.5 q=5]", 1, it.reps)
            assertEquals("interval [tuned reps=0 ivl=0.0 ef=2.5 q=5]", 10.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=0 ivl=0.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(1, 1.0, 2.5, now), 0, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=1.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [tuned reps=1 ivl=1.0 ef=2.5 q=0]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=1.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(1, 1.0, 2.5, now), 1, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=1.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [tuned reps=1 ivl=1.0 ef=2.5 q=1]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=1.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(1, 1.0, 2.5, now), 2, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=1.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [tuned reps=1 ivl=1.0 ef=2.5 q=2]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=1.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(1, 1.0, 2.5, now), 3, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=1.0 ef=2.5 q=3]", 2, it.reps)
            assertEquals("interval [tuned reps=1 ivl=1.0 ef=2.5 q=3]", 6.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=1.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(1, 1.0, 2.5, now), 4, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=1.0 ef=2.5 q=4]", 2, it.reps)
            assertEquals("interval [tuned reps=1 ivl=1.0 ef=2.5 q=4]", 6.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=1.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(1, 1.0, 2.5, now), 5, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=1.0 ef=2.5 q=5]", 2, it.reps)
            assertEquals("interval [tuned reps=1 ivl=1.0 ef=2.5 q=5]", 9.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=1.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(1, 3.0, 2.5, now), 0, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=3.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [tuned reps=1 ivl=3.0 ef=2.5 q=0]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=3.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(1, 3.0, 2.5, now), 1, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=3.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [tuned reps=1 ivl=3.0 ef=2.5 q=1]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=3.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(1, 3.0, 2.5, now), 2, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=3.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [tuned reps=1 ivl=3.0 ef=2.5 q=2]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=3.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(1, 3.0, 2.5, now), 3, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=3.0 ef=2.5 q=3]", 2, it.reps)
            assertEquals("interval [tuned reps=1 ivl=3.0 ef=2.5 q=3]", 6.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=3.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(1, 3.0, 2.5, now), 4, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=3.0 ef=2.5 q=4]", 2, it.reps)
            assertEquals("interval [tuned reps=1 ivl=3.0 ef=2.5 q=4]", 6.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=3.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(1, 3.0, 2.5, now), 5, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=3.0 ef=2.5 q=5]", 2, it.reps)
            assertEquals("interval [tuned reps=1 ivl=3.0 ef=2.5 q=5]", 9.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=3.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(1, 7.0, 2.5, now), 0, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=7.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [tuned reps=1 ivl=7.0 ef=2.5 q=0]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=7.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(1, 7.0, 2.5, now), 1, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=7.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [tuned reps=1 ivl=7.0 ef=2.5 q=1]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=7.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(1, 7.0, 2.5, now), 2, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=7.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [tuned reps=1 ivl=7.0 ef=2.5 q=2]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=7.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(1, 7.0, 2.5, now), 3, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=7.0 ef=2.5 q=3]", 2, it.reps)
            assertEquals("interval [tuned reps=1 ivl=7.0 ef=2.5 q=3]", 6.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=7.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(1, 7.0, 2.5, now), 4, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=7.0 ef=2.5 q=4]", 2, it.reps)
            assertEquals("interval [tuned reps=1 ivl=7.0 ef=2.5 q=4]", 6.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=7.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(1, 7.0, 2.5, now), 5, now, tunedSettings).let {
            assertEquals("reps [tuned reps=1 ivl=7.0 ef=2.5 q=5]", 2, it.reps)
            assertEquals("interval [tuned reps=1 ivl=7.0 ef=2.5 q=5]", 9.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=1 ivl=7.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 2.5, now), 0, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=6.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [tuned reps=2 ivl=6.0 ef=2.5 q=0]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=6.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 2.5, now), 1, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=6.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [tuned reps=2 ivl=6.0 ef=2.5 q=1]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=6.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 2.5, now), 2, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=6.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [tuned reps=2 ivl=6.0 ef=2.5 q=2]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=6.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 2.5, now), 3, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=6.0 ef=2.5 q=3]", 3, it.reps)
            assertEquals("interval [tuned reps=2 ivl=6.0 ef=2.5 q=3]", 15.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=6.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 2.5, now), 4, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=6.0 ef=2.5 q=4]", 3, it.reps)
            assertEquals("interval [tuned reps=2 ivl=6.0 ef=2.5 q=4]", 15.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=6.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 2.5, now), 5, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=6.0 ef=2.5 q=5]", 3, it.reps)
            assertEquals("interval [tuned reps=2 ivl=6.0 ef=2.5 q=5]", 22.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=6.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 1.3, now), 0, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=6.0 ef=1.3 q=0]", 0, it.reps)
            assertEquals("interval [tuned reps=2 ivl=6.0 ef=1.3 q=0]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=6.0 ef=1.3 q=0]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 1.3, now), 1, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=6.0 ef=1.3 q=1]", 0, it.reps)
            assertEquals("interval [tuned reps=2 ivl=6.0 ef=1.3 q=1]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=6.0 ef=1.3 q=1]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 1.3, now), 2, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=6.0 ef=1.3 q=2]", 0, it.reps)
            assertEquals("interval [tuned reps=2 ivl=6.0 ef=1.3 q=2]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=6.0 ef=1.3 q=2]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 1.3, now), 3, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=6.0 ef=1.3 q=3]", 3, it.reps)
            assertEquals("interval [tuned reps=2 ivl=6.0 ef=1.3 q=3]", 8.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=6.0 ef=1.3 q=3]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 1.3, now), 4, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=6.0 ef=1.3 q=4]", 3, it.reps)
            assertEquals("interval [tuned reps=2 ivl=6.0 ef=1.3 q=4]", 8.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=6.0 ef=1.3 q=4]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(2, 6.0, 1.3, now), 5, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=6.0 ef=1.3 q=5]", 3, it.reps)
            assertEquals("interval [tuned reps=2 ivl=6.0 ef=1.3 q=5]", 12.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=6.0 ef=1.3 q=5]", 1.4000000000000001, it.easeFactor, 1e-9)
        }
        review(CardState(3, 6.0, 2.5, now), 0, now, tunedSettings).let {
            assertEquals("reps [tuned reps=3 ivl=6.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [tuned reps=3 ivl=6.0 ef=2.5 q=0]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=3 ivl=6.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(3, 6.0, 2.5, now), 1, now, tunedSettings).let {
            assertEquals("reps [tuned reps=3 ivl=6.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [tuned reps=3 ivl=6.0 ef=2.5 q=1]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=3 ivl=6.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(3, 6.0, 2.5, now), 2, now, tunedSettings).let {
            assertEquals("reps [tuned reps=3 ivl=6.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [tuned reps=3 ivl=6.0 ef=2.5 q=2]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=3 ivl=6.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(3, 6.0, 2.5, now), 3, now, tunedSettings).let {
            assertEquals("reps [tuned reps=3 ivl=6.0 ef=2.5 q=3]", 4, it.reps)
            assertEquals("interval [tuned reps=3 ivl=6.0 ef=2.5 q=3]", 15.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=3 ivl=6.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(3, 6.0, 2.5, now), 4, now, tunedSettings).let {
            assertEquals("reps [tuned reps=3 ivl=6.0 ef=2.5 q=4]", 4, it.reps)
            assertEquals("interval [tuned reps=3 ivl=6.0 ef=2.5 q=4]", 15.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=3 ivl=6.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(3, 6.0, 2.5, now), 5, now, tunedSettings).let {
            assertEquals("reps [tuned reps=3 ivl=6.0 ef=2.5 q=5]", 4, it.reps)
            assertEquals("interval [tuned reps=3 ivl=6.0 ef=2.5 q=5]", 22.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=3 ivl=6.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(3, 10.0, 2.36, now), 0, now, tunedSettings).let {
            assertEquals("reps [tuned reps=3 ivl=10.0 ef=2.36 q=0]", 0, it.reps)
            assertEquals("interval [tuned reps=3 ivl=10.0 ef=2.36 q=0]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=3 ivl=10.0 ef=2.36 q=0]", 1.56, it.easeFactor, 1e-9)
        }
        review(CardState(3, 10.0, 2.36, now), 1, now, tunedSettings).let {
            assertEquals("reps [tuned reps=3 ivl=10.0 ef=2.36 q=1]", 0, it.reps)
            assertEquals("interval [tuned reps=3 ivl=10.0 ef=2.36 q=1]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=3 ivl=10.0 ef=2.36 q=1]", 1.8199999999999998, it.easeFactor, 1e-9)
        }
        review(CardState(3, 10.0, 2.36, now), 2, now, tunedSettings).let {
            assertEquals("reps [tuned reps=3 ivl=10.0 ef=2.36 q=2]", 0, it.reps)
            assertEquals("interval [tuned reps=3 ivl=10.0 ef=2.36 q=2]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=3 ivl=10.0 ef=2.36 q=2]", 2.04, it.easeFactor, 1e-9)
        }
        review(CardState(3, 10.0, 2.36, now), 3, now, tunedSettings).let {
            assertEquals("reps [tuned reps=3 ivl=10.0 ef=2.36 q=3]", 4, it.reps)
            assertEquals("interval [tuned reps=3 ivl=10.0 ef=2.36 q=3]", 24.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=3 ivl=10.0 ef=2.36 q=3]", 2.2199999999999998, it.easeFactor, 1e-9)
        }
        review(CardState(3, 10.0, 2.36, now), 4, now, tunedSettings).let {
            assertEquals("reps [tuned reps=3 ivl=10.0 ef=2.36 q=4]", 4, it.reps)
            assertEquals("interval [tuned reps=3 ivl=10.0 ef=2.36 q=4]", 24.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=3 ivl=10.0 ef=2.36 q=4]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(3, 10.0, 2.36, now), 5, now, tunedSettings).let {
            assertEquals("reps [tuned reps=3 ivl=10.0 ef=2.36 q=5]", 4, it.reps)
            assertEquals("interval [tuned reps=3 ivl=10.0 ef=2.36 q=5]", 35.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=3 ivl=10.0 ef=2.36 q=5]", 2.46, it.easeFactor, 1e-9)
        }
        review(CardState(5, 30.0, 2.9, now), 0, now, tunedSettings).let {
            assertEquals("reps [tuned reps=5 ivl=30.0 ef=2.9 q=0]", 0, it.reps)
            assertEquals("interval [tuned reps=5 ivl=30.0 ef=2.9 q=0]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=5 ivl=30.0 ef=2.9 q=0]", 2.1, it.easeFactor, 1e-9)
        }
        review(CardState(5, 30.0, 2.9, now), 1, now, tunedSettings).let {
            assertEquals("reps [tuned reps=5 ivl=30.0 ef=2.9 q=1]", 0, it.reps)
            assertEquals("interval [tuned reps=5 ivl=30.0 ef=2.9 q=1]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=5 ivl=30.0 ef=2.9 q=1]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(5, 30.0, 2.9, now), 2, now, tunedSettings).let {
            assertEquals("reps [tuned reps=5 ivl=30.0 ef=2.9 q=2]", 0, it.reps)
            assertEquals("interval [tuned reps=5 ivl=30.0 ef=2.9 q=2]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=5 ivl=30.0 ef=2.9 q=2]", 2.58, it.easeFactor, 1e-9)
        }
        review(CardState(5, 30.0, 2.9, now), 3, now, tunedSettings).let {
            assertEquals("reps [tuned reps=5 ivl=30.0 ef=2.9 q=3]", 6, it.reps)
            assertEquals("interval [tuned reps=5 ivl=30.0 ef=2.9 q=3]", 87.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=5 ivl=30.0 ef=2.9 q=3]", 2.76, it.easeFactor, 1e-9)
        }
        review(CardState(5, 30.0, 2.9, now), 4, now, tunedSettings).let {
            assertEquals("reps [tuned reps=5 ivl=30.0 ef=2.9 q=4]", 6, it.reps)
            assertEquals("interval [tuned reps=5 ivl=30.0 ef=2.9 q=4]", 87.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=5 ivl=30.0 ef=2.9 q=4]", 2.9, it.easeFactor, 1e-9)
        }
        review(CardState(5, 30.0, 2.9, now), 5, now, tunedSettings).let {
            assertEquals("reps [tuned reps=5 ivl=30.0 ef=2.9 q=5]", 6, it.reps)
            assertEquals("interval [tuned reps=5 ivl=30.0 ef=2.9 q=5]", 130.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=5 ivl=30.0 ef=2.9 q=5]", 3.0, it.easeFactor, 1e-9)
        }
        review(CardState(8, 200.0, 3.1, now), 0, now, tunedSettings).let {
            assertEquals("reps [tuned reps=8 ivl=200.0 ef=3.1 q=0]", 0, it.reps)
            assertEquals("interval [tuned reps=8 ivl=200.0 ef=3.1 q=0]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=8 ivl=200.0 ef=3.1 q=0]", 2.3000000000000003, it.easeFactor, 1e-9)
        }
        review(CardState(8, 200.0, 3.1, now), 1, now, tunedSettings).let {
            assertEquals("reps [tuned reps=8 ivl=200.0 ef=3.1 q=1]", 0, it.reps)
            assertEquals("interval [tuned reps=8 ivl=200.0 ef=3.1 q=1]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=8 ivl=200.0 ef=3.1 q=1]", 2.56, it.easeFactor, 1e-9)
        }
        review(CardState(8, 200.0, 3.1, now), 2, now, tunedSettings).let {
            assertEquals("reps [tuned reps=8 ivl=200.0 ef=3.1 q=2]", 0, it.reps)
            assertEquals("interval [tuned reps=8 ivl=200.0 ef=3.1 q=2]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=8 ivl=200.0 ef=3.1 q=2]", 2.7800000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(8, 200.0, 3.1, now), 3, now, tunedSettings).let {
            assertEquals("reps [tuned reps=8 ivl=200.0 ef=3.1 q=3]", 9, it.reps)
            assertEquals("interval [tuned reps=8 ivl=200.0 ef=3.1 q=3]", 620.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=8 ivl=200.0 ef=3.1 q=3]", 2.96, it.easeFactor, 1e-9)
        }
        review(CardState(8, 200.0, 3.1, now), 4, now, tunedSettings).let {
            assertEquals("reps [tuned reps=8 ivl=200.0 ef=3.1 q=4]", 9, it.reps)
            assertEquals("interval [tuned reps=8 ivl=200.0 ef=3.1 q=4]", 620.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=8 ivl=200.0 ef=3.1 q=4]", 3.1, it.easeFactor, 1e-9)
        }
        review(CardState(8, 200.0, 3.1, now), 5, now, tunedSettings).let {
            assertEquals("reps [tuned reps=8 ivl=200.0 ef=3.1 q=5]", 9, it.reps)
            assertEquals("interval [tuned reps=8 ivl=200.0 ef=3.1 q=5]", 930.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=8 ivl=200.0 ef=3.1 q=5]", 3.2, it.easeFactor, 1e-9)
        }
        review(CardState(2, 1.0, 2.5, now), 0, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=1.0 ef=2.5 q=0]", 0, it.reps)
            assertEquals("interval [tuned reps=2 ivl=1.0 ef=2.5 q=0]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=1.0 ef=2.5 q=0]", 1.7000000000000002, it.easeFactor, 1e-9)
        }
        review(CardState(2, 1.0, 2.5, now), 1, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=1.0 ef=2.5 q=1]", 0, it.reps)
            assertEquals("interval [tuned reps=2 ivl=1.0 ef=2.5 q=1]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=1.0 ef=2.5 q=1]", 1.96, it.easeFactor, 1e-9)
        }
        review(CardState(2, 1.0, 2.5, now), 2, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=1.0 ef=2.5 q=2]", 0, it.reps)
            assertEquals("interval [tuned reps=2 ivl=1.0 ef=2.5 q=2]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=1.0 ef=2.5 q=2]", 2.1799999999999997, it.easeFactor, 1e-9)
        }
        review(CardState(2, 1.0, 2.5, now), 3, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=1.0 ef=2.5 q=3]", 3, it.reps)
            assertEquals("interval [tuned reps=2 ivl=1.0 ef=2.5 q=3]", 2.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=1.0 ef=2.5 q=3]", 2.36, it.easeFactor, 1e-9)
        }
        review(CardState(2, 1.0, 2.5, now), 4, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=1.0 ef=2.5 q=4]", 3, it.reps)
            assertEquals("interval [tuned reps=2 ivl=1.0 ef=2.5 q=4]", 2.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=1.0 ef=2.5 q=4]", 2.5, it.easeFactor, 1e-9)
        }
        review(CardState(2, 1.0, 2.5, now), 5, now, tunedSettings).let {
            assertEquals("reps [tuned reps=2 ivl=1.0 ef=2.5 q=5]", 3, it.reps)
            assertEquals("interval [tuned reps=2 ivl=1.0 ef=2.5 q=5]", 4.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=2 ivl=1.0 ef=2.5 q=5]", 2.6, it.easeFactor, 1e-9)
        }
        review(CardState(4, 0.5, 1.3, now), 0, now, tunedSettings).let {
            assertEquals("reps [tuned reps=4 ivl=0.5 ef=1.3 q=0]", 0, it.reps)
            assertEquals("interval [tuned reps=4 ivl=0.5 ef=1.3 q=0]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=4 ivl=0.5 ef=1.3 q=0]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(4, 0.5, 1.3, now), 1, now, tunedSettings).let {
            assertEquals("reps [tuned reps=4 ivl=0.5 ef=1.3 q=1]", 0, it.reps)
            assertEquals("interval [tuned reps=4 ivl=0.5 ef=1.3 q=1]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=4 ivl=0.5 ef=1.3 q=1]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(4, 0.5, 1.3, now), 2, now, tunedSettings).let {
            assertEquals("reps [tuned reps=4 ivl=0.5 ef=1.3 q=2]", 0, it.reps)
            assertEquals("interval [tuned reps=4 ivl=0.5 ef=1.3 q=2]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=4 ivl=0.5 ef=1.3 q=2]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(4, 0.5, 1.3, now), 3, now, tunedSettings).let {
            assertEquals("reps [tuned reps=4 ivl=0.5 ef=1.3 q=3]", 5, it.reps)
            assertEquals("interval [tuned reps=4 ivl=0.5 ef=1.3 q=3]", 1.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=4 ivl=0.5 ef=1.3 q=3]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(4, 0.5, 1.3, now), 4, now, tunedSettings).let {
            assertEquals("reps [tuned reps=4 ivl=0.5 ef=1.3 q=4]", 5, it.reps)
            assertEquals("interval [tuned reps=4 ivl=0.5 ef=1.3 q=4]", 1.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=4 ivl=0.5 ef=1.3 q=4]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(4, 0.5, 1.3, now), 5, now, tunedSettings).let {
            assertEquals("reps [tuned reps=4 ivl=0.5 ef=1.3 q=5]", 5, it.reps)
            assertEquals("interval [tuned reps=4 ivl=0.5 ef=1.3 q=5]", 1.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=4 ivl=0.5 ef=1.3 q=5]", 1.4000000000000001, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 1.3, now), 0, now, tunedSettings).let {
            assertEquals("reps [tuned reps=0 ivl=0.0 ef=1.3 q=0]", 0, it.reps)
            assertEquals("interval [tuned reps=0 ivl=0.0 ef=1.3 q=0]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=0 ivl=0.0 ef=1.3 q=0]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 1.3, now), 1, now, tunedSettings).let {
            assertEquals("reps [tuned reps=0 ivl=0.0 ef=1.3 q=1]", 0, it.reps)
            assertEquals("interval [tuned reps=0 ivl=0.0 ef=1.3 q=1]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=0 ivl=0.0 ef=1.3 q=1]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 1.3, now), 2, now, tunedSettings).let {
            assertEquals("reps [tuned reps=0 ivl=0.0 ef=1.3 q=2]", 0, it.reps)
            assertEquals("interval [tuned reps=0 ivl=0.0 ef=1.3 q=2]", 0.5, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=0 ivl=0.0 ef=1.3 q=2]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 1.3, now), 3, now, tunedSettings).let {
            assertEquals("reps [tuned reps=0 ivl=0.0 ef=1.3 q=3]", 1, it.reps)
            assertEquals("interval [tuned reps=0 ivl=0.0 ef=1.3 q=3]", 2.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=0 ivl=0.0 ef=1.3 q=3]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 1.3, now), 4, now, tunedSettings).let {
            assertEquals("reps [tuned reps=0 ivl=0.0 ef=1.3 q=4]", 1, it.reps)
            assertEquals("interval [tuned reps=0 ivl=0.0 ef=1.3 q=4]", 4.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=0 ivl=0.0 ef=1.3 q=4]", 1.3, it.easeFactor, 1e-9)
        }
        review(CardState(0, 0.0, 1.3, now), 5, now, tunedSettings).let {
            assertEquals("reps [tuned reps=0 ivl=0.0 ef=1.3 q=5]", 1, it.reps)
            assertEquals("interval [tuned reps=0 ivl=0.0 ef=1.3 q=5]", 10.0, it.intervalDays, 1e-9)
            assertEquals("ease [tuned reps=0 ivl=0.0 ef=1.3 q=5]", 1.4000000000000001, it.easeFactor, 1e-9)
        }
    }

    @Test
    fun `interval labels match the Python implementation`() {
        assertEquals("days=0.0", "<1 j", intervalLabel(0.0))
        assertEquals("days=0.4", "<1 j", intervalLabel(0.4))
        assertEquals("days=0.5", "<1 j", intervalLabel(0.5))
        assertEquals("days=1.0", "1 j", intervalLabel(1.0))
        assertEquals("days=2.5", "2 j", intervalLabel(2.5))
        assertEquals("days=3.0", "3 j", intervalLabel(3.0))
        assertEquals("days=7.0", "7 j", intervalLabel(7.0))
        assertEquals("days=29.0", "29 j", intervalLabel(29.0))
        assertEquals("days=30.0", "1 mois", intervalLabel(30.0))
        assertEquals("days=44.0", "1 mois", intervalLabel(44.0))
        assertEquals("days=45.0", "2 mois", intervalLabel(45.0))
        assertEquals("days=75.0", "2 mois", intervalLabel(75.0))
        assertEquals("days=364.0", "12 mois", intervalLabel(364.0))
        assertEquals("days=365.0", "1 an", intervalLabel(365.0))
        assertEquals("days=547.0", "1 an", intervalLabel(547.0))
        assertEquals("days=730.0", "2 ans", intervalLabel(730.0))
        assertEquals("days=1000.0", "3 ans", intervalLabel(1000.0))
    }

    @Test
    fun `similarity matches Python difflib ratio`() {
        assertEquals("Functional Analysis vs Chapter1", 0.14814814814814814, Similarity.ratio("Functional Analysis", "Chapter1"), 1e-12)
        assertEquals("Functional Analysis vs functional_analysis", 0.9473684210526315, Similarity.ratio("Functional Analysis", "functional_analysis"), 1e-12)
        assertEquals("Functional Analysis vs Functional Analysis", 1.0, Similarity.ratio("Functional Analysis", "Functional Analysis"), 1e-12)
        assertEquals("Functional Analysis vs syllabus", 0.14814814814814814, Similarity.ratio("Functional Analysis", "syllabus"), 1e-12)
        assertEquals("Functional Analysis vs Foundations_of_ML", 0.4444444444444444, Similarity.ratio("Functional Analysis", "Foundations_of_ML"), 1e-12)
        assertEquals("Functional Analysis vs Time_Series_2024", 0.2857142857142857, Similarity.ratio("Functional Analysis", "Time_Series_2024"), 1e-12)
        assertEquals("Functional Analysis vs martingale-notes", 0.4, Similarity.ratio("Functional Analysis", "martingale-notes"), 1e-12)
        assertEquals("Functional Analysis vs Cours", 0.16666666666666666, Similarity.ratio("Functional Analysis", "Cours"), 1e-12)
        assertEquals("Functional Analysis vs TD1", 0.09090909090909091, Similarity.ratio("Functional Analysis", "TD1"), 1e-12)
        assertEquals("Functional Analysis vs Final_Exam_2022", 0.35294117647058826, Similarity.ratio("Functional Analysis", "Final_Exam_2022"), 1e-12)
        assertEquals("Math ML DL Pauwels vs Chapter1", 0.23076923076923078, Similarity.ratio("Math ML DL Pauwels", "Chapter1"), 1e-12)
        assertEquals("Math ML DL Pauwels vs functional_analysis", 0.21621621621621623, Similarity.ratio("Math ML DL Pauwels", "functional_analysis"), 1e-12)
        assertEquals("Math ML DL Pauwels vs Functional Analysis", 0.2702702702702703, Similarity.ratio("Math ML DL Pauwels", "Functional Analysis"), 1e-12)
        assertEquals("Math ML DL Pauwels vs syllabus", 0.23076923076923078, Similarity.ratio("Math ML DL Pauwels", "syllabus"), 1e-12)
        assertEquals("Math ML DL Pauwels vs Foundations_of_ML", 0.22857142857142856, Similarity.ratio("Math ML DL Pauwels", "Foundations_of_ML"), 1e-12)
        assertEquals("Math ML DL Pauwels vs Time_Series_2024", 0.17647058823529413, Similarity.ratio("Math ML DL Pauwels", "Time_Series_2024"), 1e-12)
        assertEquals("Math ML DL Pauwels vs martingale-notes", 0.35294117647058826, Similarity.ratio("Math ML DL Pauwels", "martingale-notes"), 1e-12)
        assertEquals("Math ML DL Pauwels vs Cours", 0.17391304347826086, Similarity.ratio("Math ML DL Pauwels", "Cours"), 1e-12)
        assertEquals("Math ML DL Pauwels vs TD1", 0.19047619047619047, Similarity.ratio("Math ML DL Pauwels", "TD1"), 1e-12)
        assertEquals("Math ML DL Pauwels vs Final_Exam_2022", 0.06060606060606061, Similarity.ratio("Math ML DL Pauwels", "Final_Exam_2022"), 1e-12)
        assertEquals("Time Series vs Chapter1", 0.3157894736842105, Similarity.ratio("Time Series", "Chapter1"), 1e-12)
        assertEquals("Time Series vs functional_analysis", 0.3333333333333333, Similarity.ratio("Time Series", "functional_analysis"), 1e-12)
        assertEquals("Time Series vs Functional Analysis", 0.4, Similarity.ratio("Time Series", "Functional Analysis"), 1e-12)
        assertEquals("Time Series vs syllabus", 0.21052631578947367, Similarity.ratio("Time Series", "syllabus"), 1e-12)
        assertEquals("Time Series vs Foundations_of_ML", 0.21428571428571427, Similarity.ratio("Time Series", "Foundations_of_ML"), 1e-12)
        assertEquals("Time Series vs Time_Series_2024", 0.7407407407407407, Similarity.ratio("Time Series", "Time_Series_2024"), 1e-12)
        assertEquals("Time Series vs martingale-notes", 0.37037037037037035, Similarity.ratio("Time Series", "martingale-notes"), 1e-12)
        assertEquals("Time Series vs Cours", 0.125, Similarity.ratio("Time Series", "Cours"), 1e-12)
        assertEquals("Time Series vs TD1", 0.14285714285714285, Similarity.ratio("Time Series", "TD1"), 1e-12)
        assertEquals("Time Series vs Final_Exam_2022", 0.15384615384615385, Similarity.ratio("Time Series", "Final_Exam_2022"), 1e-12)
        assertEquals("M1 vs Chapter1", 0.2, Similarity.ratio("M1", "Chapter1"), 1e-12)
        assertEquals("M1 vs functional_analysis", 0.0, Similarity.ratio("M1", "functional_analysis"), 1e-12)
        assertEquals("M1 vs Functional Analysis", 0.0, Similarity.ratio("M1", "Functional Analysis"), 1e-12)
        assertEquals("M1 vs syllabus", 0.0, Similarity.ratio("M1", "syllabus"), 1e-12)
        assertEquals("M1 vs Foundations_of_ML", 0.10526315789473684, Similarity.ratio("M1", "Foundations_of_ML"), 1e-12)
        assertEquals("M1 vs Time_Series_2024", 0.1111111111111111, Similarity.ratio("M1", "Time_Series_2024"), 1e-12)
        assertEquals("M1 vs martingale-notes", 0.1111111111111111, Similarity.ratio("M1", "martingale-notes"), 1e-12)
        assertEquals("M1 vs Cours", 0.0, Similarity.ratio("M1", "Cours"), 1e-12)
        assertEquals("M1 vs TD1", 0.4, Similarity.ratio("M1", "TD1"), 1e-12)
        assertEquals("M1 vs Final_Exam_2022", 0.11764705882352941, Similarity.ratio("M1", "Final_Exam_2022"), 1e-12)
        assertEquals("Topological and Metric Spaces vs Chapter1", 0.16216216216216217, Similarity.ratio("Topological and Metric Spaces", "Chapter1"), 1e-12)
        assertEquals("Topological and Metric Spaces vs functional_analysis", 0.3333333333333333, Similarity.ratio("Topological and Metric Spaces", "functional_analysis"), 1e-12)
        assertEquals("Topological and Metric Spaces vs Functional Analysis", 0.375, Similarity.ratio("Topological and Metric Spaces", "Functional Analysis"), 1e-12)
        assertEquals("Topological and Metric Spaces vs syllabus", 0.16216216216216217, Similarity.ratio("Topological and Metric Spaces", "syllabus"), 1e-12)
        assertEquals("Topological and Metric Spaces vs Foundations_of_ML", 0.17391304347826086, Similarity.ratio("Topological and Metric Spaces", "Foundations_of_ML"), 1e-12)
        assertEquals("Topological and Metric Spaces vs Time_Series_2024", 0.35555555555555557, Similarity.ratio("Topological and Metric Spaces", "Time_Series_2024"), 1e-12)
        assertEquals("Topological and Metric Spaces vs martingale-notes", 0.35555555555555557, Similarity.ratio("Topological and Metric Spaces", "martingale-notes"), 1e-12)
        assertEquals("Topological and Metric Spaces vs Cours", 0.17647058823529413, Similarity.ratio("Topological and Metric Spaces", "Cours"), 1e-12)
        assertEquals("Topological and Metric Spaces vs TD1", 0.125, Similarity.ratio("Topological and Metric Spaces", "TD1"), 1e-12)
        assertEquals("Topological and Metric Spaces vs Final_Exam_2022", 0.22727272727272727, Similarity.ratio("Topological and Metric Spaces", "Final_Exam_2022"), 1e-12)
        assertEquals("Foundations of ML vs Chapter1", 0.16, Similarity.ratio("Foundations of ML", "Chapter1"), 1e-12)
        assertEquals("Foundations of ML vs functional_analysis", 0.4444444444444444, Similarity.ratio("Foundations of ML", "functional_analysis"), 1e-12)
        assertEquals("Foundations of ML vs Functional Analysis", 0.4444444444444444, Similarity.ratio("Foundations of ML", "Functional Analysis"), 1e-12)
        assertEquals("Foundations of ML vs syllabus", 0.16, Similarity.ratio("Foundations of ML", "syllabus"), 1e-12)
        assertEquals("Foundations of ML vs Foundations_of_ML", 0.8823529411764706, Similarity.ratio("Foundations of ML", "Foundations_of_ML"), 1e-12)
        assertEquals("Foundations of ML vs Time_Series_2024", 0.18181818181818182, Similarity.ratio("Foundations of ML", "Time_Series_2024"), 1e-12)
        assertEquals("Foundations of ML vs martingale-notes", 0.30303030303030304, Similarity.ratio("Foundations of ML", "martingale-notes"), 1e-12)
        assertEquals("Foundations of ML vs Cours", 0.2727272727272727, Similarity.ratio("Foundations of ML", "Cours"), 1e-12)
        assertEquals("Foundations of ML vs TD1", 0.1, Similarity.ratio("Foundations of ML", "TD1"), 1e-12)
        assertEquals("Foundations of ML vs Final_Exam_2022", 0.25, Similarity.ratio("Foundations of ML", "Final_Exam_2022"), 1e-12)
        assertEquals("Martingale vs Chapter1", 0.2222222222222222, Similarity.ratio("Martingale", "Chapter1"), 1e-12)
        assertEquals("Martingale vs functional_analysis", 0.3448275862068966, Similarity.ratio("Martingale", "functional_analysis"), 1e-12)
        assertEquals("Martingale vs Functional Analysis", 0.3448275862068966, Similarity.ratio("Martingale", "Functional Analysis"), 1e-12)
        assertEquals("Martingale vs syllabus", 0.1111111111111111, Similarity.ratio("Martingale", "syllabus"), 1e-12)
        assertEquals("Martingale vs Foundations_of_ML", 0.37037037037037035, Similarity.ratio("Martingale", "Foundations_of_ML"), 1e-12)
        assertEquals("Martingale vs Time_Series_2024", 0.23076923076923078, Similarity.ratio("Martingale", "Time_Series_2024"), 1e-12)
        assertEquals("Martingale vs martingale-notes", 0.7692307692307693, Similarity.ratio("Martingale", "martingale-notes"), 1e-12)
        assertEquals("Martingale vs Cours", 0.13333333333333333, Similarity.ratio("Martingale", "Cours"), 1e-12)
        assertEquals("Martingale vs TD1", 0.15384615384615385, Similarity.ratio("Martingale", "TD1"), 1e-12)
        assertEquals("Martingale vs Final_Exam_2022", 0.4, Similarity.ratio("Martingale", "Final_Exam_2022"), 1e-12)
    }
}

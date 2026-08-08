package com.drinfonty.checkbox.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimerEntryTest {
	private static final long T0 = 1_765_200_000_000L;
	private static final long FIVE_MIN = 5 * 60 * 1_000L;

	private static TimerEntry timer() {
		return TimerEntry.create("Furnace batch", EntryScope.WORLD, T0, FIVE_MIN);
	}

	@Test
	void startedTimerCountsDownAgainstTheClock() {
		TimerEntry entry = timer();
		entry.start(T0);

		assertEquals(TimerEntry.State.RUNNING, entry.state());
		assertEquals(FIVE_MIN, entry.remainingMillis(T0));
		assertEquals(FIVE_MIN - 90_000L, entry.remainingMillis(T0 + 90_000L));
	}

	@Test
	void pauseAndResumePreserveRemainingTime() {
		TimerEntry entry = timer();
		entry.start(T0);
		entry.pause(T0 + 60_000L);

		assertEquals(TimerEntry.State.PAUSED, entry.state());
		assertEquals(FIVE_MIN - 60_000L, entry.remainingMillis(T0 + 60_000L));

		// Time passes while paused; the remaining time must not move.
		assertEquals(FIVE_MIN - 60_000L, entry.remainingMillis(T0 + 600_000L));

		entry.start(T0 + 600_000L);
		assertEquals(TimerEntry.State.RUNNING, entry.state());
		assertEquals(FIVE_MIN - 60_000L, entry.remainingMillis(T0 + 600_000L));
	}

	@Test
	void updateFiresExactlyOnceOnExpiry() {
		TimerEntry entry = timer();
		entry.start(T0);

		assertFalse(entry.update(T0 + FIVE_MIN - 1));
		assertTrue(entry.update(T0 + FIVE_MIN), "expiry should be reported");
		assertFalse(entry.update(T0 + FIVE_MIN + 5_000L), "expiry must not be reported twice");

		assertEquals(TimerEntry.State.EXPIRED, entry.state());
		assertTrue(entry.isDone());
		assertEquals(0L, entry.remainingMillis(T0 + FIVE_MIN + 5_000L));
		assertEquals(T0 + FIVE_MIN, entry.completedAt());
	}

	@Test
	void resetReturnsToIdleWithFullDuration() {
		TimerEntry entry = timer();
		entry.start(T0);
		entry.update(T0 + FIVE_MIN);
		entry.reset(T0 + FIVE_MIN);

		assertEquals(TimerEntry.State.IDLE, entry.state());
		assertFalse(entry.isDone());
		assertEquals(FIVE_MIN, entry.remainingMillis(T0 + FIVE_MIN));
		assertEquals(0L, entry.completedAt());
	}

	@Test
	void restartingAnExpiredTimerUsesTheFullDuration() {
		TimerEntry entry = timer();
		entry.start(T0);
		entry.update(T0 + FIVE_MIN);

		entry.start(T0 + FIVE_MIN);
		assertEquals(TimerEntry.State.RUNNING, entry.state());
		assertEquals(FIVE_MIN, entry.remainingMillis(T0 + FIVE_MIN));
	}

	@Test
	void timerLoadedAsRunningWithNoEndTimestampFallsBackToPaused() {
		// Guards against a hand-edited or truncated file silently restarting the countdown.
		TimerEntry entry = TimerEntry.restored(java.util.UUID.randomUUID(), "t", EntryScope.WORLD,
				T0, 0, FIVE_MIN, TimerEntry.State.RUNNING, 0L, 90_000L, 0L);

		assertEquals(TimerEntry.State.PAUSED, entry.state());
		assertEquals(90_000L, entry.remainingMillis(T0));
	}

	@Test
	void timerRestoredAsRunningExpiresIfItsEndPassedWhileOffline() {
		TimerEntry entry = TimerEntry.restored(java.util.UUID.randomUUID(), "t", EntryScope.WORLD,
				T0, 0, FIVE_MIN, TimerEntry.State.RUNNING, T0 + FIVE_MIN, 0L, 0L);

		assertTrue(entry.update(T0 + FIVE_MIN + 1_000L));
		assertEquals(TimerEntry.State.EXPIRED, entry.state());
	}

	@Test
	void remainingTimeFormatting() {
		assertEquals("0:00", TimerEntry.formatDuration(0L));
		assertEquals("0:01", TimerEntry.formatDuration(1L), "a partial second still reads as 1s");
		assertEquals("4:31", TimerEntry.formatDuration(271_000L));
		assertEquals("1:00:00", TimerEntry.formatDuration(3_600_000L));
		assertEquals("2:05:09", TimerEntry.formatDuration(7_509_000L));
	}
}

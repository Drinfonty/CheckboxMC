package com.drinfonty.checkbox.model;

import java.util.UUID;

/**
 * A countdown timer (SPEC §2.4).
 *
 * <p>A running timer stores an absolute end timestamp rather than decrementing a counter, so
 * the displayed time cannot drift no matter how irregular the tick rate is. A paused timer
 * stores the remaining duration instead, which is what makes pausing survive a restart.
 *
 * <p>Every method that needs the current time takes it as a parameter. The model never reads
 * the clock itself, so the pause, expiry and restart behaviour is testable without waiting.
 */
public final class TimerEntry extends TodoEntry {
	public static final long DURATION_MIN_MS = 1_000L;
	public static final long DURATION_MAX_MS = 24L * 60L * 60L * 1_000L;

	public enum State {
		IDLE,
		RUNNING,
		PAUSED,
		EXPIRED;

		public static State parse(String raw, State fallback) {
			if (raw == null) {
				return fallback;
			}
			for (State state : values()) {
				if (state.name().equalsIgnoreCase(raw)) {
					return state;
				}
			}
			return fallback;
		}
	}

	private long durationMillis;
	private State state = State.IDLE;
	private long endsAtEpochMillis;
	private long remainingMillis;

	/** Set once the expiry notification has fired, so it does not fire again every tick. */
	private transient boolean notified;

	public TimerEntry(UUID id, String text, EntryScope scope, long createdAt, long durationMillis) {
		super(id, text, scope, createdAt);
		this.durationMillis = validateDuration(durationMillis);
		this.remainingMillis = this.durationMillis;
	}

	public static TimerEntry create(String text, EntryScope scope, long nowMillis,
			long durationMillis) {
		return new TimerEntry(UUID.randomUUID(), text, scope, nowMillis, durationMillis);
	}

	@Override
	public Type type() {
		return Type.TIMER;
	}

	@Override
	public boolean isDone() {
		return state == State.EXPIRED;
	}

	public State state() {
		return state;
	}

	public long durationMillis() {
		return durationMillis;
	}

	public void setDuration(long durationMillis) {
		long validated = validateDuration(durationMillis);
		if (validated != this.durationMillis) {
			this.durationMillis = validated;
			if (state != State.RUNNING) {
				this.remainingMillis = validated;
			}
			markDirty();
		}
	}

	/** Milliseconds left, never negative. Safe to call in any state. */
	public long remainingMillis(long nowMillis) {
		if (state == State.RUNNING) {
			return Math.max(0L, endsAtEpochMillis - nowMillis);
		}
		return Math.max(0L, remainingMillis);
	}

	public long endsAtEpochMillis() {
		return endsAtEpochMillis;
	}

	/** Starts an idle timer, or resumes a paused one. No effect while running or expired. */
	public void start(long nowMillis) {
		if (state == State.RUNNING) {
			return;
		}
		long remaining = state == State.EXPIRED ? durationMillis : Math.max(0L, remainingMillis);
		if (remaining <= 0L) {
			remaining = durationMillis;
		}
		state = State.RUNNING;
		endsAtEpochMillis = nowMillis + remaining;
		remainingMillis = remaining;
		notified = false;
		updateCompletion(false, nowMillis);
		markDirty();
	}

	/** Freezes a running timer, preserving the time left. */
	public void pause(long nowMillis) {
		if (state != State.RUNNING) {
			return;
		}
		remainingMillis = Math.max(0L, endsAtEpochMillis - nowMillis);
		state = State.PAUSED;
		endsAtEpochMillis = 0L;
		markDirty();
	}

	public void reset(long nowMillis) {
		state = State.IDLE;
		remainingMillis = durationMillis;
		endsAtEpochMillis = 0L;
		notified = false;
		updateCompletion(false, nowMillis);
		markDirty();
	}

	/**
	 * Advances state to match the clock.
	 *
	 * @return {@code true} exactly once, on the call where the timer runs out
	 */
	public boolean update(long nowMillis) {
		if (state != State.RUNNING || nowMillis < endsAtEpochMillis) {
			return false;
		}
		state = State.EXPIRED;
		remainingMillis = 0L;
		endsAtEpochMillis = 0L;
		updateCompletion(true, nowMillis);
		markDirty();
		return true;
	}

	public boolean isNotified() {
		return notified;
	}

	public void setNotified(boolean notified) {
		this.notified = notified;
	}

	/** Formats remaining time as {@code h:mm:ss} above an hour, otherwise {@code m:ss}. */
	public String formatRemaining(long nowMillis) {
		return formatDuration(remainingMillis(nowMillis));
	}

	public static String formatDuration(long millis) {
		long totalSeconds = Math.max(0L, millis + 999L) / 1000L;
		long hours = totalSeconds / 3600L;
		long minutes = (totalSeconds % 3600L) / 60L;
		long seconds = totalSeconds % 60L;
		if (hours > 0) {
			return "%d:%02d:%02d".formatted(hours, minutes, seconds);
		}
		return "%d:%02d".formatted(minutes, seconds);
	}

	public static long validateDuration(long durationMillis) {
		if (durationMillis < DURATION_MIN_MS || durationMillis > DURATION_MAX_MS) {
			throw new IllegalArgumentException("duration must be between 1s and 24h");
		}
		return durationMillis;
	}

	public static long coerceDuration(long durationMillis) {
		return Math.max(DURATION_MIN_MS, Math.min(DURATION_MAX_MS, durationMillis));
	}

	/** Rebuilds an entry from persisted state. For the storage codec only. */
	public static TimerEntry restored(UUID id, String text, EntryScope scope, long createdAt,
			int order, long durationMillis, State state, long endsAtEpochMillis,
			long remainingMillis, long completedAt) {
		TimerEntry entry = new TimerEntry(id, text, scope, createdAt, durationMillis);
		entry.setOrder(order);
		entry.state = state == null ? State.IDLE : state;
		entry.endsAtEpochMillis = Math.max(0L, endsAtEpochMillis);
		entry.remainingMillis = Math.max(0L, remainingMillis);

		if (entry.state == State.RUNNING && entry.endsAtEpochMillis <= 0L) {
			// A running timer with no end timestamp cannot be resumed as running; falling
			// back to PAUSED keeps the time left rather than silently restarting it.
			entry.state = State.PAUSED;
			entry.remainingMillis = entry.remainingMillis > 0L
					? entry.remainingMillis
					: entry.durationMillis;
		}
		if (entry.state != State.RUNNING && entry.state != State.EXPIRED
				&& entry.remainingMillis <= 0L) {
			entry.remainingMillis = entry.durationMillis;
		}
		if (entry.state == State.EXPIRED) {
			entry.remainingMillis = 0L;
			entry.notified = true;
			entry.setCompletedAt(completedAt);
		}
		return entry;
	}
}

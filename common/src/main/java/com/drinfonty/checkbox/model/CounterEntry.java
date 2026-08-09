package com.drinfonty.checkbox.model;

import java.util.UUID;

/**
 * An auto-tracked counter (SPEC §2.2, §2.3). Items collected and mobs killed are one type:
 * they differ only in what {@link EntryMatch#kind()} resolves against and which tracker feeds
 * them, so they share one progress model, one completion rule and one renderer.
 */
public final class CounterEntry extends TodoEntry {
	public static final int TARGET_MIN = 1;
	public static final int TARGET_MAX = 9999;

	/** How item progress is measured. Kill counters always behave like {@link #ACQUIRED}. */
	public enum CountMode {
		/** Every net increase in the inventory counts. Progress never decreases. */
		ACQUIRED,
		/** Progress mirrors how many the player is currently holding, and can decrease. */
		INVENTORY,
		/** Only items picked up off the ground count. */
		PICKED_UP;

		public static CountMode parse(String raw, CountMode fallback) {
			if (raw == null) {
				return fallback;
			}
			for (CountMode mode : values()) {
				if (mode.name().equalsIgnoreCase(raw)) {
					return mode;
				}
			}
			return fallback;
		}
	}

	private EntryMatch match;
	private int target;
	private int progress;
	private CountMode countMode;

	/**
	 * When set, the stored text is only a fallback and the display layer regenerates the
	 * label from the current target and the tracked thing's translated name. Kept as a flag
	 * rather than generating once at creation so that changing the target from 8 to 16 does
	 * not leave a description still claiming 8.
	 */
	private boolean autoLabel;

	public CounterEntry(UUID id, String text, EntryScope scope, long createdAt,
			EntryMatch match, int target, CountMode countMode) {
		super(id, text, scope, createdAt);
		if (match == null) {
			throw new IllegalArgumentException("counter entry requires a match");
		}
		this.match = match;
		this.target = validateTarget(target);
		this.countMode = countMode == null ? CountMode.ACQUIRED : countMode;
	}

	public static CounterEntry create(String text, EntryScope scope, long nowMillis,
			EntryMatch match, int target, CountMode countMode) {
		return new CounterEntry(UUID.randomUUID(), text, scope, nowMillis, match, target, countMode);
	}

	@Override
	public Type type() {
		return Type.COUNTER;
	}

	@Override
	public boolean isDone() {
		return progress >= target;
	}

	public EntryMatch match() {
		return match;
	}

	public void setMatch(EntryMatch match) {
		if (match != null && !match.equals(this.match)) {
			this.match = match;
			markDirty();
		}
	}

	public int target() {
		return target;
	}

	public void setTarget(int target, long nowMillis) {
		int validated = validateTarget(target);
		if (validated != this.target) {
			this.target = validated;
			this.progress = Math.min(this.progress, validated);
			updateCompletion(isDone(), nowMillis);
			markDirty();
		}
	}

	public int progress() {
		return progress;
	}

	/** Adds to progress, clamped at the target. Used by {@code ACQUIRED} and kill tracking. */
	public void addProgress(int amount, long nowMillis) {
		if (amount <= 0) {
			return;
		}
		setProgress(Math.min(target, progress + amount), nowMillis);
	}

	/** Sets progress outright, clamped to {@code 0..target}. Used by {@code INVENTORY}. */
	public void setProgress(int value, long nowMillis) {
		int clamped = Math.max(0, Math.min(target, value));
		if (clamped != this.progress) {
			this.progress = clamped;
			updateCompletion(isDone(), nowMillis);
			markDirty();
		}
	}

	public void resetProgress(long nowMillis) {
		setProgress(0, nowMillis);
	}

	public boolean autoLabel() {
		return autoLabel;
	}

	public void setAutoLabel(boolean autoLabel) {
		if (autoLabel != this.autoLabel) {
			this.autoLabel = autoLabel;
			markDirty();
		}
	}

	public CountMode countMode() {
		return countMode;
	}

	public void setCountMode(CountMode countMode) {
		if (countMode != null && countMode != this.countMode) {
			this.countMode = countMode;
			markDirty();
		}
	}

	/** {@code 0.0}–{@code 1.0}, for the HUD progress bar. */
	public float fraction() {
		return target <= 0 ? 0f : (float) progress / (float) target;
	}

	public static int validateTarget(int target) {
		if (target < TARGET_MIN || target > TARGET_MAX) {
			throw new IllegalArgumentException(
					"target must be between " + TARGET_MIN + " and " + TARGET_MAX);
		}
		return target;
	}

	public static int coerceTarget(int target) {
		return Math.max(TARGET_MIN, Math.min(TARGET_MAX, target));
	}

	/** Rebuilds an entry from persisted state. For the storage codec only. */
	public static CounterEntry restored(UUID id, String text, EntryScope scope, long createdAt,
			int order, EntryMatch match, int target, int progress, CountMode countMode,
			boolean autoLabel, long completedAt) {
		CounterEntry entry = new CounterEntry(id, text, scope, createdAt, match, target, countMode);
		entry.setOrder(order);
		entry.autoLabel = autoLabel;
		entry.progress = Math.max(0, Math.min(entry.target, progress));
		entry.setCompletedAt(entry.isDone() ? completedAt : 0L);
		return entry;
	}
}

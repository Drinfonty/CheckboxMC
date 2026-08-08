package com.drinfonty.checkbox.model;

import java.util.UUID;

/**
 * One row of the todo list. Sealed over the three entry types in SPEC §2.
 *
 * <p>Entries carry their own dirty flag rather than routing every mutation through the owning
 * {@link TodoList}. Progress is advanced by the trackers, which hold an entry directly and
 * have no reason to know which list it came from; a per-entry flag means those writes cannot
 * be lost by forgetting to notify the list.
 */
public sealed abstract class TodoEntry permits TextEntry, CounterEntry, TimerEntry {
	public static final int TEXT_MAX_LENGTH = 128;

	public enum Type {
		TEXT,
		COUNTER,
		TIMER;

		public static Type parse(String raw) {
			if (raw == null) {
				return null;
			}
			for (Type type : values()) {
				if (type.name().equalsIgnoreCase(raw)) {
					return type;
				}
			}
			return null;
		}
	}

	private final UUID id;
	private final long createdAt;
	private String text;
	private EntryScope scope;
	private int order;
	private long completedAt;
	private transient boolean dirty;

	protected TodoEntry(UUID id, String text, EntryScope scope, long createdAt) {
		this.id = id == null ? UUID.randomUUID() : id;
		this.text = validateText(text);
		this.scope = scope == null ? EntryScope.WORLD : scope;
		this.createdAt = createdAt;
	}

	public abstract Type type();

	/** Whether this entry counts as finished. Counters and timers derive it from their state. */
	public abstract boolean isDone();

	public final UUID id() {
		return id;
	}

	public final long createdAt() {
		return createdAt;
	}

	public final String text() {
		return text;
	}

	public final void setText(String text) {
		String validated = validateText(text);
		if (!validated.equals(this.text)) {
			this.text = validated;
			markDirty();
		}
	}

	public final EntryScope scope() {
		return scope;
	}

	public final void setScope(EntryScope scope) {
		if (scope != null && scope != this.scope) {
			this.scope = scope;
			markDirty();
		}
	}

	public final int order() {
		return order;
	}

	public final void setOrder(int order) {
		if (order != this.order) {
			this.order = order;
			markDirty();
		}
	}

	/** Epoch millis when this entry was completed, or {@code 0} if it is not complete. */
	public final long completedAt() {
		return completedAt;
	}

	protected final void setCompletedAt(long completedAt) {
		this.completedAt = Math.max(0L, completedAt);
	}

	/**
	 * Records completion time when an entry flips to done, and clears it when it flips back
	 * (an {@code INVENTORY} counter can go either way).
	 */
	protected final void updateCompletion(boolean doneNow, long nowMillis) {
		if (doneNow && completedAt == 0L) {
			completedAt = nowMillis;
		} else if (!doneNow && completedAt != 0L) {
			completedAt = 0L;
		}
	}

	public final boolean isDirty() {
		return dirty;
	}

	public final void markDirty() {
		this.dirty = true;
	}

	public final void clearDirty() {
		this.dirty = false;
	}

	public static String validateText(String text) {
		if (text == null) {
			throw new IllegalArgumentException("entry text must not be null");
		}
		String trimmed = text.strip();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("entry text must not be blank");
		}
		if (trimmed.length() > TEXT_MAX_LENGTH) {
			throw new IllegalArgumentException(
					"entry text must be at most " + TEXT_MAX_LENGTH + " characters");
		}
		return trimmed;
	}

	/** Repairs rather than rejects - used when loading text that is already on disk. */
	public static String coerceText(String text) {
		if (text == null) {
			return "(untitled)";
		}
		String trimmed = text.strip();
		if (trimmed.isEmpty()) {
			return "(untitled)";
		}
		return trimmed.length() > TEXT_MAX_LENGTH ? trimmed.substring(0, TEXT_MAX_LENGTH) : trimmed;
	}

	@Override
	public final boolean equals(Object other) {
		return other instanceof TodoEntry entry && entry.id.equals(this.id);
	}

	@Override
	public final int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		return type() + "[" + text + "]";
	}
}

package com.drinfonty.checkbox.model;

import java.util.UUID;

/** A manually checked-off entry (SPEC §2.1). */
public final class TextEntry extends TodoEntry {
	private boolean done;

	public TextEntry(UUID id, String text, EntryScope scope, long createdAt) {
		super(id, text, scope, createdAt);
	}

	public static TextEntry create(String text, EntryScope scope, long nowMillis) {
		return new TextEntry(UUID.randomUUID(), text, scope, nowMillis);
	}

	@Override
	public Type type() {
		return Type.TEXT;
	}

	@Override
	public boolean isDone() {
		return done;
	}

	public void setDone(boolean done, long nowMillis) {
		if (done != this.done) {
			this.done = done;
			updateCompletion(done, nowMillis);
			markDirty();
		}
	}

	public void toggle(long nowMillis) {
		setDone(!done, nowMillis);
	}

	/** Rebuilds an entry from persisted state. For the storage codec only. */
	public static TextEntry restored(UUID id, String text, EntryScope scope, long createdAt,
			int order, boolean done, long completedAt) {
		TextEntry entry = new TextEntry(id, text, scope, createdAt);
		entry.setOrder(order);
		entry.done = done;
		entry.setCompletedAt(done ? completedAt : 0L);
		return entry;
	}
}

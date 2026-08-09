package com.drinfonty.checkbox.track;

import com.drinfonty.checkbox.model.CounterEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Turns "how many of this item does the player hold right now" into progress (DESIGN §5.1).
 *
 * <p>Takes the counts as a function rather than reading an inventory itself, so the diffing
 * rules - which are where this feature goes subtly wrong - are unit testable without a game.
 * {@code TrackerManager} supplies the function that walks the real inventory.
 *
 * <p>The baseline is the load-bearing part. An entry whose count has never been seen is
 * recorded without crediting anything; otherwise logging into a world holding 64 oak logs
 * would instantly complete "collect 8 oak logs".
 */
public final class ItemCensus {
	private final Map<UUID, Integer> lastCounts = new HashMap<>();

	/**
	 * @param entries    the active item counters
	 * @param countOf    current inventory count for an entry
	 * @param nowMillis  wall clock, for completion timestamps
	 */
	/**
	 * @param onCompleted called for an entry that this census pushed over its target, so the
	 *                    caller can announce it. Never called on an entry's first sighting -
	 *                    an INVENTORY entry already satisfied when you log in has not just
	 *                    been achieved.
	 */
	public void update(List<CounterEntry> entries, ToIntFunction<CounterEntry> countOf,
			long nowMillis, Consumer<CounterEntry> onCompleted) {
		Set<UUID> live = new HashSet<>(entries.size());

		for (CounterEntry entry : entries) {
			if (entry.countMode() == CounterEntry.CountMode.PICKED_UP) {
				// Event-driven; the census would double-count it.
				continue;
			}

			UUID id = entry.id();
			live.add(id);
			int count = Math.max(0, countOf.applyAsInt(entry));
			Integer previous = lastCounts.put(id, count);
			boolean wasDone = entry.isDone();

			if (entry.countMode() == CounterEntry.CountMode.INVENTORY) {
				// Mirrors what is held, including on the very first census.
				entry.setProgress(count, nowMillis);
				if (previous != null && !wasDone && entry.isDone()) {
					onCompleted.accept(entry);
				}
				continue;
			}

			if (previous == null) {
				// First sighting: seed the baseline, credit nothing.
				continue;
			}
			int delta = count - previous;
			if (delta > 0) {
				entry.addProgress(delta, nowMillis);
				if (!wasDone && entry.isDone()) {
					onCompleted.accept(entry);
				}
			}
		}

		// Entries that were deleted should not keep a stale baseline around; if one comes
		// back it must re-seed rather than credit everything acquired while it was gone.
		lastCounts.keySet().retainAll(live);
	}

	/**
	 * Forgets every baseline, so the next census re-seeds without crediting. Called when the
	 * inventory changes out from under us - a world join or a respawn.
	 */
	public void invalidate() {
		lastCounts.clear();
	}

	/** Visible for testing. */
	public boolean hasBaselineFor(UUID entryId) {
		return lastCounts.containsKey(entryId);
	}
}

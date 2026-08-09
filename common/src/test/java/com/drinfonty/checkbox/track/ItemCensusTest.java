package com.drinfonty.checkbox.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.drinfonty.checkbox.model.CounterEntry;
import com.drinfonty.checkbox.model.EntryMatch;
import com.drinfonty.checkbox.model.EntryScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemCensusTest {
	private static final long T0 = 1_765_200_000_000L;

	/** Stands in for the inventory walk. */
	private final Map<UUID, Integer> counts = new HashMap<>();

	private CounterEntry entry(CounterEntry.CountMode mode, int target) {
		return CounterEntry.create("Collect oak logs", EntryScope.WORLD, T0,
				EntryMatch.item("oak_log"), target, mode);
	}

	private void hold(CounterEntry entry, int amount) {
		counts.put(entry.id(), amount);
	}

	/** Entries the last census reported as newly complete. */
	private final List<CounterEntry> completed = new ArrayList<>();

	private void census(ItemCensus census, List<CounterEntry> entries) {
		completed.clear();
		census.update(entries, e -> counts.getOrDefault(e.id(), 0), T0, completed::add);
	}

	@Test
	void firstCensusSeedsTheBaselineWithoutCrediting() {
		// The bug this exists to prevent: logging in holding 64 logs completing "collect 8".
		ItemCensus census = new ItemCensus();
		CounterEntry entry = entry(CounterEntry.CountMode.ACQUIRED, 8);
		hold(entry, 64);

		census(census, List.of(entry));
		assertEquals(0, entry.progress());
		assertTrue(census.hasBaselineFor(entry.id()));
	}

	@Test
	void acquiredCountsIncreasesOnly() {
		ItemCensus census = new ItemCensus();
		CounterEntry entry = entry(CounterEntry.CountMode.ACQUIRED, 8);
		List<CounterEntry> entries = List.of(entry);

		hold(entry, 0);
		census(census, entries);

		hold(entry, 3);
		census(census, entries);
		assertEquals(3, entry.progress());

		// Spending or storing them must not undo progress.
		hold(entry, 0);
		census(census, entries);
		assertEquals(3, entry.progress());

		// Re-acquiring counts again from the new, lower baseline.
		hold(entry, 2);
		census(census, entries);
		assertEquals(5, entry.progress());
	}

	@Test
	void inventoryModeMirrorsWhatIsHeldFromTheFirstCensus() {
		ItemCensus census = new ItemCensus();
		CounterEntry entry = entry(CounterEntry.CountMode.INVENTORY, 8);
		List<CounterEntry> entries = List.of(entry);

		hold(entry, 5);
		census(census, entries);
		assertEquals(5, entry.progress(), "INVENTORY shows the current count immediately");

		hold(entry, 8);
		census(census, entries);
		assertTrue(entry.isDone());

		hold(entry, 1);
		census(census, entries);
		assertEquals(1, entry.progress());
		assertFalse(entry.isDone(), "INVENTORY progress can fall back");
	}

	@Test
	void pickedUpEntriesAreLeftToTheEventPath() {
		ItemCensus census = new ItemCensus();
		CounterEntry entry = entry(CounterEntry.CountMode.PICKED_UP, 8);
		List<CounterEntry> entries = List.of(entry);

		hold(entry, 0);
		census(census, entries);
		hold(entry, 5);
		census(census, entries);

		assertEquals(0, entry.progress(), "the census must not double-count ground pickups");
		assertFalse(census.hasBaselineFor(entry.id()));
	}

	@Test
	void invalidateReseedsWithoutCrediting() {
		// A world join or respawn swaps the inventory out from under the baseline.
		ItemCensus census = new ItemCensus();
		CounterEntry entry = entry(CounterEntry.CountMode.ACQUIRED, 64);
		List<CounterEntry> entries = List.of(entry);

		hold(entry, 0);
		census(census, entries);
		hold(entry, 5);
		census(census, entries);
		assertEquals(5, entry.progress());

		census.invalidate();
		hold(entry, 40);
		census(census, entries);
		assertEquals(5, entry.progress(), "a new world's inventory is not an acquisition");

		hold(entry, 42);
		census(census, entries);
		assertEquals(7, entry.progress(), "counting resumes from the new baseline");
	}

	@Test
	void newEntriesSeedWithoutCreditingWhileOthersKeepCounting() {
		ItemCensus census = new ItemCensus();
		CounterEntry existing = entry(CounterEntry.CountMode.ACQUIRED, 64);
		hold(existing, 10);
		census(census, List.of(existing));

		CounterEntry added = entry(CounterEntry.CountMode.ACQUIRED, 64);
		hold(added, 10);
		hold(existing, 12);
		census(census, List.of(existing, added));

		assertEquals(2, existing.progress());
		assertEquals(0, added.progress(), "an entry added while holding items starts at zero");
	}

	@Test
	void deletedEntriesDoNotKeepStaleBaselines() {
		ItemCensus census = new ItemCensus();
		CounterEntry entry = entry(CounterEntry.CountMode.ACQUIRED, 64);
		hold(entry, 5);
		census(census, List.of(entry));
		assertTrue(census.hasBaselineFor(entry.id()));

		census(census, List.of());
		assertFalse(census.hasBaselineFor(entry.id()));
	}

	@Test
	void completionIsReportedOnceWhenTheTargetIsReached() {
		ItemCensus census = new ItemCensus();
		CounterEntry entry = entry(CounterEntry.CountMode.ACQUIRED, 8);
		List<CounterEntry> entries = List.of(entry);

		hold(entry, 0);
		census(census, entries);
		assertTrue(completed.isEmpty());

		hold(entry, 4);
		census(census, entries);
		assertTrue(completed.isEmpty(), "partial progress is not a completion");

		hold(entry, 8);
		census(census, entries);
		assertEquals(List.of(entry), completed);

		hold(entry, 20);
		census(census, entries);
		assertTrue(completed.isEmpty(), "an already-complete entry must not announce again");
	}

	@Test
	void anAlreadySatisfiedInventoryEntryDoesNotAnnounceOnFirstSight() {
		// Logging in holding enough is not an achievement worth a chime.
		ItemCensus census = new ItemCensus();
		CounterEntry entry = entry(CounterEntry.CountMode.INVENTORY, 8);
		List<CounterEntry> entries = List.of(entry);

		hold(entry, 32);
		census(census, entries);
		assertTrue(entry.isDone());
		assertTrue(completed.isEmpty());

		// Dropping below and climbing back is a real transition, so that does announce.
		hold(entry, 2);
		census(census, entries);
		hold(entry, 8);
		census(census, entries);
		assertEquals(List.of(entry), completed);
	}

	@Test
	void progressStopsAtTheTarget() {
		ItemCensus census = new ItemCensus();
		CounterEntry entry = entry(CounterEntry.CountMode.ACQUIRED, 8);
		List<CounterEntry> entries = List.of(entry);

		hold(entry, 0);
		census(census, entries);
		hold(entry, 500);
		census(census, entries);

		assertEquals(8, entry.progress());
		assertTrue(entry.isDone());
	}
}

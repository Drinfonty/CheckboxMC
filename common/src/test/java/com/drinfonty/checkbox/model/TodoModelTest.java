package com.drinfonty.checkbox.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TodoModelTest {
	private static final long T0 = 1_765_200_000_000L;

	@Test
	void counterCompletesWhenProgressReachesTarget() {
		CounterEntry entry = CounterEntry.create("Collect oak logs", EntryScope.WORLD, T0,
				EntryMatch.item("oak_log"), 8, CounterEntry.CountMode.ACQUIRED);

		entry.addProgress(3, T0);
		assertFalse(entry.isDone());
		assertEquals(3, entry.progress());

		entry.addProgress(5, T0 + 100);
		assertTrue(entry.isDone());
		assertEquals(T0 + 100, entry.completedAt());
	}

	@Test
	void counterProgressIsClampedToTarget() {
		CounterEntry entry = CounterEntry.create("Collect", EntryScope.WORLD, T0,
				EntryMatch.item("oak_log"), 8, CounterEntry.CountMode.ACQUIRED);

		entry.addProgress(999, T0);
		assertEquals(8, entry.progress());
		entry.setProgress(-5, T0);
		assertEquals(0, entry.progress());
	}

	@Test
	void inventoryCounterCanFallBackBelowTarget() {
		// SPEC §2.5: an INVENTORY entry that drops below its target is no longer done.
		CounterEntry entry = CounterEntry.create("Hold logs", EntryScope.WORLD, T0,
				EntryMatch.item("oak_log"), 8, CounterEntry.CountMode.INVENTORY);

		entry.setProgress(8, T0);
		assertTrue(entry.isDone());

		entry.setProgress(2, T0 + 50);
		assertFalse(entry.isDone());
		assertEquals(0L, entry.completedAt(), "completion time should clear when un-done");
	}

	@Test
	void loweringTargetBelowProgressCompletesTheEntry() {
		CounterEntry entry = CounterEntry.create("Collect", EntryScope.WORLD, T0,
				EntryMatch.item("oak_log"), 64, CounterEntry.CountMode.ACQUIRED);
		entry.addProgress(10, T0);

		entry.setTarget(10, T0 + 5);
		assertEquals(10, entry.progress());
		assertTrue(entry.isDone());
	}

	@Test
	void matchIdsAreNormalized() {
		assertEquals("minecraft:oak_log", EntryMatch.item(" Oak_Log ").id());
		assertEquals("minecraft:logs", new EntryMatch(EntryMatch.Kind.ITEM_TAG, "#minecraft:logs").id());
		assertEquals("#minecraft:logs",
				new EntryMatch(EntryMatch.Kind.ITEM_TAG, "minecraft:logs").display());
		assertTrue(EntryMatch.item("oak_log").isWellFormed());
		assertFalse(new EntryMatch(EntryMatch.Kind.ITEM, "mod:Bad Id").isWellFormed(),
				"a space should not pass as a registry id");
	}

	@Test
	void invalidEntryInputIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> TextEntry.create("   ", EntryScope.WORLD, T0));
		assertThrows(IllegalArgumentException.class,
				() -> TextEntry.create("x".repeat(TodoEntry.TEXT_MAX_LENGTH + 1), EntryScope.WORLD, T0));
		assertThrows(IllegalArgumentException.class, () -> CounterEntry.create("c", EntryScope.WORLD,
				T0, EntryMatch.item("oak_log"), 0, CounterEntry.CountMode.ACQUIRED));
		assertThrows(IllegalArgumentException.class, () -> CounterEntry.create("c", EntryScope.WORLD,
				T0, EntryMatch.item("oak_log"), 10_000, CounterEntry.CountMode.ACQUIRED));
		assertThrows(IllegalArgumentException.class,
				() -> TimerEntry.create("t", EntryScope.WORLD, T0, 500L));
		assertThrows(IllegalArgumentException.class,
				() -> TimerEntry.create("t", EntryScope.WORLD, T0, TimerEntry.DURATION_MAX_MS + 1));
	}

	@Test
	void listOrderingAndReordering() {
		TodoList list = new TodoList();
		TextEntry a = TextEntry.create("a", EntryScope.WORLD, T0);
		TextEntry b = TextEntry.create("b", EntryScope.WORLD, T0 + 1);
		TextEntry c = TextEntry.create("c", EntryScope.WORLD, T0 + 2);
		list.add(a);
		list.add(b);
		list.add(c);

		assertEquals(0, a.order());
		assertEquals(2, c.order());

		assertTrue(list.moveUp(c.id()));
		assertEquals(java.util.List.of(a, c, b), list.entries());
		assertEquals(1, c.order(), "order values should be renumbered after a move");

		assertFalse(list.moveUp(a.id()), "the first entry cannot move up");
		assertTrue(list.moveDown(a.id()));
		assertEquals(java.util.List.of(c, a, b), list.entries());
	}

	@Test
	void listDirtinessFollowsEntryMutations() {
		TodoList list = new TodoList();
		CounterEntry entry = CounterEntry.create("Collect", EntryScope.WORLD, T0,
				EntryMatch.item("oak_log"), 8, CounterEntry.CountMode.ACQUIRED);
		list.add(entry);
		list.clearDirty();
		assertFalse(list.isDirty());

		// A tracker holds the entry directly and never touches the list.
		entry.addProgress(1, T0);
		assertTrue(list.isDirty(), "an entry mutation must schedule a save");

		list.clearDirty();
		assertFalse(list.isDirty());
		assertFalse(entry.isDirty());
	}

	@Test
	void clearCompletedRemovesOnlyDoneEntries() {
		TodoList list = new TodoList();
		TextEntry done = TextEntry.create("done", EntryScope.WORLD, T0);
		TextEntry open = TextEntry.create("open", EntryScope.WORLD, T0 + 1);
		done.setDone(true, T0);
		list.add(done);
		list.add(open);

		assertEquals(1, list.clearCompleted());
		assertEquals(java.util.List.of(open), list.entries());
	}
}

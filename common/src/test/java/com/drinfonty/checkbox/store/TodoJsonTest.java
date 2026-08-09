package com.drinfonty.checkbox.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.drinfonty.checkbox.model.CounterEntry;
import com.drinfonty.checkbox.model.EntryMatch;
import com.drinfonty.checkbox.model.EntryScope;
import com.drinfonty.checkbox.model.TextEntry;
import com.drinfonty.checkbox.model.TimerEntry;
import com.drinfonty.checkbox.model.TodoEntry;
import com.drinfonty.checkbox.model.TodoList;
import org.junit.jupiter.api.Test;

class TodoJsonTest {
	private static final long T0 = 1_765_200_000_000L;

	@Test
	void roundTripsEveryEntryType() {
		TodoList list = new TodoList();
		TextEntry text = TextEntry.create("Build a house", EntryScope.WORLD, T0);
		CounterEntry item = CounterEntry.create("Collect oak logs", EntryScope.WORLD, T0 + 1,
				EntryMatch.item("oak_log"), 8, CounterEntry.CountMode.ACQUIRED);
		CounterEntry kill = CounterEntry.create("Kill zombies", EntryScope.WORLD, T0 + 2,
				EntryMatch.entity("zombie"), 10, CounterEntry.CountMode.ACQUIRED);
		TimerEntry timer = TimerEntry.create("Furnace batch", EntryScope.GLOBAL, T0 + 3, 300_000L);

		item.addProgress(3, T0);
		kill.addProgress(10, T0);
		timer.start(T0);

		list.add(text);
		list.add(item);
		list.add(kill);
		list.add(timer);

		TodoJson.ParseResult result = TodoJson.parse(TodoJson.write(list));
		assertFalse(result.damaged());
		assertEquals(0, result.skipped());
		assertEquals(4, result.list().size());

		CounterEntry loadedItem = (CounterEntry) result.list().find(item.id()).orElseThrow();
		assertEquals("minecraft:oak_log", loadedItem.match().id());
		assertEquals(EntryMatch.Kind.ITEM, loadedItem.match().kind());
		assertEquals(3, loadedItem.progress());
		assertEquals(8, loadedItem.target());
		assertEquals(CounterEntry.CountMode.ACQUIRED, loadedItem.countMode());
		assertFalse(loadedItem.isDone());

		CounterEntry loadedKill = (CounterEntry) result.list().find(kill.id()).orElseThrow();
		assertTrue(loadedKill.isDone());
		assertEquals(kill.completedAt(), loadedKill.completedAt());

		TimerEntry loadedTimer = (TimerEntry) result.list().find(timer.id()).orElseThrow();
		assertEquals(TimerEntry.State.RUNNING, loadedTimer.state());
		assertEquals(timer.endsAtEpochMillis(), loadedTimer.endsAtEpochMillis());
		assertEquals(EntryScope.GLOBAL, loadedTimer.scope());

		assertInstanceOf(TextEntry.class, result.list().find(text.id()).orElseThrow());
		assertFalse(result.list().isDirty(), "a freshly loaded list must not want saving");
	}

	@Test
	void loadedEntriesKeepTheirRelativeOrder() {
		String json = """
				{
				  "schemaVersion": 1,
				  "entries": [
				    {"id":"11111111-1111-1111-1111-111111111111","type":"TEXT","text":"third","order":9},
				    {"id":"22222222-2222-2222-2222-222222222222","type":"TEXT","text":"first","order":0},
				    {"id":"33333333-3333-3333-3333-333333333333","type":"TEXT","text":"second","order":4}
				  ]
				}
				""";

		TodoList list = TodoJson.parse(json).list();
		assertEquals(java.util.List.of("first", "second", "third"),
				list.entries().stream().map(TodoEntry::text).toList());
		assertEquals(java.util.List.of(0, 1, 2),
				list.entries().stream().map(TodoEntry::order).toList(),
				"gaps in stored order values should be renumbered");
	}

	@Test
	void malformedJsonIsReportedAsDamagedRatherThanThrowing() {
		TodoJson.ParseResult result = TodoJson.parse("{\"entries\": [ this is not json");
		assertTrue(result.damaged());
		assertTrue(result.list().isEmpty());
	}

	@Test
	void emptyAndMissingDocumentsAreNotDamaged() {
		assertFalse(TodoJson.parse("").damaged());
		assertFalse(TodoJson.parse(null).damaged());
		assertFalse(TodoJson.parse("{\"schemaVersion\":1}").damaged());
		assertTrue(TodoJson.parse("[]").damaged(), "a non-object root is unusable");
	}

	@Test
	void outOfRangeFieldsAreClampedRatherThanDropped() {
		String json = """
				{
				  "entries": [
				    {"id":"bogus-uuid","type":"COUNTER","text":"  ","order":-5,
				     "match":{"kind":"ITEM","id":"Oak_Log"},
				     "target":99999,"progress":-3,"countMode":"NONSENSE"},
				    {"type":"TIMER","text":"t","durationMillis":999999999999,"state":"WAT"}
				  ]
				}
				""";

		TodoJson.ParseResult result = TodoJson.parse(json);
		assertFalse(result.damaged());
		assertEquals(2, result.list().size(), "repairable entries must survive");

		CounterEntry counter = (CounterEntry) result.list().entries().get(0);
		assertEquals("(untitled)", counter.text());
		assertEquals(CounterEntry.TARGET_MAX, counter.target());
		assertEquals(0, counter.progress());
		assertEquals(CounterEntry.CountMode.ACQUIRED, counter.countMode());
		assertEquals("minecraft:oak_log", counter.match().id());
		assertEquals(0, counter.order());

		TimerEntry timer = (TimerEntry) result.list().entries().get(1);
		assertEquals(TimerEntry.DURATION_MAX_MS, timer.durationMillis());
		assertEquals(TimerEntry.State.IDLE, timer.state());
	}

	@Test
	void unrecoverableEntriesAreSkippedWithoutLosingTheRest() {
		String json = """
				{
				  "entries": [
				    {"type":"WHAT","text":"unknown type"},
				    {"type":"COUNTER","text":"no match object","target":5},
				    "not even an object",
				    {"type":"TEXT","text":"survivor"}
				  ]
				}
				""";

		TodoJson.ParseResult result = TodoJson.parse(json);
		assertFalse(result.damaged());
		assertEquals(3, result.skipped());
		assertEquals(1, result.list().size());
		assertEquals("survivor", result.list().entries().get(0).text());
	}

	@Test
	void autoLabelSurvivesARoundTripAndDefaultsOff() {
		TodoList list = new TodoList();
		CounterEntry auto = CounterEntry.create("Collect 8 Oak Log", EntryScope.WORLD, T0,
				EntryMatch.item("oak_log"), 8, CounterEntry.CountMode.ACQUIRED);
		auto.setAutoLabel(true);
		CounterEntry manual = CounterEntry.create("My own words", EntryScope.WORLD, T0 + 1,
				EntryMatch.item("stone"), 8, CounterEntry.CountMode.ACQUIRED);
		list.add(auto);
		list.add(manual);

		TodoList reloaded = TodoJson.parse(TodoJson.write(list)).list();
		assertTrue(((CounterEntry) reloaded.find(auto.id()).orElseThrow()).autoLabel());
		assertFalse(((CounterEntry) reloaded.find(manual.id()).orElseThrow()).autoLabel());

		// Entries written before the flag existed must load as manually labelled.
		String legacy = """
				{"entries":[{"type":"COUNTER","text":"old entry",
				  "match":{"kind":"ITEM","id":"minecraft:stone"},"target":4}]}
				""";
		CounterEntry old = (CounterEntry) TodoJson.parse(legacy).list().entries().get(0);
		assertFalse(old.autoLabel());
		assertEquals("old entry", old.text());
	}

	@Test
	void unresolvableRegistryIdsSurviveARoundTrip() {
		// SPEC §2.2: an id the client cannot resolve is preserved, not deleted.
		TodoList list = new TodoList();
		list.add(CounterEntry.create("Collect widgets", EntryScope.WORLD, T0,
				EntryMatch.item("some_removed_mod:widget"), 4, CounterEntry.CountMode.ACQUIRED));

		TodoList reloaded = TodoJson.parse(TodoJson.write(list)).list();
		CounterEntry entry = (CounterEntry) reloaded.entries().get(0);
		assertEquals("some_removed_mod:widget", entry.match().id());
	}
}

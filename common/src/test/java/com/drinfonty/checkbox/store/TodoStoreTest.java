package com.drinfonty.checkbox.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.drinfonty.checkbox.model.CounterEntry;
import com.drinfonty.checkbox.model.EntryMatch;
import com.drinfonty.checkbox.model.EntryScope;
import com.drinfonty.checkbox.model.TextEntry;
import com.drinfonty.checkbox.model.TodoEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TodoStoreTest {
	private static final long T0 = 1_765_200_000_000L;

	/** A hand-cranked clock, so debounce behaviour is tested without sleeping. */
	private static final class FakeClock implements java.util.function.LongSupplier {
		private long now = T0;

		@Override
		public long getAsLong() {
			return now;
		}

		void advance(long millis) {
			now += millis;
		}
	}

	private static Path listFile(Path configDir, String relative) {
		return configDir.resolve("checkbox").resolve("lists").resolve(relative);
	}

	@Test
	void writesToTheScopedPath(@TempDir Path configDir) {
		TodoStore store = new TodoStore(configDir, new FakeClock());
		store.open(StoreScope.singleplayer("New World"));
		store.add(TextEntry.create("Build a house", EntryScope.WORLD, T0));
		store.flush(true);

		assertTrue(Files.isRegularFile(listFile(configDir, "sp/new_world.json")));
	}

	@Test
	void reloadsWhatItWrote(@TempDir Path configDir) {
		TodoStore store = new TodoStore(configDir, new FakeClock());
		store.open(StoreScope.multiplayer("play.example.com:25565"));
		CounterEntry counter = CounterEntry.create("Collect oak logs", EntryScope.WORLD, T0,
				EntryMatch.item("oak_log"), 8, CounterEntry.CountMode.ACQUIRED);
		counter.addProgress(5, T0);
		store.add(counter);
		store.close();

		TodoStore reopened = new TodoStore(configDir, new FakeClock());
		reopened.open(StoreScope.multiplayer("play.example.com:25565"));
		assertEquals(1, reopened.entries().size());
		assertEquals(5, ((CounterEntry) reopened.entries().get(0)).progress());
	}

	@Test
	void worldAndGlobalListsAreSeparateFilesAndMergeWorldFirst(@TempDir Path configDir) {
		TodoStore store = new TodoStore(configDir, new FakeClock());
		store.open(StoreScope.singleplayer("world"));
		store.add(TextEntry.create("global one", EntryScope.GLOBAL, T0));
		store.add(TextEntry.create("world one", EntryScope.WORLD, T0 + 1));
		store.flush(true);

		assertTrue(Files.isRegularFile(listFile(configDir, "sp/world.json")));
		assertTrue(Files.isRegularFile(listFile(configDir, "global.json")));
		assertEquals(List.of("world one", "global one"),
				store.entries().stream().map(TodoEntry::text).toList());

		// The global list must follow the player to another world.
		store.close();
		TodoStore other = new TodoStore(configDir, new FakeClock());
		other.open(StoreScope.singleplayer("a different world"));
		assertEquals(List.of("global one"),
				other.entries().stream().map(TodoEntry::text).toList());
	}

	@Test
	void movingAnEntryBetweenScopesMovesItBetweenFiles(@TempDir Path configDir) {
		TodoStore store = new TodoStore(configDir, new FakeClock());
		store.open(StoreScope.singleplayer("world"));
		TextEntry entry = TextEntry.create("promote me", EntryScope.WORLD, T0);
		store.add(entry);

		assertTrue(store.moveToScope(entry.id(), EntryScope.GLOBAL));
		assertTrue(store.listFor(EntryScope.WORLD).isEmpty());
		assertEquals(1, store.listFor(EntryScope.GLOBAL).size());
		store.close();

		TodoStore reopened = new TodoStore(configDir, new FakeClock());
		reopened.open(StoreScope.singleplayer("world"));
		assertEquals(EntryScope.GLOBAL, reopened.entries().get(0).scope());
	}

	@Test
	void globalWorldScopeSharesOneFileWithoutDuplicating(@TempDir Path configDir) {
		// Realms and unidentifiable connections fall back to global for both lists.
		TodoStore store = new TodoStore(configDir, new FakeClock());
		store.open(StoreScope.global());
		store.add(TextEntry.create("world", EntryScope.WORLD, T0));
		store.add(TextEntry.create("global", EntryScope.GLOBAL, T0 + 1));
		store.close();

		TodoStore reopened = new TodoStore(configDir, new FakeClock());
		reopened.open(StoreScope.global());
		assertEquals(2, reopened.entries().size(), "entries must not be written twice or lost");
	}

	@Test
	void savesAreDebouncedButFlushOnClose(@TempDir Path configDir) {
		FakeClock clock = new FakeClock();
		TodoStore store = new TodoStore(configDir, clock);
		store.open(StoreScope.singleplayer("world"));
		Path file = listFile(configDir, "sp/world.json");

		store.add(TextEntry.create("first", EntryScope.WORLD, T0));
		store.tick();
		assertFalse(Files.exists(file), "a change must not hit the disk immediately");

		clock.advance(TodoStore.FLUSH_INTERVAL_MS);
		store.tick();
		assertTrue(Files.exists(file), "the debounce window should have elapsed");
		assertFalse(store.isDirty());

		store.add(TextEntry.create("second", EntryScope.WORLD, T0 + 1));
		store.tick();
		assertTrue(store.isDirty(), "still inside the next debounce window");
		store.close();
		assertFalse(store.isDirty(), "closing must flush unconditionally");
	}

	@Test
	void aCleanListIsNeverRewritten(@TempDir Path configDir) throws IOException {
		TodoStore store = new TodoStore(configDir, new FakeClock());
		store.open(StoreScope.singleplayer("world"));
		store.add(TextEntry.create("entry", EntryScope.WORLD, T0));
		store.flush(true);

		Path file = listFile(configDir, "sp/world.json");
		long modified = Files.getLastModifiedTime(file).toMillis();
		Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(modified - 10_000L));

		store.flush(true);
		assertEquals(modified - 10_000L, Files.getLastModifiedTime(file).toMillis(),
				"flushing a clean list should not touch the file");
	}

	@Test
	void aCorruptFileIsBackedUpAndLeftAloneUntilAChange(@TempDir Path configDir) throws IOException {
		Path file = listFile(configDir, "sp/world.json");
		Files.createDirectories(file.getParent());
		String garbage = "{ this is not json at all";
		Files.writeString(file, garbage, StandardCharsets.UTF_8);

		TodoStore store = new TodoStore(configDir, new FakeClock());
		store.open(StoreScope.singleplayer("world"));

		assertTrue(store.isDamaged());
		assertTrue(store.entries().isEmpty(), "an unreadable list starts empty");
		assertEquals(garbage, Files.readString(file), "the original must be left intact");
		assertEquals(garbage, Files.readString(file.resolveSibling(file.getFileName() + ".corrupt")),
				"a copy of the unreadable file should be kept");

		// Closing without a change must not overwrite it either.
		store.close();
		assertEquals(garbage, Files.readString(file));

		// Once the player edits the list, saving is expected.
		store.open(StoreScope.singleplayer("world"));
		store.add(TextEntry.create("new entry", EntryScope.WORLD, T0));
		store.close();
		assertTrue(Files.readString(file).contains("new entry"));
	}

	@Test
	void noTempFilesAreLeftBehind(@TempDir Path configDir) throws IOException {
		TodoStore store = new TodoStore(configDir, new FakeClock());
		store.open(StoreScope.singleplayer("world"));
		store.add(TextEntry.create("entry", EntryScope.WORLD, T0));
		store.close();

		try (var files = Files.walk(configDir)) {
			assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
		}
	}
}

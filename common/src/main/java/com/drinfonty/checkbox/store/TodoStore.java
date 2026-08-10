package com.drinfonty.checkbox.store;

import com.drinfonty.checkbox.Checkbox;
import com.drinfonty.checkbox.model.EntryScope;
import com.drinfonty.checkbox.model.TodoEntry;
import com.drinfonty.checkbox.model.TodoList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Owns the two lists that are live at any moment - the current world's and the global one -
 * and persists them (SPEC §7).
 *
 * <p>Writes are debounced and atomic. Debounced because progress can tick several times a
 * second and the disk should not; atomic because the alternative is a truncated list file
 * after a crash, which costs the player everything they wrote down.
 *
 * <p>Nothing here writes unless something is dirty, which is also what satisfies the rule
 * that a malformed file must survive until the player actually changes something.
 *
 * <p>Merged views list world entries before global ones. The two files number their entries
 * independently, so there is no meaningful way to interleave them, and reordering acts within
 * a scope.
 */
public final class TodoStore {
	public static final long FLUSH_INTERVAL_MS = 5_000L;

	private final Path listsDir;
	private final LongSupplier clock;

	private StoreScope worldScope = StoreScope.global();
	private TodoList worldList = new TodoList();
	private TodoList globalList = new TodoList();
	private boolean worldDamaged;
	private boolean globalDamaged;
	private boolean open;
	private long lastFlushMillis;

	public TodoStore(Path configDir) {
		this(configDir, System::currentTimeMillis);
	}

	public TodoStore(Path configDir, LongSupplier clock) {
		this.listsDir = configDir.resolve("checkbox").resolve("lists");
		this.clock = clock;
	}

	/** Loads the given world scope plus the global list. Flushes any previous scope first. */
	public void open(StoreScope scope) {
		close();

		this.worldScope = scope == null ? StoreScope.global() : scope;
		this.globalList = read(StoreScope.global(), damaged -> globalDamaged = damaged);

		if (this.worldScope.equals(StoreScope.global())) {
			// Realms and anything else we cannot identify falls back to the global list.
			// Sharing the instance keeps one file from being written by two lists.
			this.worldList = this.globalList;
			this.worldDamaged = this.globalDamaged;
		} else {
			this.worldList = read(this.worldScope, damaged -> worldDamaged = damaged);
		}

		this.open = true;
		this.lastFlushMillis = clock.getAsLong();

		if (Checkbox.debug()) {
			Checkbox.LOGGER.info("Opened Checkbox lists: {} ({} entries), global ({} entries)",
					worldScope, worldList.size(), globalList.size());
		}
	}

	/** Flushes and drops the loaded lists. Safe to call when nothing is open. */
	public void close() {
		if (open) {
			flush(true);
		}
		open = false;
		worldScope = StoreScope.global();
		worldList = new TodoList();
		globalList = new TodoList();
		worldDamaged = false;
		globalDamaged = false;
	}

	public boolean isOpen() {
		return open;
	}

	public StoreScope worldScope() {
		return worldScope;
	}

	/** True if either loaded file was unreadable, so the UI can warn instead of staying quiet. */
	public boolean isDamaged() {
		return worldDamaged || globalDamaged;
	}

	public TodoList listFor(EntryScope scope) {
		return scope == EntryScope.GLOBAL ? globalList : worldList;
	}

	/** World entries followed by global ones. */
	public List<TodoEntry> entries() {
		if (worldList == globalList) {
			return worldList.entries();
		}
		List<TodoEntry> merged = new ArrayList<>(worldList.size() + globalList.size());
		merged.addAll(worldList.entries());
		merged.addAll(globalList.entries());
		return Collections.unmodifiableList(merged);
	}

	public void add(TodoEntry entry) {
		if (entry != null) {
			listFor(entry.scope()).add(entry);
		}
	}

	public boolean remove(UUID id) {
		boolean removed = worldList.remove(id);
		if (!removed && worldList != globalList) {
			removed = globalList.remove(id);
		}
		return removed;
	}

	public Optional<TodoEntry> find(UUID id) {
		Optional<TodoEntry> found = worldList.find(id);
		if (found.isEmpty() && worldList != globalList) {
			found = globalList.find(id);
		}
		return found;
	}

	/** Moves an entry between the world and global lists, preserving its state. */
	public boolean moveToScope(UUID id, EntryScope target) {
		Optional<TodoEntry> found = find(id);
		if (found.isEmpty() || target == null) {
			return false;
		}
		TodoEntry entry = found.get();
		if (entry.scope() == target) {
			return false;
		}
		listFor(entry.scope()).remove(id);
		entry.setScope(target);
		listFor(target).add(entry);
		return true;
	}

	public int clearCompleted() {
		int removed = worldList.clearCompleted();
		if (worldList != globalList) {
			removed += globalList.clearCompleted();
		}
		return removed;
	}

	public boolean isDirty() {
		return worldList.isDirty() || globalList.isDirty();
	}

	/** Call once per client tick; writes at most once per {@link #FLUSH_INTERVAL_MS}. */
	public void tick() {
		if (!open || !isDirty()) {
			return;
		}
		long now = clock.getAsLong();
		// A clock that jumped backwards (NTP correction) would otherwise stall saves.
		if (now - lastFlushMillis >= FLUSH_INTERVAL_MS || now < lastFlushMillis) {
			flush(false);
		}
	}

	/** Writes every dirty list now. */
	public void flush(boolean force) {
		if (!open && !force) {
			return;
		}
		if (worldList.isDirty()) {
			write(worldScope, worldList);
		}
		if (worldList != globalList && globalList.isDirty()) {
			write(StoreScope.global(), globalList);
		}
		lastFlushMillis = clock.getAsLong();
	}

	private TodoList read(StoreScope scope, java.util.function.Consumer<Boolean> damagedSink) {
		Path file = listsDir.resolve(scope.relativePath());
		if (!Files.isRegularFile(file)) {
			damagedSink.accept(false);
			return new TodoList();
		}

		String json;
		try {
			json = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			Checkbox.LOGGER.error("Could not read Checkbox list {}: {}", file, e.toString());
			damagedSink.accept(true);
			return new TodoList();
		}

		TodoJson.ParseResult result = TodoJson.parse(json);
		if (result.damaged()) {
			Checkbox.LOGGER.error("Checkbox list {} is unreadable; starting empty. The file is "
					+ "left untouched until you change something, and a copy was kept.", file);
			backup(file);
		} else if (result.skipped() > 0) {
			Checkbox.LOGGER.warn("Dropped {} unreadable entries from Checkbox list {}",
					result.skipped(), file);
		}
		damagedSink.accept(result.damaged());
		return result.list();
	}

	private void write(StoreScope scope, TodoList list) {
		Path file = listsDir.resolve(scope.relativePath());
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(temp, TodoJson.write(list), StandardCharsets.UTF_8);
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			list.clearDirty();
			if (Checkbox.debug()) {
				Checkbox.LOGGER.info("Saved Checkbox list {} ({} entries)", scope, list.size());
			}
		} catch (IOException e) {
			// Leave the list dirty so the next flush retries rather than losing the edit.
			Checkbox.LOGGER.error("Could not save Checkbox list {}: {}", file, e.toString());
			try {
				Files.deleteIfExists(temp);
			} catch (IOException ignored) {
				// Nothing useful to do; the next write overwrites it anyway.
			}
		}
	}

	/** Keeps a copy of an unreadable file so a later save cannot be the end of it. */
	private void backup(Path file) {
		Path corrupt = file.resolveSibling(file.getFileName() + ".corrupt");
		try {
			Files.copy(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
			Checkbox.LOGGER.error("Kept a copy of the unreadable list at {}", corrupt);
		} catch (IOException e) {
			Checkbox.LOGGER.error("Could not back up unreadable list {}: {}", file, e.toString());
		}
	}
}

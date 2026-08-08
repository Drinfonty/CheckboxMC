package com.drinfonty.checkbox.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * An ordered list of entries backing one storage file.
 *
 * <p>Dirtiness is the union of the list's own structural changes and the dirty flags of the
 * entries in it, so a tracker advancing progress on an entry it holds directly still schedules
 * a save.
 */
public final class TodoList {
	private final List<TodoEntry> entries = new ArrayList<>();
	private boolean structurallyDirty;

	public List<TodoEntry> entries() {
		return Collections.unmodifiableList(entries);
	}

	public int size() {
		return entries.size();
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public Optional<TodoEntry> find(UUID id) {
		return entries.stream().filter(entry -> entry.id().equals(id)).findFirst();
	}

	/** Appends to the end of the list. */
	public void add(TodoEntry entry) {
		if (entry == null || find(entry.id()).isPresent()) {
			return;
		}
		entry.setOrder(entries.size());
		entries.add(entry);
		structurallyDirty = true;
	}

	/**
	 * Adds an entry keeping the order value it already carries, for loading a batch off disk.
	 * Call {@link #normalizeOrder()} once the batch is complete - renumbering mid-batch would
	 * overwrite the stored order of the entries read so far with their read order.
	 */
	public void addPreservingOrder(TodoEntry entry) {
		if (entry != null && find(entry.id()).isEmpty()) {
			entries.add(entry);
			structurallyDirty = true;
		}
	}

	public boolean remove(UUID id) {
		boolean removed = entries.removeIf(entry -> entry.id().equals(id));
		if (removed) {
			renumber();
			structurallyDirty = true;
		}
		return removed;
	}

	public boolean moveUp(UUID id) {
		return swap(indexOf(id), indexOf(id) - 1);
	}

	public boolean moveDown(UUID id) {
		return swap(indexOf(id), indexOf(id) + 1);
	}

	public int clearCompleted() {
		int before = entries.size();
		entries.removeIf(TodoEntry::isDone);
		int removed = before - entries.size();
		if (removed > 0) {
			renumber();
			structurallyDirty = true;
		}
		return removed;
	}

	/**
	 * Sorts by stored order, then renumbers 0..n-1. For use after loading a batch, where the
	 * order values are authoritative and may have gaps or duplicates.
	 */
	public void normalizeOrder() {
		entries.sort((a, b) -> {
			int byOrder = Integer.compare(a.order(), b.order());
			return byOrder != 0 ? byOrder : Long.compare(a.createdAt(), b.createdAt());
		});
		renumber();
	}

	/**
	 * Renumbers order values from list position. The list position is authoritative here -
	 * sorting by the stored order first would undo the move that prompted the renumber.
	 */
	private void renumber() {
		for (int i = 0; i < entries.size(); i++) {
			entries.get(i).setOrder(i);
		}
	}

	public boolean isDirty() {
		return structurallyDirty || entries.stream().anyMatch(TodoEntry::isDirty);
	}

	public void markDirty() {
		structurallyDirty = true;
	}

	public void clearDirty() {
		structurallyDirty = false;
		entries.forEach(TodoEntry::clearDirty);
	}

	private int indexOf(UUID id) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private boolean swap(int from, int to) {
		if (from < 0 || to < 0 || from >= entries.size() || to >= entries.size() || from == to) {
			return false;
		}
		Collections.swap(entries, from, to);
		renumber();
		structurallyDirty = true;
		return true;
	}
}

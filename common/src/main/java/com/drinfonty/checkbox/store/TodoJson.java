package com.drinfonty.checkbox.store;

import com.drinfonty.checkbox.Checkbox;
import com.drinfonty.checkbox.model.CounterEntry;
import com.drinfonty.checkbox.model.EntryMatch;
import com.drinfonty.checkbox.model.EntryScope;
import com.drinfonty.checkbox.model.TextEntry;
import com.drinfonty.checkbox.model.TimerEntry;
import com.drinfonty.checkbox.model.TodoEntry;
import com.drinfonty.checkbox.model.TodoList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;

/**
 * Reads and writes the list file format (SPEC §7).
 *
 * <p>Hand-rolled against {@link JsonObject} rather than Gson's reflective binding. The format
 * is polymorphic on {@code type}, and more importantly every field has to be repairable: a
 * player's todo list is not worth throwing away because one integer is out of range. Anything
 * unreadable is clamped or defaulted; only an entry with no recoverable identity is dropped.
 */
public final class TodoJson {
	public static final int SCHEMA_VERSION = 1;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private TodoJson() {
	}

	/**
	 * @param list      the entries that could be read, never null
	 * @param damaged   true if the document itself was unusable, as opposed to individual
	 *                  entries being repaired
	 * @param skipped   how many entries could not be recovered at all
	 */
	public record ParseResult(TodoList list, boolean damaged, int skipped) {
	}

	public static ParseResult parse(String json) {
		TodoList list = new TodoList();
		if (json == null || json.isBlank()) {
			return new ParseResult(list, false, 0);
		}

		JsonObject root;
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				return new ParseResult(list, true, 0);
			}
			root = parsed.getAsJsonObject();
		} catch (RuntimeException e) {
			Checkbox.LOGGER.warn("Checkbox list file is not valid JSON: {}", e.getMessage());
			return new ParseResult(list, true, 0);
		}

		JsonElement entriesElement = root.get("entries");
		if (entriesElement == null || !entriesElement.isJsonArray()) {
			// A document with no entries array is not corrupt, just empty.
			return new ParseResult(list, false, 0);
		}

		int skipped = 0;
		JsonArray entries = entriesElement.getAsJsonArray();
		for (JsonElement element : entries) {
			if (!element.isJsonObject()) {
				skipped++;
				continue;
			}
			TodoEntry entry = parseEntry(element.getAsJsonObject());
			if (entry == null) {
				skipped++;
			} else {
				list.addPreservingOrder(entry);
			}
		}

		list.normalizeOrder();
		list.clearDirty();
		return new ParseResult(list, false, skipped);
	}

	private static TodoEntry parseEntry(JsonObject obj) {
		TodoEntry.Type type = TodoEntry.Type.parse(string(obj, "type", null));
		if (type == null) {
			Checkbox.LOGGER.warn("Skipping Checkbox entry with unknown type: {}",
					string(obj, "type", "<missing>"));
			return null;
		}

		UUID id = uuid(obj, "id");
		String text = TodoEntry.coerceText(string(obj, "text", null));
		EntryScope scope = EntryScope.parse(string(obj, "scope", null), EntryScope.WORLD);
		long createdAt = positiveLong(obj, "createdAt", 0L);
		int order = (int) clamp(longValue(obj, "order", 0L), 0, Integer.MAX_VALUE);
		long completedAt = positiveLong(obj, "completedAt", 0L);
		boolean done = bool(obj, "done", false);

		try {
			return switch (type) {
				case TEXT -> TextEntry.restored(id, text, scope, createdAt, order, done, completedAt);
				case COUNTER -> parseCounter(obj, id, text, scope, createdAt, order, completedAt);
				case TIMER -> parseTimer(obj, id, text, scope, createdAt, order, completedAt);
			};
		} catch (RuntimeException e) {
			Checkbox.LOGGER.warn("Skipping unreadable Checkbox entry '{}': {}", text, e.getMessage());
			return null;
		}
	}

	private static CounterEntry parseCounter(JsonObject obj, UUID id, String text, EntryScope scope,
			long createdAt, int order, long completedAt) {
		JsonObject matchObj = obj.has("match") && obj.get("match").isJsonObject()
				? obj.getAsJsonObject("match")
				: null;
		if (matchObj == null) {
			throw new IllegalArgumentException("counter entry has no match object");
		}

		EntryMatch.Kind kind = EntryMatch.Kind.parse(string(matchObj, "kind", null), null);
		String matchId = string(matchObj, "id", null);
		if (kind == null || matchId == null || matchId.isBlank()) {
			throw new IllegalArgumentException("counter entry has an incomplete match");
		}

		// An id that no longer resolves is kept verbatim; only a structurally impossible one
		// is rejected. See EntryMatch's class comment.
		EntryMatch match = new EntryMatch(kind, matchId);
		int target = CounterEntry.coerceTarget((int) clamp(longValue(obj, "target", 1L), 1, CounterEntry.TARGET_MAX));
		int progress = (int) clamp(longValue(obj, "progress", 0L), 0, target);
		CounterEntry.CountMode mode = CounterEntry.CountMode.parse(
				string(obj, "countMode", null), CounterEntry.CountMode.ACQUIRED);

		return CounterEntry.restored(id, text, scope, createdAt, order, match, target, progress,
				mode, bool(obj, "autoLabel", false), completedAt);
	}

	private static TimerEntry parseTimer(JsonObject obj, UUID id, String text, EntryScope scope,
			long createdAt, int order, long completedAt) {
		long duration = TimerEntry.coerceDuration(longValue(obj, "durationMillis", TimerEntry.DURATION_MIN_MS));
		TimerEntry.State state = TimerEntry.State.parse(string(obj, "state", null), TimerEntry.State.IDLE);
		long endsAt = positiveLong(obj, "endsAtEpochMillis", 0L);
		long remaining = positiveLong(obj, "remainingMillis", duration);

		return TimerEntry.restored(id, text, scope, createdAt, order, duration, state, endsAt,
				remaining, completedAt);
	}

	public static String write(TodoList list) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);

		JsonArray entries = new JsonArray();
		for (TodoEntry entry : list.entries()) {
			entries.add(writeEntry(entry));
		}
		root.add("entries", entries);
		return GSON.toJson(root);
	}

	private static JsonObject writeEntry(TodoEntry entry) {
		JsonObject obj = new JsonObject();
		obj.addProperty("id", entry.id().toString());
		obj.addProperty("type", entry.type().name());
		obj.addProperty("text", entry.text());
		obj.addProperty("order", entry.order());
		obj.addProperty("scope", entry.scope().name());
		obj.addProperty("done", entry.isDone());
		obj.addProperty("createdAt", entry.createdAt());
		if (entry.completedAt() > 0L) {
			obj.addProperty("completedAt", entry.completedAt());
		} else {
			obj.add("completedAt", null);
		}

		switch (entry) {
			case TextEntry ignored -> {
			}
			case CounterEntry counter -> {
				JsonObject match = new JsonObject();
				match.addProperty("kind", counter.match().kind().name());
				match.addProperty("id", counter.match().id());
				obj.add("match", match);
				obj.addProperty("target", counter.target());
				obj.addProperty("progress", counter.progress());
				obj.addProperty("countMode", counter.countMode().name());
				if (counter.autoLabel()) {
					obj.addProperty("autoLabel", true);
				}
			}
			case TimerEntry timer -> {
				obj.addProperty("durationMillis", timer.durationMillis());
				obj.addProperty("state", timer.state().name());
				if (timer.state() == TimerEntry.State.RUNNING) {
					obj.addProperty("endsAtEpochMillis", timer.endsAtEpochMillis());
					obj.add("remainingMillis", null);
				} else {
					obj.add("endsAtEpochMillis", null);
					obj.addProperty("remainingMillis", timer.storedRemainingMillis());
				}
			}
		}
		return obj;
	}

	private static String string(JsonObject obj, String key, String fallback) {
		JsonElement element = obj.get(key);
		if (element == null || !element.isJsonPrimitive()) {
			return fallback;
		}
		try {
			return element.getAsString();
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static boolean bool(JsonObject obj, String key, boolean fallback) {
		JsonElement element = obj.get(key);
		if (element == null || !element.isJsonPrimitive()) {
			return fallback;
		}
		try {
			return element.getAsBoolean();
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static long longValue(JsonObject obj, String key, long fallback) {
		JsonElement element = obj.get(key);
		if (element == null || !element.isJsonPrimitive()) {
			return fallback;
		}
		try {
			return element.getAsLong();
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static long positiveLong(JsonObject obj, String key, long fallback) {
		return Math.max(0L, longValue(obj, key, fallback));
	}

	private static UUID uuid(JsonObject obj, String key) {
		String raw = string(obj, key, null);
		if (raw == null) {
			return UUID.randomUUID();
		}
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException e) {
			return UUID.randomUUID();
		}
	}

	private static long clamp(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}
}

package com.drinfonty.checkbox.model;

import java.util.Locale;

/**
 * What a {@link CounterEntry} counts: a registry id, plus whether that id names an item, an
 * entity type, or a tag of either.
 *
 * <p>The id is held as a plain string rather than a {@code net.minecraft.resources.Identifier}
 * on purpose. It keeps the whole model layer free of Minecraft classes - so it is unit
 * testable without bootstrapping the game - and it means an entry whose id does not resolve
 * (a modded item after the mod is removed, say) survives a load/save round trip verbatim
 * instead of being silently dropped. Resolution against the registries happens in the
 * tracking layer, where a client and its registries actually exist.
 *
 * @param kind what the id names
 * @param id   a namespaced id such as {@code minecraft:oak_log}, never blank, never prefixed
 *             with {@code #} (tag-ness is carried by {@code kind})
 */
public record EntryMatch(Kind kind, String id) {
	public enum Kind {
		ITEM,
		ITEM_TAG,
		ENTITY,
		ENTITY_TAG;

		public boolean isItem() {
			return this == ITEM || this == ITEM_TAG;
		}

		public boolean isTag() {
			return this == ITEM_TAG || this == ENTITY_TAG;
		}

		public static Kind parse(String raw, Kind fallback) {
			if (raw == null) {
				return fallback;
			}
			for (Kind kind : values()) {
				if (kind.name().equalsIgnoreCase(raw)) {
					return kind;
				}
			}
			return fallback;
		}
	}

	public EntryMatch {
		if (kind == null) {
			throw new IllegalArgumentException("match kind must not be null");
		}
		id = normalize(id);
	}

	public static EntryMatch item(String id) {
		return new EntryMatch(Kind.ITEM, id);
	}

	public static EntryMatch entity(String id) {
		return new EntryMatch(Kind.ENTITY, id);
	}

	/**
	 * Lower-cases, trims, strips a leading {@code #}, and defaults a bare path to the
	 * {@code minecraft} namespace, so "Oak_Log", " oak_log " and "minecraft:oak_log" are one
	 * entry rather than three.
	 */
	public static String normalize(String raw) {
		if (raw == null) {
			throw new IllegalArgumentException("match id must not be null");
		}
		String id = raw.trim().toLowerCase(Locale.ROOT);
		if (id.startsWith("#")) {
			id = id.substring(1);
		}
		if (id.isEmpty()) {
			throw new IllegalArgumentException("match id must not be blank");
		}
		if (id.indexOf(':') < 0) {
			id = "minecraft:" + id;
		}
		return id;
	}

	/** {@code true} if the id is at least shaped like a valid registry id. */
	public boolean isWellFormed() {
		int colon = id.indexOf(':');
		if (colon <= 0 || colon == id.length() - 1) {
			return false;
		}
		return id.chars().allMatch(c -> c == ':' || c == '_' || c == '-' || c == '.' || c == '/'
				|| (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'));
	}

	/** The id as a player would type it, with a {@code #} for tags. */
	public String display() {
		return kind.isTag() ? "#" + id : id;
	}
}

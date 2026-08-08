package com.drinfonty.checkbox.model;

/**
 * Which list an entry is stored in. {@link #WORLD} entries live in the file for the current
 * save or server; {@link #GLOBAL} entries follow the player everywhere.
 */
public enum EntryScope {
	WORLD,
	GLOBAL;

	public static EntryScope parse(String raw, EntryScope fallback) {
		if (raw == null) {
			return fallback;
		}
		for (EntryScope scope : values()) {
			if (scope.name().equalsIgnoreCase(raw)) {
				return scope;
			}
		}
		return fallback;
	}
}

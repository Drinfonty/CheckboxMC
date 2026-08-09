package com.drinfonty.checkbox.store;

import java.util.Locale;
import java.util.Objects;

/**
 * Identifies which list file entries belong to (SPEC §7).
 *
 * <p>Deliberately free of Minecraft classes: this holds only the resolved key, so the naming
 * and sanitisation rules are unit testable. Turning a live connection into a scope is
 * {@code ScopeResolver}'s job.
 */
public final class StoreScope {
	/** Long enough to stay recognisable, short enough to survive any filesystem. */
	private static final int MAX_KEY_LENGTH = 64;

	public enum Kind {
		SINGLEPLAYER("sp"),
		MULTIPLAYER("mp"),
		GLOBAL("");

		private final String directory;

		Kind(String directory) {
			this.directory = directory;
		}

		public String directory() {
			return directory;
		}
	}

	private static final StoreScope GLOBAL = new StoreScope(Kind.GLOBAL, "global");

	private final Kind kind;
	private final String key;

	private StoreScope(Kind kind, String key) {
		this.kind = kind;
		this.key = key;
	}

	public static StoreScope global() {
		return GLOBAL;
	}

	/** @param saveName the world save folder name */
	public static StoreScope singleplayer(String saveName) {
		String key = sanitize(saveName);
		return key.isEmpty() ? GLOBAL : new StoreScope(Kind.SINGLEPLAYER, key);
	}

	/**
	 * Derives a singleplayer scope from the save directory.
	 *
	 * <p>Normalises first, because Minecraft's {@code LevelResource.ROOT} resolves to a path
	 * ending in {@code "."} - taking the file name of that raw path yields {@code "."}, which
	 * sanitises to nothing and silently sends every singleplayer world to the global list.
	 */
	public static StoreScope singleplayerFromPath(java.nio.file.Path worldRoot) {
		if (worldRoot == null) {
			return GLOBAL;
		}
		java.nio.file.Path folder = worldRoot.toAbsolutePath().normalize().getFileName();
		return folder == null ? GLOBAL : singleplayer(folder.toString());
	}

	/** @param address the server address, with or without a port */
	public static StoreScope multiplayer(String address) {
		String key = sanitize(address);
		return key.isEmpty() ? GLOBAL : new StoreScope(Kind.MULTIPLAYER, key);
	}

	public Kind kind() {
		return kind;
	}

	public String key() {
		return key;
	}

	/** Path of this scope's file, relative to the lists directory. */
	public String relativePath() {
		return kind == Kind.GLOBAL ? key + ".json" : kind.directory() + "/" + key + ".json";
	}

	/**
	 * Reduces an arbitrary world or server name to {@code [a-z0-9._-]}.
	 *
	 * <p>Also strips path separators and any leading dots, so a hostile or merely unlucky
	 * save name such as {@code ../../.bashrc} cannot escape the lists directory.
	 */
	public static String sanitize(String raw) {
		if (raw == null) {
			return "";
		}
		String lower = raw.trim().toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(lower.length());
		boolean lastWasFill = false;
		for (int i = 0; i < lower.length(); i++) {
			char c = lower.charAt(i);
			boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
					|| c == '.' || c == '_' || c == '-';
			if (allowed) {
				out.append(c);
				lastWasFill = false;
			} else if (!lastWasFill && !out.isEmpty()) {
				out.append('_');
				lastWasFill = true;
			}
		}
		while (!out.isEmpty() && (out.charAt(0) == '.' || out.charAt(0) == '_')) {
			out.deleteCharAt(0);
		}
		while (!out.isEmpty()) {
			char last = out.charAt(out.length() - 1);
			if (last == '.' || last == '_') {
				out.deleteCharAt(out.length() - 1);
			} else {
				break;
			}
		}
		if (out.length() > MAX_KEY_LENGTH) {
			out.setLength(MAX_KEY_LENGTH);
		}
		return out.toString();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof StoreScope scope && scope.kind == kind && scope.key.equals(key);
	}

	@Override
	public int hashCode() {
		return Objects.hash(kind, key);
	}

	@Override
	public String toString() {
		return relativePath();
	}
}

package com.drinfonty.checkbox.config;

import com.drinfonty.checkbox.Checkbox;
import com.drinfonty.checkbox.hud.HudAnchor;
import com.drinfonty.checkbox.model.CounterEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * HUD and behaviour settings (SPEC §6), stored at {@code config/checkbox/config.json}.
 *
 * <p>Fields are public and plain, following {@code RedfxConfig}. Loading never throws: every
 * field is range-checked and defaulted individually by {@link #repair()}, so a hand-edited or
 * truncated config costs the player their settings, not their game.
 */
public class CheckboxConfig {
	public enum WidthMode {
		AUTO,
		FIXED
	}

	public enum BackgroundStyle {
		NONE,
		SHADOW,
		PANEL
	}

	public enum CompletedBehaviour {
		FADE,
		KEEP
	}

	public static final int SCHEMA_VERSION = 1;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Path configDir = Path.of("config");
	private static CheckboxConfig instance;

	public int schemaVersion = SCHEMA_VERSION;

	// Visibility
	public boolean hudVisible = true;
	public boolean hideWhenScreenOpen = false;
	public boolean hideWithDebugScreen = true;

	// Placement and size
	public HudAnchor anchor = HudAnchor.TOP_LEFT;
	public int offsetX = 4;
	public int offsetY = 4;
	public float scale = 1.0f;
	public WidthMode widthMode = WidthMode.AUTO;
	public int fixedWidth = 140;
	public int maxVisibleEntries = 8;

	// Appearance
	public BackgroundStyle backgroundStyle = BackgroundStyle.PANEL;
	public int backgroundOpacity = 50;
	public boolean textShadow = true;
	public boolean showTitle = true;
	public String titleText = "Checkbox";
	public boolean showProgressBar = true;
	public boolean showCompleted = true;
	public CompletedBehaviour completedBehaviour = CompletedBehaviour.FADE;
	public int completedFadeSeconds = 10;

	// Behaviour
	public boolean playSounds = true;
	public boolean showToasts = true;
	public CounterEntry.CountMode defaultCountMode = CounterEntry.CountMode.ACQUIRED;
	public int killAttributionWindowTicks = 200;
	public boolean pauseTimersOnQuit = true;
	public boolean statReconciliation = false;

	/** Overridable for tests. */
	public static void setConfigDir(Path dir) {
		configDir = dir;
		instance = null;
	}

	public static CheckboxConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static Path file() {
		return configDir.resolve("checkbox").resolve("config.json");
	}

	public static CheckboxConfig load() {
		Path file = file();
		if (Files.isRegularFile(file)) {
			try {
				String json = Files.readString(file, StandardCharsets.UTF_8);
				CheckboxConfig loaded = GSON.fromJson(json, CheckboxConfig.class);
				if (loaded != null) {
					loaded.repair();
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				Checkbox.LOGGER.error("Could not read {}; using defaults: {}", file, e.toString());
			}
		}
		CheckboxConfig fresh = new CheckboxConfig();
		fresh.save();
		return fresh;
	}

	/**
	 * Replaces anything missing or out of range with its default. Gson leaves unparseable
	 * enums and absent fields as null, and writes out-of-range numbers verbatim.
	 */
	public void repair() {
		schemaVersion = SCHEMA_VERSION;

		if (anchor == null) {
			anchor = HudAnchor.TOP_LEFT;
		}
		if (widthMode == null) {
			widthMode = WidthMode.AUTO;
		}
		if (backgroundStyle == null) {
			backgroundStyle = BackgroundStyle.PANEL;
		}
		if (completedBehaviour == null) {
			completedBehaviour = CompletedBehaviour.FADE;
		}
		if (defaultCountMode == null) {
			defaultCountMode = CounterEntry.CountMode.ACQUIRED;
		}
		if (titleText == null || titleText.isBlank()) {
			titleText = "Checkbox";
		} else if (titleText.length() > 32) {
			titleText = titleText.substring(0, 32);
		}

		offsetX = clamp(offsetX, -4096, 4096);
		offsetY = clamp(offsetY, -4096, 4096);
		scale = clamp(scale, 0.5f, 2.0f);
		fixedWidth = clamp(fixedWidth, 60, 320);
		maxVisibleEntries = clamp(maxVisibleEntries, 1, 20);
		backgroundOpacity = clamp(backgroundOpacity, 0, 100);
		completedFadeSeconds = clamp(completedFadeSeconds, 1, 60);
		killAttributionWindowTicks = clamp(killAttributionWindowTicks, 20, 6000);
	}

	public void resetToDefaults() {
		CheckboxConfig defaults = new CheckboxConfig();
		hudVisible = defaults.hudVisible;
		hideWhenScreenOpen = defaults.hideWhenScreenOpen;
		hideWithDebugScreen = defaults.hideWithDebugScreen;
		anchor = defaults.anchor;
		offsetX = defaults.offsetX;
		offsetY = defaults.offsetY;
		scale = defaults.scale;
		widthMode = defaults.widthMode;
		fixedWidth = defaults.fixedWidth;
		maxVisibleEntries = defaults.maxVisibleEntries;
		backgroundStyle = defaults.backgroundStyle;
		backgroundOpacity = defaults.backgroundOpacity;
		textShadow = defaults.textShadow;
		showTitle = defaults.showTitle;
		titleText = defaults.titleText;
		showProgressBar = defaults.showProgressBar;
		showCompleted = defaults.showCompleted;
		completedBehaviour = defaults.completedBehaviour;
		completedFadeSeconds = defaults.completedFadeSeconds;
		playSounds = defaults.playSounds;
		showToasts = defaults.showToasts;
		defaultCountMode = defaults.defaultCountMode;
		killAttributionWindowTicks = defaults.killAttributionWindowTicks;
		pauseTimersOnQuit = defaults.pauseTimersOnQuit;
		statReconciliation = defaults.statReconciliation;
	}

	public void save() {
		Path file = file();
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(temp, GSON.toJson(this), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			Checkbox.LOGGER.error("Could not save {}: {}", file, e.toString());
		}
	}

	/**
	 * Changes to any of these force the HUD to re-lay-out. Cheaper and less error-prone than
	 * asking every settings widget to remember to invalidate a cache.
	 */
	public int layoutRevision() {
		int result = anchor.ordinal();
		result = 31 * result + Float.floatToIntBits(scale);
		result = 31 * result + widthMode.ordinal();
		result = 31 * result + fixedWidth;
		result = 31 * result + maxVisibleEntries;
		result = 31 * result + backgroundStyle.ordinal();
		result = 31 * result + backgroundOpacity;
		result = 31 * result + (textShadow ? 1 : 0);
		result = 31 * result + (showTitle ? 1 : 0);
		result = 31 * result + titleText.hashCode();
		result = 31 * result + (showProgressBar ? 1 : 0);
		result = 31 * result + (showCompleted ? 1 : 0);
		result = 31 * result + completedBehaviour.ordinal();
		result = 31 * result + completedFadeSeconds;
		return result;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}

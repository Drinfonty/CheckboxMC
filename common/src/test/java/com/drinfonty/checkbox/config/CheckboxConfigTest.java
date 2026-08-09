package com.drinfonty.checkbox.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.drinfonty.checkbox.hud.HudAnchor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckboxConfigTest {
	private static void write(Path configDir, String json) throws IOException {
		Path file = configDir.resolve("checkbox").resolve("config.json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, json, StandardCharsets.UTF_8);
	}

	@Test
	void missingConfigIsCreatedWithDefaults(@TempDir Path configDir) {
		CheckboxConfig.setConfigDir(configDir);
		CheckboxConfig config = CheckboxConfig.get();

		assertTrue(config.hudVisible);
		assertEquals(HudAnchor.TOP_LEFT, config.anchor);
		assertEquals(1.0f, config.scale);
		assertTrue(Files.isRegularFile(CheckboxConfig.file()), "defaults are written out");
	}

	@Test
	void savedSettingsSurviveAReload(@TempDir Path configDir) {
		CheckboxConfig.setConfigDir(configDir);
		CheckboxConfig config = CheckboxConfig.get();
		config.anchor = HudAnchor.BOTTOM_RIGHT;
		config.scale = 1.5f;
		config.hudVisible = false;
		config.titleText = "Quests";
		config.save();

		CheckboxConfig.setConfigDir(configDir);
		CheckboxConfig reloaded = CheckboxConfig.get();
		assertEquals(HudAnchor.BOTTOM_RIGHT, reloaded.anchor);
		assertEquals(1.5f, reloaded.scale);
		assertFalse(reloaded.hudVisible);
		assertEquals("Quests", reloaded.titleText);
	}

	@Test
	void outOfRangeValuesAreClampedNotRejected(@TempDir Path configDir) throws IOException {
		write(configDir, """
				{
				  "scale": 99.0,
				  "maxVisibleEntries": 0,
				  "backgroundOpacity": 5000,
				  "fixedWidth": 3,
				  "completedFadeSeconds": -4,
				  "offsetX": 999999,
				  "killAttributionWindowTicks": 1
				}
				""");
		CheckboxConfig.setConfigDir(configDir);
		CheckboxConfig config = CheckboxConfig.get();

		assertEquals(2.0f, config.scale);
		assertEquals(1, config.maxVisibleEntries);
		assertEquals(100, config.backgroundOpacity);
		assertEquals(60, config.fixedWidth);
		assertEquals(1, config.completedFadeSeconds);
		assertEquals(4096, config.offsetX);
		assertEquals(20, config.killAttributionWindowTicks);
	}

	@Test
	void unknownEnumsAndBlanksFallBackToDefaults(@TempDir Path configDir) throws IOException {
		write(configDir, """
				{"anchor": null, "backgroundStyle": null, "titleText": "   ", "widthMode": null}
				""");
		CheckboxConfig.setConfigDir(configDir);
		CheckboxConfig config = CheckboxConfig.get();

		assertEquals(HudAnchor.TOP_LEFT, config.anchor);
		assertEquals(CheckboxConfig.BackgroundStyle.PANEL, config.backgroundStyle);
		assertEquals(CheckboxConfig.WidthMode.AUTO, config.widthMode);
		assertEquals("Checkbox", config.titleText);
	}

	@Test
	void malformedConfigDoesNotPreventStartup(@TempDir Path configDir) throws IOException {
		write(configDir, "{ this is not json");
		CheckboxConfig.setConfigDir(configDir);

		CheckboxConfig config = CheckboxConfig.get();
		assertEquals(HudAnchor.TOP_LEFT, config.anchor);
		assertTrue(config.hudVisible);
	}

	@Test
	void resetRestoresEveryField(@TempDir Path configDir) {
		CheckboxConfig.setConfigDir(configDir);
		CheckboxConfig config = CheckboxConfig.get();
		config.anchor = HudAnchor.MIDDLE_RIGHT;
		config.scale = 0.5f;
		config.showTitle = false;
		config.pauseTimersOnQuit = false;
		config.maxVisibleEntries = 20;

		config.resetToDefaults();

		CheckboxConfig defaults = new CheckboxConfig();
		assertEquals(defaults.anchor, config.anchor);
		assertEquals(defaults.scale, config.scale);
		assertEquals(defaults.showTitle, config.showTitle);
		assertEquals(defaults.pauseTimersOnQuit, config.pauseTimersOnQuit);
		assertEquals(defaults.maxVisibleEntries, config.maxVisibleEntries);
	}

	@Test
	void layoutRevisionTracksSettingsThatChangeTheLayout(@TempDir Path configDir) {
		CheckboxConfig.setConfigDir(configDir);
		CheckboxConfig config = CheckboxConfig.get();
		int before = config.layoutRevision();

		config.playSounds = !config.playSounds;
		assertEquals(before, config.layoutRevision(), "behaviour settings do not affect layout");

		config.scale = 1.75f;
		assertNotEquals(before, config.layoutRevision());
	}
}

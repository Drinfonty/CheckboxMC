package com.drinfonty.checkbox.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StoreScopeTest {
	@Test
	void scopePaths() {
		assertEquals("sp/new_world.json", StoreScope.singleplayer("New World").relativePath());
		assertEquals("mp/play.example.com_25565.json",
				StoreScope.multiplayer("play.example.com:25565").relativePath());
		assertEquals("global.json", StoreScope.global().relativePath());
	}

	@Test
	void keysAreReducedToSafeCharacters() {
		assertEquals("my_world_2", StoreScope.sanitize("My World #2"));
		assertEquals("caf_au_lait", StoreScope.sanitize("Café au lait"));
		assertEquals("a.b-c_d", StoreScope.sanitize("a.b-c_d"));
		assertTrue(StoreScope.sanitize("我的世界").isEmpty(),
				"a name with nothing safe in it yields no key");
	}

	@Test
	void keysCannotEscapeTheListsDirectory() {
		// A save folder is attacker-influenced input in the sense that it is arbitrary text
		// from the filesystem; it must never produce a path segment or a dotfile.
		String key = StoreScope.singleplayer("../../.bashrc").key();
		assertFalse(key.contains("/"));
		assertFalse(key.contains("\\"));
		assertFalse(key.startsWith("."));
		assertEquals("bashrc", key);

		assertFalse(StoreScope.singleplayer("..").relativePath().contains(".."));
	}

	@Test
	void saveDirectoryPathsYieldTheFolderName() {
		// Minecraft's LevelResource.ROOT resolves to a path ending in ".", which is how every
		// singleplayer world quietly ended up sharing the global list.
		assertEquals("sp/new_world.json",
				StoreScope.singleplayerFromPath(java.nio.file.Path.of("run/saves/New World/."))
						.relativePath());
		assertEquals("sp/new_world.json",
				StoreScope.singleplayerFromPath(java.nio.file.Path.of("run/saves/New World"))
						.relativePath());
		assertEquals("sp/new_world.json",
				StoreScope.singleplayerFromPath(java.nio.file.Path.of("run/saves/New World/x/.."))
						.relativePath());
		assertEquals(StoreScope.global(), StoreScope.singleplayerFromPath(null));
	}

	@Test
	void unusableNamesFallBackToGlobal() {
		assertEquals(StoreScope.global(), StoreScope.singleplayer(""));
		assertEquals(StoreScope.global(), StoreScope.singleplayer(null));
		assertEquals(StoreScope.global(), StoreScope.multiplayer("   "));
	}

	@Test
	void longNamesAreTruncated() {
		String key = StoreScope.singleplayer("w".repeat(200)).key();
		assertEquals(64, key.length());
	}

	@Test
	void scopesWithTheSameKeyAreEqual() {
		assertEquals(StoreScope.singleplayer("World"), StoreScope.singleplayer("world"));
		assertFalse(StoreScope.singleplayer("world").equals(StoreScope.multiplayer("world")));
	}
}

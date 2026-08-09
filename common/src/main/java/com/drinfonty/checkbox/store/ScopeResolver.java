package com.drinfonty.checkbox.store;

import com.drinfonty.checkbox.Checkbox;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Turns the client's current connection into a {@link StoreScope}.
 *
 * <p>Kept apart from {@link StoreScope} so that everything else in the store layer stays free
 * of Minecraft classes and unit testable; this is the one piece that needs a live client.
 */
public final class ScopeResolver {
	private ScopeResolver() {
	}

	public static StoreScope resolveCurrent() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return StoreScope.global();
		}

		if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
			// The save folder name, not the display name: two saves can share a display name.
			try {
				Path root = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
				return StoreScope.singleplayerFromPath(root);
			} catch (RuntimeException e) {
				Checkbox.LOGGER.warn("Could not resolve the save folder; using the global list: {}",
						e.toString());
			}
			return StoreScope.global();
		}

		ServerData server = minecraft.getCurrentServer();
		if (server != null && server.ip != null && !server.ip.isBlank()) {
			// Realms addresses are issued per session and change, so they would spawn a new
			// list every time. SPEC §7 sends them to the global list instead.
			if (server.isRealm()) {
				return StoreScope.global();
			}
			return StoreScope.multiplayer(server.ip);
		}

		return StoreScope.global();
	}
}

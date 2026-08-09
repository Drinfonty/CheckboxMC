package com.drinfonty.checkbox;

import com.drinfonty.checkbox.client.CheckboxKeys;
import com.drinfonty.checkbox.config.CheckboxConfig;
import com.drinfonty.checkbox.store.ScopeResolver;
import com.drinfonty.checkbox.store.StoreScope;
import com.drinfonty.checkbox.store.TodoStore;
import com.drinfonty.checkbox.track.TrackerManager;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The client lifecycle facade the loader modules call into.
 *
 * <p>Both loaders only have to forward one thing - the client tick. World joins, leaves,
 * respawns and dimension changes are all derived here from the level and player instances
 * changing, which keeps the per-loader glue to a single event each instead of four.
 */
public final class CheckboxClient {
	private static final TodoStore STORE = new TodoStore(Path.of("config"));
	private static final TrackerManager TRACKERS = new TrackerManager(STORE);

	private static ClientLevel lastLevel;
	private static LocalPlayer lastPlayer;

	private CheckboxClient() {
	}

	public static TodoStore store() {
		return STORE;
	}

	public static TrackerManager trackers() {
		return TRACKERS;
	}

	public static void onClientTick(Minecraft minecraft) {
		CheckboxKeys.handle(minecraft);

		ClientLevel level = minecraft.level;
		if (level == null) {
			if (STORE.isOpen()) {
				leaveWorld();
			}
			return;
		}

		if (level != lastLevel) {
			enterWorld(level);
		}
		if (minecraft.player != lastPlayer) {
			// A respawn or dimension change hands us a new player, and with it an inventory
			// that must not be mistaken for things the player just acquired.
			lastPlayer = minecraft.player;
			TRACKERS.onPlayerChanged();
		}

		long now = System.currentTimeMillis();
		TRACKERS.tick(minecraft, now);
		STORE.tick();
	}

	private static void enterWorld(ClientLevel level) {
		StoreScope scope = ScopeResolver.resolveCurrent();
		if (!STORE.isOpen() || !scope.equals(STORE.worldScope())) {
			// A dimension change keeps the same scope, so the list is not reloaded for it.
			STORE.open(scope);
		}
		TRACKERS.reset();
		lastLevel = level;
		lastPlayer = null;
	}

	private static void leaveWorld() {
		// SPEC §2.4: running timers freeze on the way out rather than burning down while the
		// player is at the title screen - unless they asked for wall-clock timers.
		if (CheckboxConfig.get().pauseTimersOnQuit) {
			TRACKERS.pauseTimers(System.currentTimeMillis());
		}
		STORE.close();
		TRACKERS.reset();
		lastLevel = null;
		lastPlayer = null;
		if (Checkbox.DEBUG) {
			Checkbox.LOGGER.info("Left world; Checkbox lists saved and closed");
		}
	}

	// --- Signals from the mixins -------------------------------------------------------

	public static void onLocalPlayerDamaged(int victimEntityId) {
		TRACKERS.onLocalPlayerDamaged(victimEntityId);
	}

	public static void onEntityDeath(LivingEntity entity) {
		TRACKERS.onEntityDeath(entity);
	}

	public static void onItemPickedUp(ItemStack stack, int amount) {
		TRACKERS.onItemPickedUp(stack, amount);
	}
}

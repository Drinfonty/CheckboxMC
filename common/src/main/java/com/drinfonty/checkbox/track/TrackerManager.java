package com.drinfonty.checkbox.track;

import com.drinfonty.checkbox.Checkbox;
import com.drinfonty.checkbox.model.CounterEntry;
import com.drinfonty.checkbox.model.EntryMatch;
import com.drinfonty.checkbox.model.TimerEntry;
import com.drinfonty.checkbox.model.TodoEntry;
import com.drinfonty.checkbox.store.TodoStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Routes client-side signals to the entries that care about them (DESIGN §5).
 *
 * <p>Everything here runs on the client thread: the census on a tick, and the packet-derived
 * events from mixins that inject past Minecraft's own thread guard.
 *
 * <p>The scratch collections are fields rather than locals because the census runs four times
 * a second forever; reusing them keeps steady-state allocation at zero.
 */
public final class TrackerManager {
	/** 4 Hz. Fast enough to feel instant, slow enough to be free. */
	public static final int CENSUS_INTERVAL_TICKS = 5;

	private final TodoStore store;
	private final ItemCensus census = new ItemCensus();
	private final KillAttribution attribution = new KillAttribution();
	private final MatchResolver resolver = new MatchResolver();
	private final TimerService timers = new TimerService();

	private final List<CounterEntry> itemScratch = new ArrayList<>();
	private final List<CounterEntry> killScratch = new ArrayList<>();
	private final List<TimerEntry> timerScratch = new ArrayList<>();
	private final Map<UUID, Integer> countScratch = new HashMap<>();

	private long tickCounter;

	public TrackerManager(TodoStore store) {
		this.store = store;
	}

	public MatchResolver resolver() {
		return resolver;
	}

	public void tick(Minecraft minecraft, long nowMillis) {
		tickCounter++;

		collectTimers();
		if (!timerScratch.isEmpty()) {
			timers.tick(minecraft, timerScratch, nowMillis);
		}

		if (tickCounter % CENSUS_INTERVAL_TICKS == 0) {
			runCensus(minecraft.player, nowMillis);
			attribution.prune(tickCounter);
		}
	}

	/** Freezes running timers on the way out of a world (SPEC §2.4). */
	public void pauseTimers(long nowMillis) {
		collectTimers();
		timers.pauseAll(timerScratch, nowMillis);
	}

	/**
	 * Drops everything derived from the previous world: baselines that no longer describe this
	 * inventory, attribution for entity ids that now mean something else, and registry lookups
	 * that may resolve differently under another server's datapacks.
	 */
	public void reset() {
		census.invalidate();
		attribution.clear();
		resolver.clearCache();
		timers.reset();
	}

	/** The player object was replaced - a respawn or a dimension change. */
	public void onPlayerChanged() {
		census.invalidate();
	}

	/** From {@code ClientboundDamageEventPacket}: the local player damaged something. */
	public void onLocalPlayerDamaged(int victimEntityId) {
		attribution.record(victimEntityId, tickCounter);
	}

	/** From {@code LivingEntity#tick} on the first tick of the death animation. */
	public void onEntityDeath(LivingEntity entity) {
		if (!store.isOpen() || !attribution.consume(entity.getId(), tickCounter)) {
			return;
		}

		EntityType<?> type = entity.getType();
		long now = System.currentTimeMillis();
		collectCounters(killScratch, false);
		for (CounterEntry entry : killScratch) {
			if (!entry.isDone() && resolver.matches(entry.match(), type)) {
				entry.addProgress(1, now);
				if (Checkbox.DEBUG) {
					Checkbox.LOGGER.info("Credited kill of {} to '{}' ({}/{})",
							EntityType.getKey(type), entry.text(), entry.progress(), entry.target());
				}
			}
		}
	}

	/** From {@code ClientboundTakeItemEntityPacket}: the player picked something off the ground. */
	public void onItemPickedUp(ItemStack stack, int amount) {
		if (Checkbox.DEBUG) {
			// Logged before the guards: this is the only evidence that the mixin capturing
			// the picked-up stack fired at all, which is otherwise invisible until some
			// PICKED_UP entry happens to match.
			Checkbox.LOGGER.info("Picked up {} x{}", stack.getItem(), amount);
		}
		if (!store.isOpen() || stack.isEmpty() || amount <= 0) {
			return;
		}

		long now = System.currentTimeMillis();
		collectCounters(itemScratch, true);
		for (CounterEntry entry : itemScratch) {
			if (entry.countMode() == CounterEntry.CountMode.PICKED_UP
					&& !entry.isDone()
					&& resolver.matches(entry.match(), stack)) {
				entry.addProgress(amount, now);
			}
		}
	}

	private void runCensus(LocalPlayer player, long nowMillis) {
		if (player == null || !store.isOpen()) {
			return;
		}
		collectCounters(itemScratch, true);
		if (itemScratch.isEmpty()) {
			return;
		}

		countScratch.clear();
		Inventory inventory = player.getInventory();
		// getContainerSize() spans the main inventory plus the equipment slots, so armour and
		// the offhand are counted without special-casing them.
		int size = inventory.getContainerSize();
		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			for (CounterEntry entry : itemScratch) {
				if (resolver.matches(entry.match(), stack)) {
					countScratch.merge(entry.id(), stack.getCount(), Integer::sum);
				}
			}
		}

		census.update(itemScratch, entry -> countScratch.getOrDefault(entry.id(), 0), nowMillis);
	}

	private void collectTimers() {
		timerScratch.clear();
		if (!store.isOpen()) {
			return;
		}
		for (TodoEntry entry : store.entries()) {
			if (entry instanceof TimerEntry timer) {
				timerScratch.add(timer);
			}
		}
	}

	/** @param items true for item counters, false for kill counters */
	private void collectCounters(List<CounterEntry> out, boolean items) {
		out.clear();
		for (TodoEntry entry : store.entries()) {
			if (entry instanceof CounterEntry counter) {
				EntryMatch.Kind kind = counter.match().kind();
				if (kind.isItem() == items) {
					out.add(counter);
				}
			}
		}
	}
}

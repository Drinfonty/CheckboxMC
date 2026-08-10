package com.drinfonty.checkbox.track;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Remembers which entities the local player has recently damaged, so a death can be credited
 * to them (DESIGN §5.2).
 *
 * <p>Fed from {@code ClientboundDamageEventPacket}, whose {@code sourceCauseId} is the
 * *causing* entity - so an arrow or a splash potion is already attributed to the player who
 * fired it, with no projectile bookkeeping here.
 *
 * <p>Each record carries a payload, which the tracker uses for the victim's entity type. That
 * is deliberate: it is captured when the player lands a hit, while the entity certainly
 * exists, so crediting the kill later never has to look the entity up again. Mods that replace
 * death effects routinely remove the dying mob before anything else sees it, and a lookup at
 * death time returns nothing - which is exactly how kill tracking failed in a modded client.
 *
 * <p>Deliberately free of Minecraft types: it is a map of entity id to tick and payload, which
 * makes the window and pruning behaviour testable without a game.
 */
public final class KillAttribution<T> {
	/** How long after hitting something its death still counts as ours. */
	public static final long DEFAULT_WINDOW_TICKS = 200L;

	private record Hit<T>(long tick, T payload) {
	}

	private final Int2ObjectMap<Hit<T>> hits = new Int2ObjectOpenHashMap<>();
	private long windowTicks;

	public KillAttribution() {
		this(DEFAULT_WINDOW_TICKS);
	}

	public KillAttribution(long windowTicks) {
		this.windowTicks = Math.max(1L, windowTicks);
	}

	public long windowTicks() {
		return windowTicks;
	}

	public void setWindowTicks(long windowTicks) {
		this.windowTicks = Math.max(1L, windowTicks);
	}

	/** @param payload what the victim was, remembered while it is still around to ask */
	public void record(int entityId, long nowTicks, T payload) {
		hits.put(entityId, new Hit<>(nowTicks, payload));
	}

	/**
	 * Claims the kill of an entity if the local player hit it recently enough.
	 *
	 * <p>Consuming rather than peeking: an entity id is reused once the entity is gone, and a
	 * death is only credited once, however many signals report it.
	 *
	 * @return the payload recorded when the player last hit it, or {@code null} if this death
	 *         is not ours
	 */
	public T consume(int entityId, long nowTicks) {
		Hit<T> hit = hits.remove(entityId);
		return hit != null && nowTicks - hit.tick() <= windowTicks ? hit.payload() : null;
	}

	/** Drops records that can no longer produce a credited kill. */
	public void prune(long nowTicks) {
		hits.int2ObjectEntrySet().removeIf(entry -> nowTicks - entry.getValue().tick() > windowTicks);
	}

	public void clear() {
		hits.clear();
	}

	public int size() {
		return hits.size();
	}
}

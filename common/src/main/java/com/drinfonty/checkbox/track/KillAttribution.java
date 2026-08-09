package com.drinfonty.checkbox.track;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/**
 * Remembers which entities the local player has recently damaged, so a death can be credited
 * to them (DESIGN §5.2).
 *
 * <p>Fed from {@code ClientboundDamageEventPacket}, whose {@code sourceCauseId} is the
 * *causing* entity - so an arrow or a splash potion is already attributed to the player who
 * fired it, with no projectile bookkeeping here.
 *
 * <p>Deliberately free of Minecraft types: it is a map of entity id to tick, which makes the
 * window and pruning behaviour testable without a game.
 */
public final class KillAttribution {
	/** How long after hitting something its death still counts as ours. */
	public static final long DEFAULT_WINDOW_TICKS = 200L;

	private static final long ABSENT = Long.MIN_VALUE;

	private final Int2LongMap lastHitTick = new Int2LongOpenHashMap();
	private long windowTicks;

	public KillAttribution() {
		this(DEFAULT_WINDOW_TICKS);
	}

	public KillAttribution(long windowTicks) {
		this.windowTicks = Math.max(1L, windowTicks);
		this.lastHitTick.defaultReturnValue(ABSENT);
	}

	public long windowTicks() {
		return windowTicks;
	}

	public void setWindowTicks(long windowTicks) {
		this.windowTicks = Math.max(1L, windowTicks);
	}

	public void record(int entityId, long nowTicks) {
		lastHitTick.put(entityId, nowTicks);
	}

	/**
	 * Claims the kill of an entity if the local player hit it recently enough.
	 *
	 * <p>Consuming rather than peeking: an entity id is reused once the entity is gone, and a
	 * death is only credited once.
	 *
	 * @return whether the kill belongs to the local player
	 */
	public boolean consume(int entityId, long nowTicks) {
		long hitAt = lastHitTick.remove(entityId);
		return hitAt != ABSENT && nowTicks - hitAt <= windowTicks;
	}

	/** Drops records that can no longer produce a credited kill. */
	public void prune(long nowTicks) {
		lastHitTick.int2LongEntrySet()
				.removeIf(entry -> nowTicks - entry.getLongValue() > windowTicks);
	}

	public void clear() {
		lastHitTick.clear();
	}

	public int size() {
		return lastHitTick.size();
	}
}

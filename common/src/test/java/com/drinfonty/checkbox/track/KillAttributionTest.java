package com.drinfonty.checkbox.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class KillAttributionTest {
	/** Stands in for the victim's entity type, which the tracker stores as the payload. */
	private static final String ZOMBIE = "minecraft:zombie";

	private static KillAttribution<String> attribution(long window) {
		return new KillAttribution<>(window);
	}

	@Test
	void aRecentHitClaimsTheKill() {
		KillAttribution<String> attribution = attribution(200L);
		attribution.record(42, 1_000L, ZOMBIE);

		assertEquals(ZOMBIE, attribution.consume(42, 1_100L));
	}

	@Test
	void aHitAtTheEdgeOfTheWindowStillCounts() {
		KillAttribution<String> attribution = attribution(200L);
		attribution.record(42, 1_000L, ZOMBIE);

		assertNotNull(attribution.consume(42, 1_200L));
	}

	@Test
	void aStaleHitDoesNotClaimTheKill() {
		// Hitting a zombie and walking away should not credit its death ten minutes later.
		KillAttribution<String> attribution = attribution(200L);
		attribution.record(42, 1_000L, ZOMBIE);

		assertNull(attribution.consume(42, 1_201L));
	}

	@Test
	void anUntouchedEntityIsNeverCredited() {
		assertNull(attribution(200L).consume(999, 10L));
	}

	@Test
	void aKillIsCreditedOnlyOnce() {
		// Two death signals report the same kill - the packet and the death animation - and
		// entity ids are recycled. Either way it pays out once.
		KillAttribution<String> attribution = attribution(200L);
		attribution.record(42, 1_000L, ZOMBIE);

		assertNotNull(attribution.consume(42, 1_010L));
		assertNull(attribution.consume(42, 1_020L));
	}

	@Test
	void theVictimIsRememberedFromTheHitNotTheDeath() {
		// The regression this guards: in a client with a death-effect mod, the dying mob is
		// removed before the death is handled, so nothing can be asked what it was. Crediting
		// works anyway because the type was captured when the player landed the hit.
		KillAttribution<String> attribution = attribution(200L);
		attribution.record(42, 1_000L, ZOMBIE);

		// Later, all the caller knows is an entity id - the entity itself is long gone.
		assertEquals(ZOMBIE, attribution.consume(42, 1_150L));
	}

	@Test
	void aLaterHitRefreshesTheWindowAndThePayload() {
		KillAttribution<String> attribution = attribution(200L);
		attribution.record(42, 1_000L, "minecraft:husk");
		attribution.record(42, 1_500L, ZOMBIE);

		assertEquals(ZOMBIE, attribution.consume(42, 1_650L));
	}

	@Test
	void pruningDropsOnlyExpiredRecords() {
		KillAttribution<String> attribution = attribution(200L);
		attribution.record(1, 1_000L, ZOMBIE);
		attribution.record(2, 1_400L, ZOMBIE);

		attribution.prune(1_500L);
		assertEquals(1, attribution.size());
		assertNull(attribution.consume(1, 1_500L));
		assertNotNull(attribution.consume(2, 1_500L));
	}

	@Test
	void clearForgetsEverything() {
		KillAttribution<String> attribution = attribution(200L);
		attribution.record(1, 1_000L, ZOMBIE);
		attribution.clear();

		assertEquals(0, attribution.size());
		assertNull(attribution.consume(1, 1_000L));
	}
}

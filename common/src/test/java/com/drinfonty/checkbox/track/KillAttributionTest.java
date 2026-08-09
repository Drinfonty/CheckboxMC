package com.drinfonty.checkbox.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KillAttributionTest {
	@Test
	void aRecentHitClaimsTheKill() {
		KillAttribution attribution = new KillAttribution(200L);
		attribution.record(42, 1_000L);

		assertTrue(attribution.consume(42, 1_100L));
	}

	@Test
	void aHitAtTheEdgeOfTheWindowStillCounts() {
		KillAttribution attribution = new KillAttribution(200L);
		attribution.record(42, 1_000L);

		assertTrue(attribution.consume(42, 1_200L));
	}

	@Test
	void aStaleHitDoesNotClaimTheKill() {
		// Hitting a zombie and walking away should not credit its death ten minutes later.
		KillAttribution attribution = new KillAttribution(200L);
		attribution.record(42, 1_000L);

		assertFalse(attribution.consume(42, 1_201L));
	}

	@Test
	void anUntouchedEntityIsNeverCredited() {
		KillAttribution attribution = new KillAttribution(200L);
		assertFalse(attribution.consume(999, 10L));
	}

	@Test
	void aKillIsCreditedOnlyOnce() {
		// Entity ids are recycled, and a death must not pay out twice.
		KillAttribution attribution = new KillAttribution(200L);
		attribution.record(42, 1_000L);

		assertTrue(attribution.consume(42, 1_010L));
		assertFalse(attribution.consume(42, 1_020L));
	}

	@Test
	void aLaterHitRefreshesTheWindow() {
		KillAttribution attribution = new KillAttribution(200L);
		attribution.record(42, 1_000L);
		attribution.record(42, 1_500L);

		assertTrue(attribution.consume(42, 1_650L));
	}

	@Test
	void pruningDropsOnlyExpiredRecords() {
		KillAttribution attribution = new KillAttribution(200L);
		attribution.record(1, 1_000L);
		attribution.record(2, 1_400L);

		attribution.prune(1_500L);
		assertEquals(1, attribution.size());
		assertFalse(attribution.consume(1, 1_500L));
		assertTrue(attribution.consume(2, 1_500L));
	}

	@Test
	void clearForgetsEverything() {
		KillAttribution attribution = new KillAttribution(200L);
		attribution.record(1, 1_000L);
		attribution.clear();

		assertEquals(0, attribution.size());
		assertFalse(attribution.consume(1, 1_000L));
	}
}

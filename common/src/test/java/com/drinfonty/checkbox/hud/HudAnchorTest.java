package com.drinfonty.checkbox.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HudAnchorTest {
	private static final int SCREEN_W = 640;
	private static final int SCREEN_H = 360;
	private static final int WIDGET_W = 120;
	private static final int WIDGET_H = 60;

	@Test
	void cornersPlaceTheWidgetInsetFromTheirOwnEdge() {
		assertEquals(4, HudAnchor.TOP_LEFT.resolveX(SCREEN_W, WIDGET_W, 4));
		assertEquals(4, HudAnchor.TOP_LEFT.resolveY(SCREEN_H, WIDGET_H, 4));

		// A positive offset on a right anchor moves inwards, not off the screen.
		assertEquals(SCREEN_W - WIDGET_W - 4, HudAnchor.TOP_RIGHT.resolveX(SCREEN_W, WIDGET_W, 4));
		assertEquals(SCREEN_H - WIDGET_H - 4,
				HudAnchor.BOTTOM_LEFT.resolveY(SCREEN_H, WIDGET_H, 4));
		assertEquals(SCREEN_W - WIDGET_W - 4,
				HudAnchor.BOTTOM_RIGHT.resolveX(SCREEN_W, WIDGET_W, 4));
	}

	@Test
	void centresIgnoreTheEdgeAndJustOffset() {
		assertEquals((SCREEN_W - WIDGET_W) / 2,
				HudAnchor.TOP_CENTER.resolveX(SCREEN_W, WIDGET_W, 0));
		assertEquals((SCREEN_H - WIDGET_H) / 2,
				HudAnchor.MIDDLE_LEFT.resolveY(SCREEN_H, WIDGET_H, 0));
		assertEquals((SCREEN_W - WIDGET_W) / 2 + 10,
				HudAnchor.MIDDLE_CENTER.resolveX(SCREEN_W, WIDGET_W, 10));
	}

	@Test
	void placementScalesWithTheScreen() {
		// The same anchor and offset keep the same relationship on a bigger display.
		int small = HudAnchor.BOTTOM_RIGHT.resolveX(640, WIDGET_W, 4);
		int large = HudAnchor.BOTTOM_RIGHT.resolveX(2560, WIDGET_W, 4);
		assertEquals(640 - WIDGET_W - 4, small);
		assertEquals(2560 - WIDGET_W - 4, large);
	}

	@Test
	void aWidgetIsNeverPushedCompletelyOffScreen() {
		// SPEC §3.2: a saved offset from a larger monitor must not strand the widget.
		int x = HudAnchor.TOP_LEFT.resolveX(640, WIDGET_W, 4000);
		assertTrue(x < 640, "must stay on screen");
		assertTrue(x + WIDGET_W > 0);
		assertTrue(640 - x >= Math.min(8, WIDGET_W), "at least a sliver stays visible");

		int negative = HudAnchor.TOP_LEFT.resolveX(640, WIDGET_W, -4000);
		assertTrue(negative + WIDGET_W >= Math.min(8, WIDGET_W));
	}

	@Test
	void aWidgetWiderThanTheScreenStillStartsOnScreen() {
		int x = HudAnchor.TOP_LEFT.resolveX(200, 400, 0);
		assertTrue(x <= 0 && x + 400 > 0);
	}

	@Test
	void nearestAnchorFollowsTheScreenThirds() {
		// Drop positions map to the anchor whose region contains the widget's centre.
		assertEquals(HudAnchor.TOP_LEFT, HudAnchor.nearest(640, 360, 0, 0, 20, 10));
		assertEquals(HudAnchor.TOP_RIGHT, HudAnchor.nearest(640, 360, 620, 0, 20, 10));
		assertEquals(HudAnchor.BOTTOM_LEFT, HudAnchor.nearest(640, 360, 0, 350, 20, 10));
		assertEquals(HudAnchor.BOTTOM_RIGHT, HudAnchor.nearest(640, 360, 620, 350, 20, 10));
		assertEquals(HudAnchor.MIDDLE_CENTER, HudAnchor.nearest(640, 360, 310, 175, 20, 10));
		assertEquals(HudAnchor.TOP_CENTER, HudAnchor.nearest(640, 360, 310, 0, 20, 10));
	}

	@Test
	void roundTripDragThenResolveKeepsThePosition() {
		// What the drag editor does: derive an anchor, store the residual, resolve it back.
		int droppedX = 500;
		int droppedY = 300;
		HudAnchor anchor = HudAnchor.nearest(640, 360, droppedX, droppedY, WIDGET_W, WIDGET_H);
		int baseX = Math.round((640 - WIDGET_W) * anchor.fractionX());
		int baseY = Math.round((360 - WIDGET_H) * anchor.fractionY());
		int offsetX = anchor.isRight() ? baseX - droppedX : droppedX - baseX;
		int offsetY = anchor.isBottom() ? baseY - droppedY : droppedY - baseY;

		assertEquals(droppedX, anchor.resolveX(640, WIDGET_W, offsetX));
		assertEquals(droppedY, anchor.resolveY(360, WIDGET_H, offsetY));
	}
}

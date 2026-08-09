package com.drinfonty.checkbox.hud;

/**
 * Where the widget sits on screen (SPEC §3.2).
 *
 * <p>An anchor plus an offset, rather than raw coordinates, is what makes a position survive a
 * resolution or GUI-scale change: the widget keeps its relationship to the corner it was
 * placed against.
 *
 * <p>Pure integer maths, no Minecraft types, so the placement rules are unit tested.
 */
public enum HudAnchor {
	TOP_LEFT(0.0f, 0.0f),
	TOP_CENTER(0.5f, 0.0f),
	TOP_RIGHT(1.0f, 0.0f),
	MIDDLE_LEFT(0.0f, 0.5f),
	MIDDLE_CENTER(0.5f, 0.5f),
	MIDDLE_RIGHT(1.0f, 0.5f),
	BOTTOM_LEFT(0.0f, 1.0f),
	BOTTOM_CENTER(0.5f, 1.0f),
	BOTTOM_RIGHT(1.0f, 1.0f);

	/** Never let the widget be dragged or configured entirely out of reach. */
	private static final int MIN_VISIBLE_PIXELS = 8;

	private final float fx;
	private final float fy;

	HudAnchor(float fx, float fy) {
		this.fx = fx;
		this.fy = fy;
	}

	public float fractionX() {
		return fx;
	}

	public float fractionY() {
		return fy;
	}

	public boolean isRight() {
		return fx == 1.0f;
	}

	public boolean isBottom() {
		return fy == 1.0f;
	}

	/**
	 * @param widgetWidth the width *after* scaling, so that changing the scale does not walk
	 *                    the widget away from the edge it is anchored to
	 */
	public int resolveX(int screenWidth, int widgetWidth, int offsetX) {
		// Offsets read as an inset from the anchored edge: +4 means 4px in from the right for
		// a right anchor, not 4px further off-screen.
		int base = Math.round((screenWidth - widgetWidth) * fx);
		return clampVisible(base + (isRight() ? -offsetX : offsetX), screenWidth, widgetWidth);
	}

	public int resolveY(int screenHeight, int widgetHeight, int offsetY) {
		int base = Math.round((screenHeight - widgetHeight) * fy);
		return clampVisible(base + (isBottom() ? -offsetY : offsetY), screenHeight, widgetHeight);
	}

	/**
	 * Keeps a sliver on screen. A saved offset from a much larger monitor would otherwise put
	 * the widget somewhere the player cannot see it or drag it back from.
	 */
	private static int clampVisible(int position, int screenSize, int widgetSize) {
		int visible = Math.min(MIN_VISIBLE_PIXELS, widgetSize);
		int min = visible - widgetSize;
		int max = screenSize - visible;
		return Math.max(min, Math.min(max, position));
	}

	/** The anchor whose corner is nearest the given position, for the drag-to-place editor. */
	public static HudAnchor nearest(int screenWidth, int screenHeight,
			int widgetX, int widgetY, int widgetWidth, int widgetHeight) {
		float centerX = widgetX + widgetWidth / 2.0f;
		float centerY = widgetY + widgetHeight / 2.0f;
		int column = thirdOf(centerX, screenWidth);
		int row = thirdOf(centerY, screenHeight);
		return values()[row * 3 + column];
	}

	private static int thirdOf(float position, int size) {
		if (size <= 0) {
			return 0;
		}
		float fraction = position / size;
		if (fraction < 1.0f / 3.0f) {
			return 0;
		}
		return fraction < 2.0f / 3.0f ? 1 : 2;
	}
}

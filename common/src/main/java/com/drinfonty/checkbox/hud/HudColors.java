package com.drinfonty.checkbox.hud;

/** RGB values for the HUD. Alpha is applied at draw time, so these are 24-bit. */
public final class HudColors {
	public static final int TEXT = 0xFFFFFF;
	/** Bright enough to stay readable once a strikethrough runs through it. */
	public static final int TEXT_DONE = 0xA6A6A6;
	public static final int TEXT_PAUSED = 0xB0B0B0;
	public static final int TITLE = 0xFFAA00;
	public static final int VALUE = 0xAAAAAA;
	public static final int TIMER_EXPIRED_A = 0xFF5555;
	public static final int TIMER_EXPIRED_B = 0xFFFF55;
	/** Light enough to read as an unfilled track against the translucent black panel. */
	public static final int BAR_TRACK = 0x5A5A5A;
	public static final int BAR_FILL = 0x55DD55;
	public static final int BAR_FILL_DONE = 0x3F8F3F;
	public static final int PANEL = 0x000000;

	private HudColors() {
	}

	/** Packs an RGB value and a 0..1 alpha into ARGB. */
	public static int argb(int rgb, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
		return (a << 24) | (rgb & 0xFFFFFF);
	}
}

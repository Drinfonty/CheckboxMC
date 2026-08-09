package com.drinfonty.checkbox.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws a checkbox: a square outline, with a tick through it once the entry is done.
 *
 * <p>Drawn rather than blitted from Minecraft's own {@code widget/checkbox} sprites. Those are
 * 20x20 with a one-pixel border, and GUI blits sample nearest - so shrinking one to fit a 9px
 * text row keeps the top border and drops the bottom, which looks broken. Drawing it means it
 * is crisp at whatever size the row happens to be, including under HUD scaling.
 */
public final class CheckboxGlyph {
	/** Fits a 9px text row with a pixel to spare above and below. */
	public static final int HUD_SIZE = 7;
	/** Roomier, for the manager's taller rows. */
	public static final int SCREEN_SIZE = 9;
	/** Space between the box and the text after it. */
	public static final int GAP = 3;

	private CheckboxGlyph() {
	}

	/** Total horizontal space a glyph occupies, including the gap before the label. */
	public static int widthWithGap(int size) {
		return size + GAP;
	}

	public static void draw(GuiGraphicsExtractor graphics, int x, int y, int size, boolean done,
			float alpha) {
		int colour = done ? HudColors.CHECK : HudColors.CHECKBOX_EMPTY;
		graphics.outline(x, y, size, size, HudColors.argb(colour, alpha));
		if (done) {
			tick(graphics, x, y, size, HudColors.argb(HudColors.CHECK, alpha));
		}
	}

	/** A short down-stroke into a long up-stroke, proportional so it works at any size. */
	private static void tick(GuiGraphicsExtractor graphics, int x, int y, int size, int colour) {
		int ax = x + size / 5;
		int ay = y + size / 2;
		int bx = x + 2 * size / 5;
		int by = y + 7 * size / 10;
		int cx = x + 4 * size / 5;
		int cy = y + size / 4;

		line(graphics, ax, ay, bx, by, colour);
		line(graphics, bx, by, cx, cy, colour);
	}

	private static void line(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
			int colour) {
		int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		for (int i = 0; i <= steps; i++) {
			int px = Math.round(x0 + (x1 - x0) * (float) i / steps);
			int py = Math.round(y0 + (y1 - y0) * (float) i / steps);
			graphics.fill(px, py, px + 1, py + 1, colour);
		}
	}
}

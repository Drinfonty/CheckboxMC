package com.drinfonty.checkbox.hud;

import com.drinfonty.checkbox.CheckboxClient;
import com.drinfonty.checkbox.config.CheckboxConfig;
import com.drinfonty.checkbox.model.TodoEntry;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * Draws the todo list on the HUD (DESIGN §6). The only class in the mod that issues draw
 * calls for the overlay.
 *
 * <p>Both loaders call {@link #render} with the same arguments - 26.2 gave Fabric's
 * {@code HudElement} and NeoForge's {@code GuiLayer} the identical signature - so there is one
 * renderer rather than one per loader.
 *
 * <p>Keeping every {@link GuiGraphicsExtractor} call inside this class is also what keeps a
 * port to older Minecraft cheap: pre-26.x has {@code GuiGraphics} and a completely different
 * HUD hook, and this is the only file that would need rewriting.
 */
public final class TodoHudRenderer {
	private static final TodoHudRenderer INSTANCE = new TodoHudRenderer();

	private static final int PADDING = 3;
	/** Leaves a pixel between the text and its progress bar, and another before the next row. */
	private static final int ROW_GAP = 3;
	private static final int BAR_HEIGHT = 1;
	private static final int BAR_TOP_GAP = 1;
	private static final long BLINK_PERIOD_MS = 500L;
	/** The fade-out happens over the final second of an entry's visible life. */
	private static final long FADE_TAIL_MS = 1000L;

	private final HudLayoutCache cache = new HudLayoutCache();

	/** Where the widget was last drawn, so the position editor can outline and drag it. */
	private int lastX;
	private int lastY;
	private int lastWidth;
	private int lastHeight;

	/** Set by the position editor, which must show the widget even when it is switched off. */
	private boolean forceVisible;

	private TodoHudRenderer() {
	}

	public static TodoHudRenderer get() {
		return INSTANCE;
	}

	/** Forces a re-layout, for when a screen edits settings or entries. */
	public void invalidate() {
		cache.invalidate();
	}

	public void setForceVisible(boolean forceVisible) {
		this.forceVisible = forceVisible;
	}

	public int lastX() {
		return lastX;
	}

	public int lastY() {
		return lastY;
	}

	public int lastWidth() {
		return lastWidth;
	}

	public int lastHeight() {
		return lastHeight;
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		CheckboxConfig config = CheckboxConfig.get();
		if (!shouldRender(minecraft, config)) {
			return;
		}

		List<TodoEntry> entries = CheckboxClient.store().entries();
		if (entries.isEmpty()) {
			return;
		}

		long now = System.currentTimeMillis();
		Font font = minecraft.font;
		cache.refresh(font, config, entries, now);
		if (cache.isEmpty() && cache.overflow() == 0) {
			return;
		}

		int rowHeight = font.lineHeight + ROW_GAP;
		int rowCount = cache.rows().size() + (cache.overflow() > 0 ? 1 : 0);
		int titleHeight = config.showTitle ? rowHeight : 0;
		int width = (config.widthMode == CheckboxConfig.WidthMode.FIXED
				? config.fixedWidth
				: cache.contentWidth()) + PADDING * 2;
		int height = titleHeight + rowCount * rowHeight + PADDING * 2;

		// Anchoring uses the *scaled* size so that changing the scale grows the widget into
		// the screen rather than sliding it away from the corner it is pinned to.
		int scaledWidth = Math.round(width * config.scale);
		int scaledHeight = Math.round(height * config.scale);
		int x = config.anchor.resolveX(graphics.guiWidth(), scaledWidth, config.offsetX);
		int y = config.anchor.resolveY(graphics.guiHeight(), scaledHeight, config.offsetY);

		this.lastX = x;
		this.lastY = y;
		this.lastWidth = scaledWidth;
		this.lastHeight = scaledHeight;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(config.scale, config.scale);
		draw(graphics, font, config, width, height, rowHeight, titleHeight, now);
		pose.popMatrix();
	}

	private void draw(GuiGraphicsExtractor graphics, Font font, CheckboxConfig config,
			int width, int height, int rowHeight, int titleHeight, long now) {
		if (config.backgroundStyle == CheckboxConfig.BackgroundStyle.PANEL) {
			graphics.fill(0, 0, width, height,
					HudColors.argb(HudColors.PANEL, config.backgroundOpacity / 100f));
		}

		int textX = PADDING;
		int y = PADDING;

		if (config.showTitle) {
			drawRowBackdrop(graphics, config, width, y, rowHeight);
			graphics.text(font, Component.literal(config.titleText), textX, y,
					HudColors.argb(HudColors.TITLE, 1f), config.textShadow);
			y += titleHeight;
		}

		for (HudLayoutCache.Row row : cache.rows()) {
			float alpha = alphaOf(row, config, now);
			if (alpha > 0f) {
				drawRowBackdrop(graphics, config, width, y, rowHeight);
				drawRow(graphics, font, config, row, textX, y, width, alpha, now);
			}
			y += rowHeight;
		}

		if (cache.overflow() > 0) {
			graphics.text(font, Component.literal("+" + cache.overflow() + " more"), textX, y,
					HudColors.argb(HudColors.VALUE, 1f), config.textShadow);
		}
	}

	private void drawRow(GuiGraphicsExtractor graphics, Font font, CheckboxConfig config,
			HudLayoutCache.Row row, int x, int y, int width, float alpha, long now) {
		int rgb = row.expiredTimer() ? blinkColor(now) : row.rgb();

		// Completed rows drop the shadow. Minecraft's shadow is a second copy of the glyphs
		// offset by a pixel, and it draws the strikethrough line again with them - on 9px grey
		// text that is four competing lines per row and the label stops being readable.
		boolean shadow = config.textShadow && !row.done();

		graphics.text(font, row.label(), x, y, HudColors.argb(rgb, alpha), shadow);

		if (!row.value().isEmpty()) {
			int valueX = width - PADDING - row.valueWidth();
			graphics.text(font, Component.literal(row.value()), valueX, y,
					HudColors.argb(row.expiredTimer() ? rgb : HudColors.VALUE, alpha), shadow);
		}

		if (row.showBar()) {
			int barY = y + font.lineHeight + BAR_TOP_GAP;
			int barRight = width - PADDING;
			graphics.fill(x, barY, barRight, barY + BAR_HEIGHT,
					HudColors.argb(HudColors.BAR_TRACK, alpha));
			int filled = Math.round((barRight - x) * Math.clamp(row.fraction(), 0f, 1f));
			if (filled > 0) {
				int fill = row.fraction() >= 1f ? HudColors.BAR_FILL_DONE : HudColors.BAR_FILL;
				graphics.fill(x, barY, x + filled, barY + BAR_HEIGHT,
						HudColors.argb(fill, alpha));
			}
		}
	}

	private static void drawRowBackdrop(GuiGraphicsExtractor graphics, CheckboxConfig config,
			int width, int y, int rowHeight) {
		if (config.backgroundStyle == CheckboxConfig.BackgroundStyle.SHADOW) {
			graphics.fill(0, y - 1, width, y + rowHeight - 1,
					HudColors.argb(HudColors.PANEL, config.backgroundOpacity / 100f * 0.6f));
		}
	}

	/** Fades a completed entry out over its final second rather than blinking it away. */
	private static float alphaOf(HudLayoutCache.Row row, CheckboxConfig config, long now) {
		if (config.completedBehaviour != CheckboxConfig.CompletedBehaviour.FADE
				|| row.completedAt() <= 0L) {
			return 1f;
		}
		long lifetime = config.completedFadeSeconds * 1000L;
		long age = now - row.completedAt();
		if (age < lifetime - FADE_TAIL_MS) {
			return 1f;
		}
		return Math.clamp((lifetime - age) / (float) FADE_TAIL_MS, 0f, 1f);
	}

	private static int blinkColor(long now) {
		return (now / BLINK_PERIOD_MS) % 2 == 0
				? HudColors.TIMER_EXPIRED_A
				: HudColors.TIMER_EXPIRED_B;
	}

	private boolean shouldRender(Minecraft minecraft, CheckboxConfig config) {
		if (minecraft.level == null || minecraft.player == null) {
			return false;
		}
		// The position editor needs to see the widget it is moving, whatever the settings say.
		if (forceVisible) {
			return true;
		}
		if (!config.hudVisible) {
			return false;
		}
		// F1. In 26.2 this lives on Hud rather than Options.
		if (minecraft.gui.hud.isHidden()) {
			return false;
		}
		if (config.hideWithDebugScreen && minecraft.getDebugOverlay().showDebugScreen()) {
			return false;
		}
		return !config.hideWhenScreenOpen || minecraft.gui.screen() == null;
	}
}

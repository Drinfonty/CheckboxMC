package com.drinfonty.checkbox.client.gui;

import com.drinfonty.checkbox.config.CheckboxConfig;
import com.drinfonty.checkbox.hud.HudAnchor;
import com.drinfonty.checkbox.hud.HudColors;
import com.drinfonty.checkbox.hud.TodoHudRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Drag-to-place editor for the HUD widget (SPEC §4.4).
 *
 * <p>Dragging adjusts the offset against the current anchor; the anchor is only re-derived on
 * release, from whichever third of the screen the widget ends up in. Storing an anchor plus a
 * small residual - rather than absolute coordinates - is what makes a position survive a
 * resolution or GUI-scale change.
 */
public class HudPositionScreen extends Screen {
	private static final int SNAP_DISTANCE = 6;
	private static final int FALLBACK_WIDTH = 120;
	private static final int FALLBACK_HEIGHT = 48;

	private final Screen parent;

	// Restored if the player cancels.
	private final HudAnchor originalAnchor;
	private final int originalOffsetX;
	private final int originalOffsetY;

	private boolean dragging;
	private int grabX;
	private int grabY;

	public HudPositionScreen(Screen parent) {
		super(Component.literal("Move the Checkbox HUD"));
		this.parent = parent;
		CheckboxConfig config = CheckboxConfig.get();
		this.originalAnchor = config.anchor;
		this.originalOffsetX = config.offsetX;
		this.originalOffsetY = config.offsetY;
	}

	@Override
	protected void init() {
		// The widget has to be visible to be dragged, even if the HUD is switched off or set
		// to hide while a screen is open.
		TodoHudRenderer.get().setForceVisible(true);
	}

	private int widgetWidth() {
		int width = TodoHudRenderer.get().lastWidth();
		return width > 0 ? width : FALLBACK_WIDTH;
	}

	private int widgetHeight() {
		int height = TodoHudRenderer.get().lastHeight();
		return height > 0 ? height : FALLBACK_HEIGHT;
	}

	private int widgetX() {
		return CheckboxConfig.get().anchor.resolveX(this.width, widgetWidth(),
				CheckboxConfig.get().offsetX);
	}

	private int widgetY() {
		return CheckboxConfig.get().anchor.resolveY(this.height, widgetHeight(),
				CheckboxConfig.get().offsetY);
	}

    /** Stores the offset that puts the widget's top-left at the given point. */
	private void moveTo(int x, int y) {
		CheckboxConfig config = CheckboxConfig.get();
		int w = widgetWidth();
		int h = widgetHeight();

		int snappedX = snap(x, this.width, w);
		int snappedY = snap(y, this.height, h);

		int baseX = Math.round((this.width - w) * config.anchor.fractionX());
		int baseY = Math.round((this.height - h) * config.anchor.fractionY());
		config.offsetX = config.anchor.isRight() ? baseX - snappedX : snappedX - baseX;
		config.offsetY = config.anchor.isBottom() ? baseY - snappedY : snappedY - baseY;
	}

	/** Pulls the widget onto an edge or the centre line when it is close to one. */
	private static int snap(int position, int screenSize, int widgetSize) {
		int[] candidates = {0, (screenSize - widgetSize) / 2, screenSize - widgetSize};
		for (int candidate : candidates) {
			if (Math.abs(position - candidate) <= SNAP_DISTANCE) {
				return candidate;
			}
		}
		return position;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int x = (int) event.x();
		int y = (int) event.y();
		if (x >= widgetX() && x < widgetX() + widgetWidth()
				&& y >= widgetY() && y < widgetY() + widgetHeight()) {
			dragging = true;
			grabX = x - widgetX();
			grabY = y - widgetY();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			moveTo((int) event.x() - grabX, (int) event.y() - grabY);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			reanchor();
			return true;
		}
		return super.mouseReleased(event);
	}

	/**
	 * Re-derives the anchor from where the widget landed, keeping it exactly where it is by
	 * converting the position into an offset against the new anchor.
	 */
	private void reanchor() {
		CheckboxConfig config = CheckboxConfig.get();
		int x = widgetX();
		int y = widgetY();
		config.anchor = HudAnchor.nearest(this.width, this.height, x, y,
				widgetWidth(), widgetHeight());
		moveTo(x, y);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int step = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
		switch (event.key()) {
			case GLFW.GLFW_KEY_LEFT -> nudge(-step, 0);
			case GLFW.GLFW_KEY_RIGHT -> nudge(step, 0);
			case GLFW.GLFW_KEY_UP -> nudge(0, -step);
			case GLFW.GLFW_KEY_DOWN -> nudge(0, step);
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				accept();
				return true;
			}
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	private void nudge(int dx, int dy) {
		moveTo(widgetX() + dx, widgetY() + dy);
		reanchor();
	}

	private void accept() {
		CheckboxConfig.get().save();
		close();
	}

	@Override
	public void onClose() {
		// Esc cancels: put back what was there when the editor opened.
		CheckboxConfig config = CheckboxConfig.get();
		config.anchor = originalAnchor;
		config.offsetX = originalOffsetX;
		config.offsetY = originalOffsetY;
		close();
	}

	private void close() {
		TodoHudRenderer.get().setForceVisible(false);
		TodoHudRenderer.get().invalidate();
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		// No dimming: the widget being positioned is drawn by the HUD layer underneath, and
		// darkening it would defeat the point of previewing it.
		int x = widgetX();
		int y = widgetY();
		int w = widgetWidth();
		int h = widgetHeight();
		graphics.outline(x - 1, y - 1, w + 2, h + 2,
				HudColors.argb(dragging ? 0x55FF55 : 0xFFFFFF, 0.9f));

		CheckboxConfig config = CheckboxConfig.get();
		graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
		graphics.centeredText(this.font,
				Component.literal("Drag, or use the arrow keys (shift = 10px)"),
				this.width / 2, 22, 0xFFAAAAAA);
		graphics.centeredText(this.font,
				Component.literal("Enter to accept, Esc to cancel"),
				this.width / 2, 34, 0xFFAAAAAA);
		graphics.centeredText(this.font,
				Component.literal("Anchor: " + config.anchor.name()
						+ "   offset " + config.offsetX + ", " + config.offsetY),
				this.width / 2, this.height - 20, 0xFFAAAAAA);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

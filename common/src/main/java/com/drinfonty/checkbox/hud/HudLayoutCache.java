package com.drinfonty.checkbox.hud;

import com.drinfonty.checkbox.config.CheckboxConfig;
import com.drinfonty.checkbox.model.CounterEntry;
import com.drinfonty.checkbox.model.TimerEntry;
import com.drinfonty.checkbox.model.TodoEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

/**
 * Pre-formatted rows for the HUD, rebuilt only when something visible actually changes.
 *
 * <p>The render path runs at the frame rate; formatting numbers and building {@link Component}s
 * there would allocate continuously for a widget that changes a few times a minute. A cheap
 * signature over the visible state decides when to rebuild - timers contribute their whole
 * second, so a running countdown costs one rebuild per second rather than one per frame.
 *
 * <p>Alpha is deliberately *not* cached: a fading entry changes opacity every frame, and
 * recomputing one float at draw time is cheaper than rebuilding the rows.
 */
public final class HudLayoutCache {
	/** Gap between an entry's label and its right-aligned value. */
	public static final int LABEL_VALUE_GAP = 6;

	/** Drawn beside the value; 8px so it sits inside a 9px text row. */
	public static final int ICON_SIZE = 8;
	public static final int ICON_GAP = 3;

	public record Row(Component label, String value, int labelWidth, int valueWidth,
			float fraction, boolean showBar, int rgb, long completedAt, boolean expiredTimer,
			boolean done, ItemStack icon) {

		/** Width taken by the value and its icon, including the gap before them. */
		public int trailingWidth() {
			int width = value.isEmpty() ? 0 : valueWidth;
			if (!icon.isEmpty()) {
				width += (width > 0 ? ICON_GAP : 0) + ICON_SIZE;
			}
			return width;
		}
	}

	private final List<Row> rows = new ArrayList<>();
	private long signature = Long.MIN_VALUE;
	private int contentWidth;
	private int overflow;

	public List<Row> rows() {
		return rows;
	}

	public int contentWidth() {
		return contentWidth;
	}

	/** How many entries did not fit in {@code maxVisibleEntries}. */
	public int overflow() {
		return overflow;
	}

	public boolean isEmpty() {
		return rows.isEmpty();
	}

	/** Rebuilds if anything visible changed. Returns true if a rebuild happened. */
	public boolean refresh(Font font, CheckboxConfig config, List<TodoEntry> entries, long now) {
		long current = signatureOf(config, entries, now);
		if (current == signature) {
			return false;
		}
		signature = current;
		rebuild(font, config, entries, now);
		return true;
	}

	public void invalidate() {
		signature = Long.MIN_VALUE;
	}

	private void rebuild(Font font, CheckboxConfig config, List<TodoEntry> entries, long now) {
		rows.clear();
		overflow = 0;
		int width = config.showTitle ? font.width(config.titleText) : 0;

		List<TodoEntry> visible = new ArrayList<>(entries.size());
		for (TodoEntry entry : entries) {
			if (isVisible(entry, config, now)) {
				visible.add(entry);
			}
		}
		// SPEC §3.4: outstanding work first, finished work after it.
		visible.sort((a, b) -> Boolean.compare(a.isDone(), b.isDone()));

		for (TodoEntry entry : visible) {
			if (rows.size() >= config.maxVisibleEntries) {
				overflow++;
				continue;
			}
			Row row = buildRow(font, config, entry, now);
			rows.add(row);
			int trailing = row.trailingWidth();
			width = Math.max(width, row.labelWidth() + (trailing > 0 ? LABEL_VALUE_GAP + trailing : 0));
		}

		if (overflow > 0) {
			width = Math.max(width, font.width("+" + overflow + " more"));
		}
		contentWidth = width;
	}

	private static Row buildRow(Font font, CheckboxConfig config, TodoEntry entry, long now) {
		boolean done = entry.isDone();

		Style style = done ? Style.EMPTY.withStrikethrough(true) : Style.EMPTY;
		Component component = Component.literal(done ? "[x] " : "[ ] ")
				.append(EntryLabels.labelOf(entry))
				.withStyle(style);

		String value = "";
		float fraction = 0f;
		boolean showBar = false;
		boolean expiredTimer = false;
		int rgb = done ? HudColors.TEXT_DONE : HudColors.TEXT;

		switch (entry) {
			case CounterEntry counter -> {
				value = counter.progress() + "/" + counter.target();
				fraction = counter.fraction();
				showBar = config.showProgressBar;
			}
			case TimerEntry timer -> {
				value = timer.formatRemaining(now);
				expiredTimer = timer.state() == TimerEntry.State.EXPIRED;
				if (timer.state() == TimerEntry.State.PAUSED) {
					rgb = HudColors.TEXT_PAUSED;
				}
			}
			default -> {
			}
		}

		return new Row(component, value, font.width(component), font.width(value), fraction,
				showBar, rgb, entry.completedAt(), expiredTimer, done, EntryLabels.iconOf(entry));
	}

	private static boolean isVisible(TodoEntry entry, CheckboxConfig config, long now) {
		if (!entry.isDone()) {
			return true;
		}
		if (!config.showCompleted) {
			return false;
		}
		if (config.completedBehaviour != CheckboxConfig.CompletedBehaviour.FADE) {
			return true;
		}
		// An entry completed before this session started has no useful completedAt.
		long completedAt = entry.completedAt();
		return completedAt > 0L && now - completedAt < config.completedFadeSeconds * 1000L;
	}

	private static long signatureOf(CheckboxConfig config, List<TodoEntry> entries, long now) {
		long result = config.layoutRevision();
		for (TodoEntry entry : entries) {
			result = 31 * result + entry.id().hashCode();
			result = 31 * result + entry.text().hashCode();
			result = 31 * result + (entry.isDone() ? 1 : 0);
			result = 31 * result + switch (entry) {
				case CounterEntry counter -> counter.progress() * 31L + counter.target();
				// Whole seconds only: a running countdown must not rebuild every frame.
				case TimerEntry timer -> timer.remainingMillis(now) / 1000L * 31L
						+ timer.state().ordinal();
				default -> 0L;
			};
			// Lets a fading entry drop out of the list when its time is up.
			if (entry.isDone() && entry.completedAt() > 0L) {
				result = 31 * result + (now - entry.completedAt()) / 1000L;
			}
		}
		return result;
	}
}

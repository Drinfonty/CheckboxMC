package com.drinfonty.checkbox.hud;

import com.drinfonty.checkbox.CheckboxClient;
import com.drinfonty.checkbox.model.CounterEntry;
import com.drinfonty.checkbox.model.TimerEntry;
import com.drinfonty.checkbox.model.TodoEntry;
import com.drinfonty.checkbox.track.MatchResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Display text and icons for entries.
 *
 * <p>Auto-generated descriptions are built here rather than stored, so "Collect 8 Oak Log"
 * follows the target if it is edited, and picks up the player's language.
 */
public final class EntryLabels {
	private EntryLabels() {
	}

	/** The description to show: the player's own text, or a generated one. */
	public static Component labelOf(TodoEntry entry) {
		if (entry instanceof CounterEntry counter && counter.autoLabel()) {
			return generate(counter);
		}
		return Component.literal(entry.text());
	}

	public static String plainLabelOf(TodoEntry entry) {
		return labelOf(entry).getString();
	}

	/** "Collect 8 Oak Log" / "Kill 10 Zombie". Also used to seed the stored fallback text. */
	public static Component generate(CounterEntry counter) {
		String verb = counter.match().kind().isItem() ? "Collect " : "Kill ";
		return Component.literal(verb + counter.target() + " ")
				.append(resolver().displayName(counter.match()));
	}

	/**
	 * The icon to draw beside an entry: the item for an item counter, the mob's spawn egg for
	 * a kill counter, and a clock for a timer.
	 *
	 * <p>Timers get one purely so the tracked entry types line up - a row without an icon in a
	 * list of rows with them reads as misaligned. Plain text entries have no value column
	 * either, so they are left alone.
	 */
	public static ItemStack iconOf(TodoEntry entry) {
		return switch (entry) {
			case CounterEntry counter -> resolver().icon(counter.match());
			case TimerEntry ignored -> new ItemStack(Items.CLOCK);
			default -> ItemStack.EMPTY;
		};
	}

	private static MatchResolver resolver() {
		return CheckboxClient.trackers().resolver();
	}
}

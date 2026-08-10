package com.drinfonty.checkbox.track;

import com.drinfonty.checkbox.Checkbox;
import com.drinfonty.checkbox.config.CheckboxConfig;
import com.drinfonty.checkbox.hud.EntryLabels;
import com.drinfonty.checkbox.model.TimerEntry;
import com.drinfonty.checkbox.model.TodoEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Announces a finished entry with a toast and a sound (SPEC §2.5).
 *
 * <p>One place for both so a completed counter and an expired timer are announced the same
 * way, differing only in the sound: a timer running out is an interruption and gets the
 * level-up chime, while finishing a tracked goal gets the lighter note-block pling.
 *
 * <p>Only automatic completions announce. Ticking an entry off by hand in the manager is
 * already its own feedback, and a chime for a button the player just pressed is noise.
 */
public final class Notifications {
	private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId();

	private Notifications() {
	}

	/** A tracked counter reached its target. */
	public static void entryCompleted(TodoEntry entry) {
		announce(Component.literal("Task complete"), EntryLabels.labelOf(entry),
				SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.0F));
	}

	/** A countdown ran out. */
	public static void timerExpired(TimerEntry timer) {
		announce(Component.literal("Timer finished"), EntryLabels.labelOf(timer),
				SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
	}

	private static void announce(Component title, Component message, SoundInstance sound) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return;
		}
		CheckboxConfig config = CheckboxConfig.get();
		if (Checkbox.debug()) {
			Checkbox.LOGGER.info("{}: {}", title.getString(), message.getString());
		}
		if (config.playSounds) {
			minecraft.getSoundManager().play(sound);
		}
		if (config.showToasts) {
			SystemToast.add(minecraft.gui.toastManager(), TOAST_ID, title, message);
		}
	}
}

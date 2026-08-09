package com.drinfonty.checkbox.track;

import com.drinfonty.checkbox.Checkbox;
import com.drinfonty.checkbox.config.CheckboxConfig;
import com.drinfonty.checkbox.model.TimerEntry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Drives countdowns and announces expiry (DESIGN §7).
 *
 * <p>The countdown arithmetic lives in {@link TimerEntry}; this class only decides *when* to
 * call it, and what a player should see when a timer runs out.
 */
public final class TimerService {
	private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId();

	/** Timers this service paused because the game was paused, to resume on unpause. */
	private final Set<UUID> autoPaused = new HashSet<>();

	private boolean wasPaused;

	public void tick(Minecraft minecraft, List<TimerEntry> timers, long nowMillis) {
		// Singleplayer pause freezes the world; a countdown that kept running through it
		// would be indefensible. Multiplayer never reports paused, so this is a no-op there.
		boolean paused = minecraft.isPaused();
		if (paused != wasPaused) {
			if (paused) {
				pauseAll(timers, nowMillis);
			} else {
				resumeAutoPaused(timers, nowMillis);
			}
			wasPaused = paused;
		}
		if (paused) {
			return;
		}

		for (TimerEntry timer : timers) {
			if (timer.update(nowMillis) && !timer.isNotified()) {
				timer.setNotified(true);
				announce(minecraft, timer);
			}
		}
	}

	/** Freezes running timers when leaving a world, preserving the time left. */
	public void pauseAll(List<TimerEntry> timers, long nowMillis) {
		for (TimerEntry timer : timers) {
			if (timer.state() == TimerEntry.State.RUNNING) {
				timer.pause(nowMillis);
				autoPaused.add(timer.id());
			}
		}
	}

	public void reset() {
		autoPaused.clear();
		wasPaused = false;
	}

	private void resumeAutoPaused(List<TimerEntry> timers, long nowMillis) {
		for (TimerEntry timer : timers) {
			if (autoPaused.remove(timer.id()) && timer.state() == TimerEntry.State.PAUSED) {
				timer.start(nowMillis);
			}
		}
		autoPaused.clear();
	}

	private void announce(Minecraft minecraft, TimerEntry timer) {
		if (Checkbox.DEBUG) {
			Checkbox.LOGGER.info("Timer expired: {}", timer.text());
		}
		CheckboxConfig config = CheckboxConfig.get();
		if (config.playSounds) {
			minecraft.getSoundManager()
					.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
		}
		if (config.showToasts) {
			SystemToast.add(minecraft.gui.toastManager(), TOAST_ID,
					Component.literal("Checkbox"), Component.literal(timer.text()));
		}
	}
}

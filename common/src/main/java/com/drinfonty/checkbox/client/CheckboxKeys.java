package com.drinfonty.checkbox.client;

import com.drinfonty.checkbox.Checkbox;
import com.drinfonty.checkbox.client.gui.CheckboxScreen;
import com.drinfonty.checkbox.config.CheckboxConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Key bindings (SPEC §5). Both defaults are unbound in vanilla.
 *
 * <p>The mappings are built here and registered by each loader, because that is the one part
 * of key handling the loaders do differently.
 */
public final class CheckboxKeys {
	/** Label comes from {@code key.category.checkbox.main} in the language file. */
	public static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Checkbox.id("main"));

	/**
	 * The {@code +} key - which is to say the {@code =} key, since Minecraft binds physical
	 * keys and cannot express shift as part of a binding. Pressing it with or without shift
	 * both open the manager.
	 */
	public static final KeyMapping OPEN = new KeyMapping(
			"key.checkbox.open", InputConstants.Type.KEYSYM, InputConstants.KEY_EQUALS, CATEGORY);

	public static final KeyMapping TOGGLE_HUD = new KeyMapping(
			"key.checkbox.toggle_hud", InputConstants.Type.KEYSYM, InputConstants.KEY_J, CATEGORY);

	private CheckboxKeys() {
	}

	/** Called once per client tick. */
	public static void handle(Minecraft minecraft) {
		boolean toggled = false;
		while (TOGGLE_HUD.consumeClick()) {
			toggled = true;
		}
		if (toggled) {
			CheckboxConfig config = CheckboxConfig.get();
			config.hudVisible = !config.hudVisible;
			config.save();
		}

		boolean open = false;
		while (OPEN.consumeClick()) {
			open = true;
		}
		// SPEC §5: presses are ignored while a screen is open, so this cannot reopen the
		// manager on top of itself.
		if (open && minecraft.gui.screen() == null) {
			minecraft.setScreenAndShow(new CheckboxScreen(null));
		}
	}
}

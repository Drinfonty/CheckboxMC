package com.drinfonty.checkbox.fabric;

import com.drinfonty.checkbox.Checkbox;
import com.drinfonty.checkbox.CheckboxClient;
import com.drinfonty.checkbox.client.CheckboxKeys;
import com.drinfonty.checkbox.hud.TodoHudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Fabric registration glue. All behaviour lives in {@code :common}; this class only wires
 * the loader's callbacks (HUD element, key mappings, client tick) into it.
 */
public class CheckboxFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Checkbox.init();
		ClientTickEvents.END_CLIENT_TICK.register(CheckboxClient::onClientTick);

		// Above chat, so the list sits over the HUD furniture without fighting the vanilla
		// overlays for space. Where it actually appears is the player's choice anyway.
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Checkbox.id("todo_list"),
				(graphics, deltaTracker) -> TodoHudRenderer.get().render(graphics, deltaTracker));

		KeyMappingHelper.registerKeyMapping(CheckboxKeys.OPEN);
		KeyMappingHelper.registerKeyMapping(CheckboxKeys.TOGGLE_HUD);
	}
}

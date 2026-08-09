package com.drinfonty.checkbox.fabric;

import com.drinfonty.checkbox.Checkbox;
import com.drinfonty.checkbox.CheckboxClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fabric registration glue. All behaviour lives in {@code :common}; this class only wires
 * the loader's callbacks (HUD element, key mappings, client tick) into it.
 */
public class CheckboxFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Checkbox.init();
		ClientTickEvents.END_CLIENT_TICK.register(CheckboxClient::onClientTick);
	}
}

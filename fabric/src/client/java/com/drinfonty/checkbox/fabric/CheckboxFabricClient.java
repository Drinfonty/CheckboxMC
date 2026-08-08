package com.drinfonty.checkbox.fabric;

import com.drinfonty.checkbox.Checkbox;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric registration glue. All behaviour lives in {@code :common}; this class only wires
 * the loader's callbacks (HUD element, key mappings, client tick) into it.
 */
public class CheckboxFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Checkbox.init();
	}
}

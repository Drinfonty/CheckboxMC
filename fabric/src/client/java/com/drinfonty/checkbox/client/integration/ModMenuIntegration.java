package com.drinfonty.checkbox.client.integration;

import com.drinfonty.checkbox.client.gui.CheckboxScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Opens the manager from ModMenu's mod list. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return CheckboxScreen::new;
	}
}

package com.drinfonty.checkbox.neoforge;

import com.drinfonty.checkbox.Checkbox;
import com.drinfonty.checkbox.CheckboxClient;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge registration glue. All behaviour lives in {@code :common}; this class only wires
 * the loader's events (gui layer, key mappings, client tick) into it.
 *
 * <p>Checkbox is client-only, so everything is behind a {@link Dist#CLIENT} guard - the jar
 * still loads on a dedicated server, it just does nothing there.
 */
@Mod("checkbox")
public class CheckboxNeoForge {
	public CheckboxNeoForge(ModContainer container) {
		if (FMLEnvironment.getDist() == Dist.CLIENT) {
			Checkbox.init();
			NeoForge.EVENT_BUS.addListener(CheckboxNeoForge::onClientTick);
		}
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		CheckboxClient.onClientTick(Minecraft.getInstance());
	}
}

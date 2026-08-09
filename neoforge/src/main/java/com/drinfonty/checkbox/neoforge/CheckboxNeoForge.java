package com.drinfonty.checkbox.neoforge;

import com.drinfonty.checkbox.Checkbox;
import com.drinfonty.checkbox.CheckboxClient;
import com.drinfonty.checkbox.hud.TodoHudRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
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
	public CheckboxNeoForge(ModContainer container, IEventBus modBus) {
		if (FMLEnvironment.getDist() == Dist.CLIENT) {
			Checkbox.init();
			NeoForge.EVENT_BUS.addListener(CheckboxNeoForge::onClientTick);
			modBus.addListener(CheckboxNeoForge::onRegisterGuiLayers);
		}
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		CheckboxClient.onClientTick(Minecraft.getInstance());
	}

	private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
		// Fabric's HudElement and NeoForge's GuiLayer take the same arguments on 26.2, so both
		// loaders hand straight off to the shared renderer.
		event.registerAbove(VanillaGuiLayers.CHAT, Checkbox.id("todo_list"),
				(graphics, deltaTracker) -> TodoHudRenderer.get().render(graphics, deltaTracker));
	}
}

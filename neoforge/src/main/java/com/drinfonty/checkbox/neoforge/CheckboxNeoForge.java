package com.drinfonty.checkbox.neoforge;

import com.drinfonty.checkbox.Checkbox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

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
		}
	}
}

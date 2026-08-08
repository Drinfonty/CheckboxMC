package com.drinfonty.checkbox;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Checkbox {
	public static final String MOD_ID = "checkbox";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Tracker diagnostics, off unless {@code -Dcheckbox.debug=true} is passed. The Gradle
	 * dev run configurations set it; a real launcher never does, so players never see it.
	 *
	 * <p>Guard at the call site rather than logging unconditionally - the tracking hooks
	 * fire on every damage event and inventory census, and their arguments are not free to
	 * compute. Being {@code static final}, the JIT folds the branch away when disabled.
	 */
	public static final boolean DEBUG = Boolean.getBoolean("checkbox.debug");

	public static void init() {
		LOGGER.info("Initializing Checkbox client!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

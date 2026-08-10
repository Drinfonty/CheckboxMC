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
	private static final boolean DEBUG_PROPERTY = Boolean.getBoolean("checkbox.debug");

	/**
	 * Whether to emit tracker diagnostics.
	 *
	 * <p>Also switchable from the config file, because the system property means editing JVM
	 * arguments in a launcher - which is exactly the wrong amount of friction when someone is
	 * trying to work out why tracking is not firing for them.
	 */
	public static boolean debug() {
		return DEBUG_PROPERTY || com.drinfonty.checkbox.config.CheckboxConfig.get().debugLogging;
	}

	public static void init() {
		LOGGER.info("Initializing Checkbox client!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

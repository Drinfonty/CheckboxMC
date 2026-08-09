package com.drinfonty.checkbox.client.gui;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Hides items that cannot be collected, so they never appear as a tracking target.
 *
 * <p>Spawn eggs and operator utilities (command blocks, barriers, the debug stick) are
 * creative-only; a "collect 8 barriers" entry could never progress. They are identified by
 * creative tab membership rather than a hardcoded list, so modded op tabs are covered too and
 * the set follows the game rather than this file.
 */
public final class ItemFilter {
	private static final Set<String> EXCLUDED_TABS = Set.of("spawn_eggs", "op_blocks");

	private static Set<Item> excluded;

	private ItemFilter() {
	}

	/** True if the tab as a whole should not be offered. */
	public static boolean isExcludedTab(CreativeModeTab tab) {
		Identifier key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
		return key != null && EXCLUDED_TABS.contains(key.getPath());
	}

	public static boolean isExcluded(Item item) {
		return item != null && excluded().contains(item);
	}

	public static boolean isExcluded(Identifier id) {
		return id != null && isExcluded(BuiltInRegistries.ITEM.getValue(id));
	}

	/** Discards the cached set, for a world whose datapacks may define different tabs. */
	public static void invalidate() {
		excluded = null;
	}

	private static Set<Item> excluded() {
		if (excluded == null) {
			excluded = build();
		}
		return excluded;
	}

	private static Set<Item> build() {
		Set<Item> set = new HashSet<>();

		// Tab contents are built lazily by whoever asks first, normally the creative
		// inventory - which a survival player may never have opened.
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null && minecraft.level != null) {
			CreativeModeTabs.tryRebuildTabContents(
					minecraft.level.enabledFeatures(),
					minecraft.player != null && minecraft.player.canUseGameMasterBlocks(),
					minecraft.level.registryAccess());
		}

		// allTabs() rather than tabs(): the operator tab is hidden from the visible list
		// unless the player is an operator, and its contents still need excluding.
		for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
			if (isExcludedTab(tab)) {
				for (ItemStack stack : tab.getDisplayItems()) {
					set.add(stack.getItem());
				}
			}
		}

		// If the tab contents were still empty, spawn eggs are at least recognisable by name,
		// so the common case degrades rather than failing open.
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (id.getPath().endsWith("_spawn_egg")) {
				set.add(BuiltInRegistries.ITEM.getValue(id));
			}
		}
		return set;
	}
}

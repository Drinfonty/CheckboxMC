package com.drinfonty.checkbox.client.gui;

import com.drinfonty.checkbox.model.EntryMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Registry id completion for the entry editor's id field.
 *
 * <p>Ranked rather than plain alphabetical: what a player types is almost always the start of
 * the path ("oak" wanting oak_log), so prefix matches come first and substring matches follow.
 */
public final class RegistrySuggestions {
	private RegistrySuggestions() {
	}

	/**
	 * @param query what the player has typed, with or without a namespace
	 * @return matching ids, best first, at most {@code limit}
	 */
	public static List<Identifier> match(EntryMatch.Kind kind, String query, int limit) {
		String needle = query.trim().toLowerCase(Locale.ROOT);
		if (needle.startsWith("#")) {
			needle = needle.substring(1);
		}

		boolean qualified = needle.indexOf(':') >= 0;
		List<Identifier> ids = new ArrayList<>(
				kind.isItem() ? BuiltInRegistries.ITEM.keySet() : BuiltInRegistries.ENTITY_TYPE.keySet());

		String finalNeedle = needle;
		boolean items = kind.isItem();
		List<Identifier> matches = new ArrayList<>();
		for (Identifier id : ids) {
			// Only offer what could actually be tracked: collectable items, killable mobs.
			if (items ? ItemFilter.isExcluded(id) : !isKillable(id)) {
				continue;
			}
			if (rank(id, finalNeedle, qualified) >= 0) {
				matches.add(id);
			}
		}

		matches.sort(Comparator
				.comparingInt((Identifier id) -> rank(id, finalNeedle, qualified))
				.thenComparing(Identifier::toString));
		return matches.size() > limit ? List.copyOf(matches.subList(0, limit)) : List.copyOf(matches);
	}

	/** Lower is better; -1 means no match. */
	private static int rank(Identifier id, String needle, boolean qualified) {
		if (needle.isEmpty()) {
			// An empty field offers vanilla ids rather than whatever sorts first overall.
			return id.getNamespace().equals("minecraft") ? 0 : 1;
		}
		String path = id.getPath();
		if (qualified) {
			String full = id.toString();
			return full.startsWith(needle) ? 0 : (full.contains(needle) ? 1 : -1);
		}
		// Unqualified queries match the path only. Matching the whole id would make every
		// vanilla entry match "cr", because "minecraft" contains it.
		if (path.startsWith(needle)) {
			return id.getNamespace().equals("minecraft") ? 0 : 1;
		}
		return path.contains(needle) ? (id.getNamespace().equals("minecraft") ? 2 : 3) : -1;
	}

	/**
	 * Whether an entity type is something a kill counter could ever credit.
	 *
	 * <p>Tested by whether the type has living-entity attributes, which only a
	 * {@code LivingEntity} does - so this is exactly the set kill detection can see, since it
	 * keys off {@code LivingEntity.deathTime}.
	 *
	 * <p>Two tempting alternatives are both wrong. {@code EntityType.getBaseClass()} is a stub
	 * in 26.2 that returns {@code Entity.class} for every type, so a class test silently
	 * matches nothing. {@code MobCategory} misses iron and snow golems, which are {@code MISC}
	 * yet perfectly killable.
	 */
	public static boolean isKillable(Identifier id) {
		if (id == null) {
			return false;
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
		return type != null && DefaultAttributes.hasSupplier(type);
	}

	/** The icon to show beside a suggestion: the item, or the mob's spawn egg. */
	public static ItemStack iconFor(EntryMatch.Kind kind, Identifier id) {
		if (kind.isItem()) {
			Item item = BuiltInRegistries.ITEM.getValue(id);
			return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
		return type == null
				? ItemStack.EMPTY
				: SpawnEggItem.byId(type).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	/**
	 * Every collectable item, for the grid picker. Air is not a thing you can collect, and
	 * neither are spawn eggs or operator utilities.
	 */
	public static List<Identifier> allItems() {
		List<Identifier> ids = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			Item item = BuiltInRegistries.ITEM.getValue(id);
			if (item != Items.AIR && !ItemFilter.isExcluded(item)) {
				ids.add(id);
			}
		}
		return ids;
	}
}

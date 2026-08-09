package com.drinfonty.checkbox.track;

import com.drinfonty.checkbox.Checkbox;
import com.drinfonty.checkbox.model.EntryMatch;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Resolves an {@link EntryMatch}'s stored registry id against the client's registries, and
 * caches the result.
 *
 * <p>The cache matters because matching runs per inventory slot per entry, several times a
 * second. It is cleared on world change: tags come from the server's datapacks and can differ
 * between worlds, and an item id that failed to resolve in one world may resolve in the next.
 */
public final class MatchResolver {
	/** Marker for an id that does not resolve, so we do not retry the lookup every tick. */
	private static final Object UNRESOLVED = new Object();

	private final Map<EntryMatch, Object> cache = new HashMap<>();

	public boolean matches(EntryMatch match, ItemStack stack) {
		if (match == null || stack.isEmpty() || !match.kind().isItem()) {
			return false;
		}
		Object resolved = resolve(match);
		if (resolved == UNRESOLVED) {
			return false;
		}
		if (match.kind() == EntryMatch.Kind.ITEM) {
			return stack.getItem() == resolved;
		}
		@SuppressWarnings("unchecked")
		Predicate<Holder<Item>> tagPredicate = (Predicate<Holder<Item>>) resolved;
		return stack.is(tagPredicate);
	}

	public boolean matches(EntryMatch match, EntityType<?> type) {
		if (match == null || type == null || match.kind().isItem()) {
			return false;
		}
		Object resolved = resolve(match);
		if (resolved == UNRESOLVED) {
			return false;
		}
		if (match.kind() == EntryMatch.Kind.ENTITY) {
			return type == resolved;
		}
		@SuppressWarnings("unchecked")
		TagKey<EntityType<?>> tag = (TagKey<EntityType<?>>) resolved;
		return type.builtInRegistryHolder().is(tag);
	}

	/** True if the id names something this client knows about, for greying out dead entries. */
	public boolean isResolvable(EntryMatch match) {
		return match != null && resolve(match) != UNRESOLVED;
	}

	/**
	 * An icon for the entry: the item itself, or the mob's spawn egg for a kill counter.
	 * Empty when nothing sensible resolves - tags name a set, not a thing.
	 */
	public ItemStack icon(EntryMatch match) {
		if (match == null || match.kind().isTag()) {
			return ItemStack.EMPTY;
		}
		Object resolved = resolve(match);
		if (resolved == UNRESOLVED) {
			return ItemStack.EMPTY;
		}
		if (match.kind() == EntryMatch.Kind.ITEM) {
			return new ItemStack((Item) resolved);
		}
		return SpawnEggItem.byId((EntityType<?>) resolved)
				.map(ItemStack::new)
				.orElse(ItemStack.EMPTY);
	}

	/**
	 * The translated name of whatever the entry tracks, for generated descriptions. Falls back
	 * to the raw id so an entry for something this client lacks still reads sensibly.
	 */
	public Component displayName(EntryMatch match) {
		if (match == null) {
			return Component.empty();
		}
		Object resolved = resolve(match);
		if (resolved == UNRESOLVED) {
			return Component.literal(match.display());
		}
		return switch (match.kind()) {
			case ITEM -> new ItemStack((Item) resolved).getHoverName();
			case ENTITY -> ((EntityType<?>) resolved).getDescription();
			default -> Component.literal(match.display());
		};
	}

	public void clearCache() {
		cache.clear();
	}

	private Object resolve(EntryMatch match) {
		return cache.computeIfAbsent(match, MatchResolver::lookup);
	}

	private static Object lookup(EntryMatch match) {
		Identifier id = Identifier.tryParse(match.id());
		if (id == null) {
			if (Checkbox.DEBUG) {
				Checkbox.LOGGER.warn("Checkbox entry has an unparseable id: {}", match.id());
			}
			return UNRESOLVED;
		}

		return switch (match.kind()) {
			case ITEM -> BuiltInRegistries.ITEM.getOptional(id).map(Object.class::cast)
					.orElse(UNRESOLVED);
			case ENTITY -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(Object.class::cast)
					.orElse(UNRESOLVED);
			// Tags are resolved optimistically: an unknown tag simply never matches, and a
			// datapack may define it in the next world.
			case ITEM_TAG -> {
				TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
				Predicate<Holder<Item>> predicate = holder -> holder.is(tag);
				yield predicate;
			}
			case ENTITY_TAG -> TagKey.create(Registries.ENTITY_TYPE, id);
		};
	}
}

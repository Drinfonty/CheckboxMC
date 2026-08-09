package com.drinfonty.checkbox.client.gui;

import com.drinfonty.checkbox.hud.HudColors;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A creative-inventory-shaped item browser, so an id can be chosen by sight.
 *
 * <p>Grouped into the game's own creative tabs rather than one flat list. The tab contents
 * come from {@link CreativeModeTabs}, which is populated once the player is in a world; any
 * tab that comes back empty is dropped, and the "All" tab from the registry is always present
 * as a fallback.
 */
public class ItemPickerScreen extends Screen {
	private static final int SLOT = 18;
	private static final int TAB_SIZE = 20;
	private static final int MAX_COLUMNS = 13;
	private static final int TABS_TOP = 50;
	private static final int GRID_TOP = TABS_TOP + TAB_SIZE + 4;
	/** Room below the grid for the count label and the Cancel button, without crowding. */
	private static final int BOTTOM_MARGIN = 52;

	/** Padding between a panel's border and the cells inside it. */
	private static final int PANEL_INSET = 3;

	private static final int PANEL_BG = 0x101010;
	private static final int PANEL_BORDER = 0x8B8B8B;
	private static final int SLOT_BG = 0x2A2A2A;
	private static final int SLOT_HOVER = 0x7A7A7A;
	private static final int TAB_ACTIVE = 0x9A9A9A;
	private static final int TAB_IDLE = 0x2A2A2A;

	private record Choice(Identifier id, ItemStack stack, String searchText) {
	}

	private record Tab(Component name, ItemStack icon, List<Choice> choices) {
	}

	private final Screen parent;
	private final Consumer<String> onPick;
	private final List<Tab> tabs = new ArrayList<>();

	private List<Choice> visible = List.of();
	private EditBox search;
	private int selectedTab;
	private int columns;
	private int rows;
	private int gridX;
	private int tabsX;
	private int scrollRow;

	public ItemPickerScreen(Screen parent, Consumer<String> onPick) {
		super(Component.literal("Choose an item"));
		this.parent = parent;
		this.onPick = onPick;
	}

	@Override
	protected void init() {
		if (tabs.isEmpty()) {
			buildTabs();
		}

		this.columns = Math.max(1, Math.min(MAX_COLUMNS, (this.width - 40) / SLOT));
		this.rows = Math.max(1, (this.height - GRID_TOP - BOTTOM_MARGIN) / SLOT);
		this.gridX = (this.width - columns * SLOT) / 2;
		this.tabsX = (this.width - tabs.size() * TAB_SIZE) / 2;

		this.search = new EditBox(this.font, this.width / 2 - 100, 24, 200, 20,
				Component.literal("Search"));
		this.search.setHint(Component.literal("Search items..."));
		this.search.setResponder(value -> {
			applyFilter();
			scrollRow = 0;
		});
		this.addRenderableWidget(this.search);
		setInitialFocus(this.search);

		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
				.bounds(this.width / 2 - 50, this.height - 28, 100, 20)
				.build());

		applyFilter();
	}

	private void buildTabs() {
		// Rebuilt per screen so a different world's datapacks are picked up. This also forces
		// the creative tab contents to be populated, which the tabs below depend on.
		ItemFilter.invalidate();

		List<Choice> everything = new ArrayList<>();
		for (Identifier id : RegistrySuggestions.allItems()) {
			everything.add(choiceOf(id, new ItemStack(BuiltInRegistries.ITEM.getValue(id))));
		}
		tabs.add(new Tab(Component.literal("All"),
				new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:chest"))),
				everything));

		for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
			// Spawn eggs and operator utilities cannot be collected, so the whole category
			// goes rather than leaving a tab that filters down to nothing.
			if (tab == CreativeModeTabs.searchTab() || ItemFilter.isExcludedTab(tab)) {
				continue;
			}
			// A tab's display items can repeat one item with different components
			// (potions, enchanted books); an id only needs to appear once.
			Set<Item> seen = new LinkedHashSet<>();
			List<Choice> choices = new ArrayList<>();
			for (ItemStack stack : tab.getDisplayItems()) {
				// An excluded item can still appear in an ordinary tab, so filter per item as
				// well as per tab.
				if (!stack.isEmpty() && !ItemFilter.isExcluded(stack.getItem())
						&& seen.add(stack.getItem())) {
					Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
					choices.add(choiceOf(id, new ItemStack(stack.getItem())));
				}
			}
			// Inventory and hotbar tabs have no display items; so do all tabs if the creative
			// contents were never built. Either way there is nothing to show.
			if (!choices.isEmpty()) {
				tabs.add(new Tab(tab.getDisplayName(), tab.getIconItem(), choices));
			}
		}
	}

	private static Choice choiceOf(Identifier id, ItemStack stack) {
		return new Choice(id, stack,
				(id + " " + stack.getHoverName().getString()).toLowerCase(Locale.ROOT));
	}

	/** Search looks across every item; a query is a stronger intent than the open tab. */
	private void applyFilter() {
		String needle = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		List<Choice> source = needle.isEmpty()
				? tabs.get(selectedTab).choices()
				: tabs.get(0).choices();

		if (needle.isEmpty()) {
			visible = source;
			return;
		}
		List<Choice> filtered = new ArrayList<>();
		for (Choice choice : source) {
			if (choice.searchText().contains(needle)) {
				filtered.add(choice);
			}
		}
		visible = filtered;
	}

	private boolean searching() {
		return search != null && !search.getValue().isBlank();
	}

	private int maxScrollRow() {
		int totalRows = (visible.size() + columns - 1) / columns;
		return Math.max(0, totalRows - rows);
	}

	private int indexAt(double mouseX, double mouseY) {
		// Reject anything above or left of the grid *before* dividing. Integer division
		// truncates toward zero, so a cursor in the 17px above the grid - which is exactly
		// where the tab strip sits - would otherwise compute as row 0 and claim the hover.
		if (mouseX < gridX || mouseY < GRID_TOP) {
			return -1;
		}
		int col = (int) ((mouseX - gridX) / SLOT);
		int row = (int) ((mouseY - GRID_TOP) / SLOT);
		if (col >= columns || row >= rows) {
			return -1;
		}
		int index = (scrollRow + row) * columns + col;
		return index < visible.size() ? index : -1;
	}

	private int tabAt(double mouseX, double mouseY) {
		if (mouseY < TABS_TOP || mouseY >= TABS_TOP + TAB_SIZE || mouseX < tabsX) {
			return -1;
		}
		int index = (int) ((mouseX - tabsX) / TAB_SIZE);
		return index >= 0 && index < tabs.size() ? index : -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int tab = tabAt(event.x(), event.y());
		if (tab >= 0) {
			selectedTab = tab;
			scrollRow = 0;
			// Switching tab is a navigation act; a stale query would hide the whole tab.
			search.setValue("");
			applyFilter();
			return true;
		}

		int index = indexAt(event.x(), event.y());
		if (index >= 0) {
			onPick.accept(visible.get(index).id().toString());
			this.minecraft.setScreenAndShow(parent);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			scrollRow = Math.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScrollRow());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

		int hoveredTab = tabAt(mouseX, mouseY);
		drawPanel(graphics, tabsX, TABS_TOP, tabs.size() * TAB_SIZE, TAB_SIZE);
		for (int i = 0; i < tabs.size(); i++) {
			int x = tabsX + i * TAB_SIZE;
			boolean active = i == selectedTab && !searching();
			graphics.fill(x, TABS_TOP, x + TAB_SIZE - 1, TABS_TOP + TAB_SIZE - 1,
					HudColors.argb(active ? TAB_ACTIVE : (i == hoveredTab ? SLOT_HOVER : TAB_IDLE),
							0.9f));
			if (active) {
				graphics.outline(x, TABS_TOP, TAB_SIZE - 1, TAB_SIZE - 1,
						HudColors.argb(0xFFFFFF, 0.9f));
			}
			graphics.item(tabs.get(i).icon(), x + 2, TABS_TOP + 2);
		}

		int hovered = indexAt(mouseX, mouseY);
		drawPanel(graphics, gridX, GRID_TOP, columns * SLOT, rows * SLOT);
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < columns; col++) {
				int index = (scrollRow + row) * columns + col;
				if (index >= visible.size()) {
					break;
				}
				int x = gridX + col * SLOT;
				int y = GRID_TOP + row * SLOT;
				graphics.fill(x, y, x + SLOT - 1, y + SLOT - 1,
						HudColors.argb(index == hovered ? SLOT_HOVER : SLOT_BG, 0.9f));
				graphics.item(visible.get(index).stack(), x + 1, y + 1);
			}
		}

		String label = searching()
				? visible.size() + " matching"
				: tabs.get(selectedTab).name().getString() + " - " + visible.size() + " items";
		// Positioned from the grid rather than the screen bottom, so it always clears the
		// panel border instead of landing on it at some window heights.
		graphics.centeredText(this.font, Component.literal(label), this.width / 2,
				GRID_TOP + rows * SLOT + PANEL_INSET + 5, 0xFFAAAAAA);

		// Tooltips last, so they draw above the grid. Tabs win: they sit outside the grid, so
		// if both ever claim the cursor again it is the grid that is wrong.
		if (hoveredTab >= 0) {
			graphics.setTooltipForNextFrame(this.font, tabs.get(hoveredTab).name(),
					mouseX, mouseY);
		} else if (hovered >= 0) {
			graphics.setTooltipForNextFrame(this.font, visible.get(hovered).stack(),
					mouseX, mouseY);
		}
	}

	/** A bordered backing panel behind a block of cells, so the region reads as one surface. */
	private static void drawPanel(GuiGraphicsExtractor graphics, int x, int y,
			int contentWidth, int contentHeight) {
		int left = x - PANEL_INSET;
		int top = y - PANEL_INSET;
		int width = contentWidth + PANEL_INSET * 2;
		int height = contentHeight + PANEL_INSET * 2;
		graphics.fill(left, top, left + width, top + height, HudColors.argb(PANEL_BG, 0.85f));
		graphics.outline(left, top, width, height, HudColors.argb(PANEL_BORDER, 1f));
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(parent);
	}
}

package com.drinfonty.checkbox.client.gui;

import com.drinfonty.checkbox.CheckboxClient;
import com.drinfonty.checkbox.config.CheckboxConfig;
import com.drinfonty.checkbox.hud.HudColors;
import com.drinfonty.checkbox.model.CounterEntry;
import com.drinfonty.checkbox.model.EntryMatch;
import com.drinfonty.checkbox.model.EntryScope;
import com.drinfonty.checkbox.model.TextEntry;
import com.drinfonty.checkbox.model.TimerEntry;
import com.drinfonty.checkbox.model.TodoEntry;
import com.drinfonty.checkbox.store.TodoStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The list manager (SPEC §4.1).
 *
 * <p>Row actions live in the footer and operate on the selection, rather than as buttons
 * inside each row: it keeps rows readable at any list length, and keeps focus handling to the
 * one thing {@link ObjectSelectionList} already does well.
 */
public class CheckboxScreen extends Screen {
	private static final int ROW_HEIGHT = 14;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 4;
	private static final int LIST_TOP = 28;
	private static final int FOOTER_HEIGHT = 3 * (BUTTON_HEIGHT + GAP) + GAP;

	private final Screen parent;

	private EntryList list;
	private Button editButton;
	private Button toggleButton;
	private Button upButton;
	private Button downButton;
	private Button deleteButton;
	private Button hudButton;

	public CheckboxScreen(Screen parent) {
		super(Component.literal("Checkbox"));
		this.parent = parent;
	}

	private static TodoStore store() {
		return CheckboxClient.store();
	}

	@Override
	protected void init() {
		int footerTop = this.height - FOOTER_HEIGHT;
		this.list = new EntryList(this.minecraft, this.width, footerTop - LIST_TOP - GAP, LIST_TOP);
		this.addRenderableWidget(this.list);

		int columns = 4;
		int margin = 16;
		int usable = this.width - margin * 2;
		int cell = (usable - GAP * (columns - 1)) / columns;
		int y = footerTop;

		boolean canAdd = store().isOpen();
		addRow(margin, y, cell,
				addButton("+ Text", canAdd, () -> openEditor(TodoEntry.Type.TEXT, null, null)),
				addButton("+ Item", canAdd,
						() -> openEditor(TodoEntry.Type.COUNTER, EntryMatch.Kind.ITEM, null)),
				addButton("+ Kill", canAdd,
						() -> openEditor(TodoEntry.Type.COUNTER, EntryMatch.Kind.ENTITY, null)),
				addButton("+ Timer", canAdd, () -> openEditor(TodoEntry.Type.TIMER, null, null)));

		y += BUTTON_HEIGHT + GAP;
		int cell5 = (usable - GAP * 4) / 5;
		this.editButton = addButton("Edit", false, this::editSelected);
		this.toggleButton = addButton("Toggle", false, this::toggleSelected);
		this.upButton = addButton("Move Up", false, () -> move(true));
		this.downButton = addButton("Move Down", false, () -> move(false));
		this.deleteButton = addButton("Delete", false, this::deleteSelected);
		addRow(margin, y, cell5, editButton, toggleButton, upButton, downButton, deleteButton);

		y += BUTTON_HEIGHT + GAP;
		int cell4 = (usable - GAP * 3) / 4;
		this.hudButton = addButton(hudButtonLabel(), true, this::toggleHud);
		addRow(margin, y, cell4,
				hudButton,
				addButton("HUD Settings", true,
						() -> this.minecraft.setScreenAndShow(new HudSettingsScreen(this))),
				addButton("Clear Completed", canAdd, this::clearCompleted),
				addButton("Done", true, this::onClose));

		// Only now that both the list field and the buttons exist is it safe to populate,
		// because filling the list reports a selection change back to this screen.
		this.list.reload(null);
		refreshButtons();
	}

	private Button addButton(String label, boolean active, Runnable action) {
		Button button = Button.builder(Component.literal(label), b -> action.run())
				.bounds(0, 0, 20, BUTTON_HEIGHT)
				.build();
		button.active = active;
		return this.addRenderableWidget(button);
	}

	private static void addRow(int x, int y, int cellWidth, Button... buttons) {
		for (int i = 0; i < buttons.length; i++) {
			buttons[i].setX(x + i * (cellWidth + GAP));
			buttons[i].setY(y);
			buttons[i].setWidth(cellWidth);
		}
	}

	private String hudButtonLabel() {
		return "HUD: " + (CheckboxConfig.get().hudVisible ? "Shown" : "Hidden");
	}

	private void toggleHud() {
		CheckboxConfig config = CheckboxConfig.get();
		config.hudVisible = !config.hudVisible;
		config.save();
		hudButton.setMessage(Component.literal(hudButtonLabel()));
	}

	private void openEditor(TodoEntry.Type type, EntryMatch.Kind kind, TodoEntry existing) {
		this.minecraft.setScreenAndShow(new EntryEditScreen(this, type, kind, existing));
	}

	private TodoEntry selected() {
		EntryList.Row row = this.list.getSelected();
		return row == null ? null : row.entry;
	}

	private void editSelected() {
		TodoEntry entry = selected();
		if (entry == null) {
			return;
		}
		EntryMatch.Kind kind = entry instanceof CounterEntry counter ? counter.match().kind() : null;
		openEditor(entry.type(), kind, entry);
	}

	private void toggleSelected() {
		TodoEntry entry = selected();
		long now = System.currentTimeMillis();
		switch (entry) {
			case TextEntry text -> text.toggle(now);
			case CounterEntry counter -> {
				// A manual toggle on a tracked counter means "call it done" / "start over".
				if (counter.isDone()) {
					counter.resetProgress(now);
				} else {
					counter.addProgress(counter.target() - counter.progress(), now);
				}
			}
			case TimerEntry timer -> {
				if (timer.state() == TimerEntry.State.RUNNING) {
					timer.pause(now);
				} else if (timer.state() == TimerEntry.State.EXPIRED) {
					timer.reset(now);
				} else {
					timer.start(now);
				}
			}
			case null, default -> {
				return;
			}
		}
		rebuild();
	}

	private void move(boolean up) {
		TodoEntry entry = selected();
		if (entry == null) {
			return;
		}
		boolean moved = up
				? store().listFor(entry.scope()).moveUp(entry.id())
				: store().listFor(entry.scope()).moveDown(entry.id());
		if (moved) {
			rebuild(entry);
		}
	}

	private void deleteSelected() {
		TodoEntry entry = selected();
		if (entry != null) {
			store().remove(entry.id());
			rebuild();
		}
	}

	/**
	 * SPEC §4.1: deleting one entry is immediate, but clearing every completed entry asks
	 * first - it is the one action here that can throw away more than the player meant to.
	 */
	private void clearCompleted() {
		long completed = store().entries().stream().filter(TodoEntry::isDone).count();
		if (completed == 0) {
			return;
		}
		this.minecraft.setScreenAndShow(new ConfirmScreen(
				confirmed -> {
					if (confirmed && store().clearCompleted() > 0) {
						rebuild();
					}
					this.minecraft.setScreenAndShow(this);
				},
				Component.literal("Clear completed entries?"),
				Component.literal("This removes " + completed
						+ (completed == 1 ? " finished entry." : " finished entries."))));
	}

	/** Rebuilds the rows, keeping the selection on the given entry when it still exists. */
	private void rebuild(TodoEntry keepSelected) {
		this.list.reload(keepSelected);
		refreshButtons();
	}

	private void rebuild() {
		rebuild(null);
	}

	private void refreshButtons() {
		boolean hasSelection = selected() != null;
		editButton.active = hasSelection;
		toggleButton.active = hasSelection;
		upButton.active = hasSelection;
		downButton.active = hasSelection;
		deleteButton.active = hasSelection;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

		if (!store().isOpen()) {
			graphics.centeredText(this.font,
					Component.literal("Join a world to manage your list"),
					this.width / 2, LIST_TOP + 20, 0xFFAAAAAA);
		} else if (this.list.children().isEmpty()) {
			graphics.centeredText(this.font,
					Component.literal("No entries yet - add one below"),
					this.width / 2, LIST_TOP + 20, 0xFFAAAAAA);
		}
	}

	@Override
	public void onClose() {
		// Anything edited here should be on disk before the player can quit the game.
		store().flush(true);
		this.minecraft.setScreenAndShow(this.parent);
	}

	/** Selection changes come through the list, so the footer follows them. */
	private void onSelectionChanged() {
		refreshButtons();
	}

	class EntryList extends ObjectSelectionList<EntryList.Row> {
		EntryList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
			// Deliberately does not populate here. reload() ends in setSelected(), which is
			// overridden to call back into the screen - from a constructor that would reach
			// the outer field before it has been assigned. init() populates instead.
		}

		void reload(TodoEntry keepSelected) {
			this.clearEntries();
			Row toSelect = null;
			for (TodoEntry entry : store().entries()) {
				Row row = new Row(entry);
				this.addEntry(row);
				if (keepSelected != null && keepSelected.id().equals(entry.id())) {
					toSelect = row;
				}
			}
			this.setSelected(toSelect);
		}

		@Override
		public void setSelected(Row selected) {
			super.setSelected(selected);
			CheckboxScreen.this.onSelectionChanged();
		}

		class Row extends ObjectSelectionList.Entry<Row> {
			private final TodoEntry entry;

			Row(TodoEntry entry) {
				this.entry = entry;
			}

			@Override
			public Component getNarration() {
				return Component.literal(entry.text());
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
					boolean hovered, float partialTick) {
				int x = getContentX();
				int y = getContentY() + 2;
				int rgb = entry.isDone() ? HudColors.TEXT_DONE : HudColors.TEXT;

				String prefix = entry.isDone() ? "[x] " : "[ ] ";
				graphics.text(CheckboxScreen.this.font, prefix + entry.text(), x, y,
						HudColors.argb(rgb, 1f));

				String value = valueOf(entry);
				if (!value.isEmpty()) {
					int width = CheckboxScreen.this.font.width(value);
					graphics.text(CheckboxScreen.this.font, value,
							getContentRight() - width, y, HudColors.argb(HudColors.VALUE, 1f));
				}
			}

			private String valueOf(TodoEntry entry) {
				return switch (entry) {
					case CounterEntry counter -> counter.progress() + "/" + counter.target()
							+ (entry.scope() == EntryScope.GLOBAL ? "  (global)" : "");
					case TimerEntry timer -> timer.formatRemaining(System.currentTimeMillis())
							+ "  " + timer.state().name().toLowerCase(java.util.Locale.ROOT);
					default -> entry.scope() == EntryScope.GLOBAL ? "(global)" : "";
				};
			}
		}
	}
}

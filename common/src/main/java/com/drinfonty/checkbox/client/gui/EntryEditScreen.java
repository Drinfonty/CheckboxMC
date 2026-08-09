package com.drinfonty.checkbox.client.gui;

import com.drinfonty.checkbox.CheckboxClient;
import com.drinfonty.checkbox.config.CheckboxConfig;
import com.drinfonty.checkbox.hud.EntryLabels;
import com.drinfonty.checkbox.hud.TodoHudRenderer;
import com.drinfonty.checkbox.model.CounterEntry;
import com.drinfonty.checkbox.model.EntryMatch;
import com.drinfonty.checkbox.model.EntryScope;
import com.drinfonty.checkbox.model.TextEntry;
import com.drinfonty.checkbox.model.TimerEntry;
import com.drinfonty.checkbox.model.TodoEntry;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Creates and edits a single entry (SPEC §4.2).
 *
 * <p>Validation is continuous rather than on submit: the confirm button stays disabled and an
 * inline reason is shown, so an invalid value can never reach the model constructors, which
 * throw by design.
 */
public class EntryEditScreen extends Screen {
	private static final int FIELD_WIDTH = 240;
	private static final int FIELD_HEIGHT = 20;
	private static final int ROW = 24;

	private final Screen parent;
	private final TodoEntry.Type type;
	private final EntryMatch.Kind matchKind;
	private final TodoEntry existing;

	private EditBox textBox;
	private EditBox idBox;
	private EditBox targetBox;
	private EditBox hoursBox;
	private EditBox minutesBox;
	private EditBox secondsBox;
	private Button confirmButton;
	private Button scopeButton;
	private Button modeButton;

	private static final int MAX_SUGGESTIONS = 7;
	private static final int SUGGESTION_HEIGHT = 12;

	/**
	 * Field contents live on the screen, not in the widgets. Minecraft calls init() again
	 * every time a screen is re-shown or the window is resized, throwing away every widget -
	 * so anything only held in an EditBox is lost when the item picker hands control back, or
	 * when the player resizes mid-edit.
	 */
	private String textValue = "";
	private String idValue = "";
	private String targetValue = "1";
	private String hoursValue = "0";
	private String minutesValue = "5";
	private String secondsValue = "0";
	private boolean prefilled;

	/**
	 * False while init() is still building widgets. Restoring a saved value fires the field's
	 * responder, which validates - and validation reads fields that later lines of init()
	 * have not created yet.
	 */
	private boolean layoutReady;

	private List<Identifier> suggestions = List.of();
	private int suggestionIndex;

	private EntryScope scope = EntryScope.WORLD;
	private CounterEntry.CountMode countMode = CounterEntry.CountMode.ACQUIRED;
	private boolean startImmediately = true;
	private String error;

	public EntryEditScreen(Screen parent, TodoEntry.Type type, EntryMatch.Kind matchKind,
			TodoEntry existing) {
		super(Component.literal(existing == null ? "New Entry" : "Edit Entry"));
		this.parent = parent;
		this.type = type;
		this.matchKind = matchKind == null ? EntryMatch.Kind.ITEM : matchKind;
		this.existing = existing;
	}

	@Override
	protected void init() {
		layoutReady = false;

		// Seed from the entry being edited exactly once; later inits restore what the player
		// has since typed instead.
		if (!prefilled) {
			prefill();
			prefilled = true;
		}

		int x = (this.width - FIELD_WIDTH) / 2;
		int y = 40;

		// Counters can describe themselves, so the field is optional there and the hint shows
		// what will be used instead.
		// Counters describe themselves from what they track, so there is no description to
		// write - offering one only invites a label that contradicts the entry.
		if (hasDescription()) {
			this.textBox = field(x, y, "Description");
			this.textBox.setMaxLength(TodoEntry.TEXT_MAX_LENGTH);
			this.textBox.setResponder(value -> {
				textValue = value;
				validate();
			});
			this.textBox.setValue(textValue);
			y += ROW + 6;
		}

		if (type == TodoEntry.Type.COUNTER) {
			boolean item = matchKind.isItem();
			// Items get a browse button; entities do not, because many have no spawn egg and
			// would be invisible in an icon grid. Typing them is still completed.
			int idWidth = item ? FIELD_WIDTH - 22 : FIELD_WIDTH;
			this.idBox = new EditBox(this.font, x, y, idWidth, FIELD_HEIGHT,
					Component.literal("Id"));
			this.idBox.setHint(Component.literal(item
					? "Item id, e.g. minecraft:oak_log"
					: "Entity id, e.g. minecraft:zombie"));
			this.idBox.setResponder(value -> {
				idValue = value;
				updateSuggestions();
				validate();
			});
			this.addRenderableWidget(this.idBox);
			this.idBox.setValue(idValue);

			if (item) {
				this.addRenderableWidget(Button.builder(Component.literal("..."),
								b -> this.minecraft.setScreenAndShow(
										new ItemPickerScreen(this, this::acceptId)))
						.bounds(x + FIELD_WIDTH - 20, y, 20, FIELD_HEIGHT)
						.build());
			}
			y += ROW;

			int half = (FIELD_WIDTH - 4) / 2;
			this.targetBox = new EditBox(this.font, x, y, half, FIELD_HEIGHT,
					Component.literal("Target"));
			this.targetBox.setResponder(value -> {
				targetValue = value;
				validate();
			});
			this.addRenderableWidget(this.targetBox);
			this.targetBox.setValue(targetValue);

			Button pick = Button.builder(
					Component.literal(item ? "Use held item" : "Use looked-at"),
					b -> pickFromWorld())
					.bounds(x + half + 4, y, half, FIELD_HEIGHT)
					.build();
			pick.active = this.minecraft != null && this.minecraft.player != null;
			this.addRenderableWidget(pick);
			y += ROW;

			if (item) {
				this.modeButton = Button.builder(Component.literal(modeLabel()),
						b -> cycleCountMode())
						.bounds(x, y, FIELD_WIDTH, FIELD_HEIGHT)
						.build();
				this.addRenderableWidget(this.modeButton);
				y += ROW;
			}
		} else if (type == TodoEntry.Type.TIMER) {
			int third = (FIELD_WIDTH - 8) / 3;
			this.hoursBox = timeField(x, y, third, hoursValue, v -> hoursValue = v);
			this.minutesBox = timeField(x + third + 4, y, third, minutesValue,
					v -> minutesValue = v);
			this.secondsBox = timeField(x + (third + 4) * 2, y, third, secondsValue,
					v -> secondsValue = v);
			y += ROW;

			Button startButton = Button.builder(Component.literal(startLabel()), b -> {
				startImmediately = !startImmediately;
				b.setMessage(Component.literal(startLabel()));
			}).bounds(x, y, FIELD_WIDTH, FIELD_HEIGHT).build();
			this.addRenderableWidget(startButton);
			y += ROW;
		}

		this.scopeButton = Button.builder(Component.literal(scopeLabel()), b -> {
			scope = scope == EntryScope.WORLD ? EntryScope.GLOBAL : EntryScope.WORLD;
			b.setMessage(Component.literal(scopeLabel()));
		}).bounds(x, y, FIELD_WIDTH, FIELD_HEIGHT).build();
		this.addRenderableWidget(this.scopeButton);
		y += ROW + 8;

		int half = (FIELD_WIDTH - 4) / 2;
		this.confirmButton = Button.builder(Component.literal("Save"), b -> confirm())
				.bounds(x, y, half, FIELD_HEIGHT)
				.build();
		this.addRenderableWidget(this.confirmButton);
		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
				.bounds(x + half + 4, y, half, FIELD_HEIGHT)
				.build());

		// Buttons that carry state in their label are built after prefill has restored it.
		if (this.modeButton != null) {
			this.modeButton.setMessage(Component.literal(modeLabel()));
		}
		this.scopeButton.setMessage(Component.literal(scopeLabel()));

		layoutReady = true;
		updateSuggestions();
		validate();
		setInitialFocus(this.textBox != null ? this.textBox : this.idBox);
	}

	/** Only text entries and timers carry a description the player writes. */
	private boolean hasDescription() {
		return type != TodoEntry.Type.COUNTER;
	}

	private EditBox field(int x, int y, String hint) {
		EditBox box = new EditBox(this.font, x, y, FIELD_WIDTH, FIELD_HEIGHT,
				Component.literal(hint));
		box.setHint(Component.literal(hint));
		box.setResponder(value -> validate());
		return this.addRenderableWidget(box);
	}

	private EditBox timeField(int x, int y, int width, String initial,
			java.util.function.Consumer<String> store) {
		EditBox box = new EditBox(this.font, x, y, width, FIELD_HEIGHT, Component.literal("0"));
		box.setMaxLength(2);
		// 26.2 dropped EditBox.setFilter; non-numeric text simply parses as 0 and validate()
		// reports the resulting duration as too short, so nothing invalid can be saved.
		box.setResponder(value -> {
			store.accept(value);
			validate();
		});
		this.addRenderableWidget(box);
		box.setValue(initial);
		return box;
	}

	/** Seeds the screen's field state from the entry being edited. Runs before any widget exists. */
	private void prefill() {
		if (existing == null) {
			this.countMode = CheckboxConfig.get().defaultCountMode;
			return;
		}

		if (hasDescription()) {
			this.textValue = existing.text();
		}
		this.scope = existing.scope();

		if (existing instanceof CounterEntry counter) {
			this.idValue = counter.match().display();
			this.targetValue = Integer.toString(counter.target());
			this.countMode = counter.countMode();
		} else if (existing instanceof TimerEntry timer) {
			long totalSeconds = timer.durationMillis() / 1000L;
			this.hoursValue = Long.toString(totalSeconds / 3600L);
			this.minutesValue = Long.toString((totalSeconds % 3600L) / 60L);
			this.secondsValue = Long.toString(totalSeconds % 60L);
		}
	}

	/** Fills the id field from what the player is holding or looking at. */
	private void pickFromWorld() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		if (matchKind.isItem()) {
			ItemStack held = this.minecraft.player.getMainHandItem();
			if (!held.isEmpty()) {
				this.idBox.setValue(BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
			}
		} else {
			Entity target = this.minecraft.crosshairPickEntity;
			if (target != null) {
				this.idBox.setValue(net.minecraft.world.entity.EntityType
						.getKey(target.getType()).toString());
			}
		}
		validate();
	}

	private void cycleCountMode() {
		countMode = switch (countMode) {
			case ACQUIRED -> CounterEntry.CountMode.INVENTORY;
			case INVENTORY -> CounterEntry.CountMode.PICKED_UP;
			case PICKED_UP -> CounterEntry.CountMode.ACQUIRED;
		};
		modeButton.setMessage(Component.literal(modeLabel()));
	}

	private String modeLabel() {
		String description = switch (countMode) {
			case ACQUIRED -> "Counting: acquired (never goes down)";
			case INVENTORY -> "Counting: held right now";
			case PICKED_UP -> "Counting: picked up off the ground";
		};
		return description;
	}

	private String scopeLabel() {
		return "Scope: " + (scope == EntryScope.GLOBAL ? "Global (every world)" : "This world");
	}

	private String startLabel() {
		return startImmediately ? "Start immediately: yes" : "Start immediately: no";
	}

	// --- Id completion ------------------------------------------------------------------

	/** Sets the id field from the picker or a suggestion, and closes the popup. */
	private void acceptId(String id) {
		// Written to the screen's own state as well, because the picker returns by re-showing
		// this screen, which rebuilds every widget.
		idValue = id;
		if (idBox != null) {
			idBox.setValue(id);
			idBox.setSuggestion("");
		}
		suggestions = List.of();
		validate();
	}

	private void updateSuggestions() {
		if (idBox == null) {
			return;
		}
		String query = idBox.getValue();
		suggestions = query.isBlank()
				? List.of()
				: RegistrySuggestions.match(matchKind, query, MAX_SUGGESTIONS);
		suggestionIndex = 0;

		// Ghost text completing the best match inline, the way the chat box does.
		String ghost = "";
		if (!suggestions.isEmpty()) {
			String best = suggestions.get(0).toString();
			String typed = query.toLowerCase(java.util.Locale.ROOT);
			if (best.startsWith(typed) && best.length() > typed.length()) {
				ghost = best.substring(typed.length());
			}
		}
		idBox.setSuggestion(ghost);
	}

	private boolean suggestionsOpen() {
		return !suggestions.isEmpty() && idBox != null && idBox.isFocused();
	}

	private int suggestionListY() {
		return idBox.getY() + FIELD_HEIGHT;
	}

	/** Recomputes {@link #error} and the confirm button's state. */
	private void validate() {
		if (!layoutReady) {
			return;
		}
		error = check();
		if (confirmButton != null) {
			confirmButton.active = error == null;
		}
	}

	private String check() {
		if (hasDescription() && textBox.getValue().isBlank()) {
			return "Give the entry a description";
		}
		if (!CheckboxClient.store().isOpen()) {
			return "Join a world first";
		}

		if (type == TodoEntry.Type.COUNTER) {
			if (idBox.getValue().isBlank()) {
				return matchKind.isItem() ? "Enter an item id" : "Enter an entity id";
			}
			EntryMatch match;
			try {
				match = new EntryMatch(matchKind, idBox.getValue());
			} catch (IllegalArgumentException e) {
				return "That id is not valid";
			}
			if (!match.isWellFormed()) {
				return "Ids look like minecraft:oak_log";
			}
			if (!CheckboxClient.trackers().resolver().isResolvable(match)) {
				return "Nothing in this game is called " + match.display();
			}
			// A typed id bypasses the filtered suggestions, so catch it here too rather than
			// letting the player save a counter that could never progress.
			Identifier matchId = Identifier.tryParse(match.id());
			if (matchKind.isItem()) {
				if (ItemFilter.isExcluded(matchId)) {
					return "That item cannot be collected";
				}
			} else if (!RegistrySuggestions.isKillable(matchId)) {
				return "That is not something you can kill";
			}
			int target = parseInt(targetBox.getValue());
			if (target < CounterEntry.TARGET_MIN || target > CounterEntry.TARGET_MAX) {
				return "Target must be between 1 and " + CounterEntry.TARGET_MAX;
			}
		} else if (type == TodoEntry.Type.TIMER) {
			long millis = durationMillis();
			if (millis < TimerEntry.DURATION_MIN_MS) {
				return "Timers must be at least one second";
			}
			if (millis > TimerEntry.DURATION_MAX_MS) {
				return "Timers can be at most 24 hours";
			}
		}
		return null;
	}

	private long durationMillis() {
		return (parseInt(hoursBox.getValue()) * 3600L
				+ parseInt(minutesBox.getValue()) * 60L
				+ parseInt(secondsBox.getValue())) * 1000L;
	}

	private static int parseInt(String value) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void confirm() {
		if (error != null) {
			return;
		}
		long now = System.currentTimeMillis();
		String text = hasDescription() ? textBox.getValue().strip() : "";

		if (existing != null) {
			applyEdits(text, now);
		} else {
			CheckboxClient.store().add(create(text, now));
		}
		CheckboxClient.store().flush(true);
		onClose();
	}

	private TodoEntry create(String text, long now) {
		return switch (type) {
			case TEXT -> TextEntry.create(text, scope, now);
			case COUNTER -> {
				CounterEntry counter = CounterEntry.create("Counter", scope, now,
						new EntryMatch(matchKind, idBox.getValue()),
						parseInt(targetBox.getValue()), countMode);
				// The label is generated for display, but the generated text is stored as
				// well: a client that cannot resolve the id later still shows something
				// better than a raw registry name.
				counter.setText(EntryLabels.generate(counter).getString());
				yield counter;
			}
			case TIMER -> {
				TimerEntry timer = TimerEntry.create(text, scope, now, durationMillis());
				if (startImmediately) {
					timer.start(now);
				}
				yield timer;
			}
		};
	}

	private void applyEdits(String text, long now) {
		if (!text.isBlank()) {
			existing.setText(text);
		}
		if (existing.scope() != scope) {
			// Moving scope means moving file, which only the store can do.
			CheckboxClient.store().moveToScope(existing.id(), scope);
		}
		switch (existing) {
			case CounterEntry counter -> {
				counter.setMatch(new EntryMatch(matchKind, idBox.getValue()));
				counter.setTarget(parseInt(targetBox.getValue()), now);
				counter.setCountMode(countMode);
				// Keep the stored fallback in step with the new target and item.
				counter.setText(EntryLabels.generate(counter).getString());
			}
			case TimerEntry timer -> timer.setDuration(durationMillis());
			default -> {
			}
		}
	}

	/** Arrow keys move through suggestions; tab or enter takes one; escape dismisses them. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (suggestionsOpen()) {
			switch (event.key()) {
				case GLFW.GLFW_KEY_DOWN -> {
					suggestionIndex = (suggestionIndex + 1) % suggestions.size();
					return true;
				}
				case GLFW.GLFW_KEY_UP -> {
					suggestionIndex = (suggestionIndex + suggestions.size() - 1)
							% suggestions.size();
					return true;
				}
				case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
					acceptId(suggestions.get(suggestionIndex).toString());
					return true;
				}
				case GLFW.GLFW_KEY_ESCAPE -> {
					// Dismiss the popup rather than the whole screen.
					suggestions = List.of();
					idBox.setSuggestion("");
					return true;
				}
				default -> {
				}
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (suggestionsOpen()) {
			int row = (int) ((event.y() - suggestionListY()) / SUGGESTION_HEIGHT);
			if (event.x() >= idBox.getX() && event.x() <= idBox.getX() + idBox.getWidth()
					&& row >= 0 && row < suggestions.size()) {
				acceptId(suggestions.get(row).toString());
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);

		if (suggestionsOpen()) {
			drawSuggestions(graphics);
		}

		// While the player is mid-word with suggestions showing, "nothing is called
		// minecraft:cr" is noise rather than news. Save stays disabled regardless.
		if (error != null && !suggestionsOpen()) {
			graphics.centeredText(this.font, Component.literal(error), this.width / 2,
					this.height - 52, 0xFFFF6666);
		}

		if (type == TodoEntry.Type.TIMER && hoursBox != null) {
			graphics.text(this.font, Component.literal("hh    mm    ss"),
					hoursBox.getX(), hoursBox.getY() - 10, 0xFFAAAAAA);
		}
	}

	/** Drawn after the widgets so it overlays whatever is beneath the id field. */
	private void drawSuggestions(GuiGraphicsExtractor graphics) {
		int x = idBox.getX();
		int y = suggestionListY();
		int width = idBox.getWidth();
		int height = suggestions.size() * SUGGESTION_HEIGHT;

		graphics.fill(x, y, x + width, y + height, 0xF0100010);
		for (int i = 0; i < suggestions.size(); i++) {
			Identifier id = suggestions.get(i);
			int rowY = y + i * SUGGESTION_HEIGHT;
			boolean selected = i == suggestionIndex;
			if (selected) {
				graphics.fill(x, rowY, x + width, rowY + SUGGESTION_HEIGHT, 0xFF3A3A5A);
			}
			ItemStack icon = RegistrySuggestions.iconFor(matchKind, id);
			if (!icon.isEmpty()) {
				TodoHudRenderer.drawIcon(graphics, icon, x + 2, rowY + 2);
			}
			graphics.text(this.font, id.toString(), x + 13, rowY + 2,
					selected ? 0xFFFFFFFF : 0xFFAAAAAA);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(this.parent);
	}

	/** Used by the manager to label its own buttons consistently. */
	public static String describe(EntryMatch.Kind kind) {
		return kind.name().toLowerCase(Locale.ROOT);
	}
}

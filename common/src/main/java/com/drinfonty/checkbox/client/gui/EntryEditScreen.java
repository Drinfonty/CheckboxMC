package com.drinfonty.checkbox.client.gui;

import com.drinfonty.checkbox.CheckboxClient;
import com.drinfonty.checkbox.config.CheckboxConfig;
import com.drinfonty.checkbox.hud.EntryLabels;
import com.drinfonty.checkbox.model.CounterEntry;
import com.drinfonty.checkbox.model.EntryMatch;
import com.drinfonty.checkbox.model.EntryScope;
import com.drinfonty.checkbox.model.TextEntry;
import com.drinfonty.checkbox.model.TimerEntry;
import com.drinfonty.checkbox.model.TodoEntry;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

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
		int x = (this.width - FIELD_WIDTH) / 2;
		int y = 40;

		// Counters can describe themselves, so the field is optional there and the hint shows
		// what will be used instead.
		this.textBox = field(x, y, type == TodoEntry.Type.COUNTER
				? "Description (optional)"
				: "Description");
		this.textBox.setMaxLength(TodoEntry.TEXT_MAX_LENGTH);
		y += ROW + 6;

		if (type == TodoEntry.Type.COUNTER) {
			boolean item = matchKind.isItem();
			this.idBox = field(x, y, item ? "Item id, e.g. minecraft:oak_log"
					: "Entity id, e.g. minecraft:zombie");
			y += ROW;

			int half = (FIELD_WIDTH - 4) / 2;
			this.targetBox = new EditBox(this.font, x, y, half, FIELD_HEIGHT,
					Component.literal("Target"));
			this.targetBox.setValue("1");
			this.targetBox.setResponder(value -> validate());
			this.addRenderableWidget(this.targetBox);

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
			this.hoursBox = timeField(x, y, third, "0");
			this.minutesBox = timeField(x + third + 4, y, third, "5");
			this.secondsBox = timeField(x + (third + 4) * 2, y, third, "0");
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

		prefill();
		validate();
		setInitialFocus(this.textBox);
	}

	private EditBox field(int x, int y, String hint) {
		EditBox box = new EditBox(this.font, x, y, FIELD_WIDTH, FIELD_HEIGHT,
				Component.literal(hint));
		box.setHint(Component.literal(hint));
		box.setResponder(value -> validate());
		return this.addRenderableWidget(box);
	}

	private EditBox timeField(int x, int y, int width, String initial) {
		EditBox box = new EditBox(this.font, x, y, width, FIELD_HEIGHT, Component.literal("0"));
		box.setValue(initial);
		box.setMaxLength(2);
		// 26.2 dropped EditBox.setFilter; non-numeric text simply parses as 0 and validate()
		// reports the resulting duration as too short, so nothing invalid can be saved.
		box.setResponder(value -> validate());
		return this.addRenderableWidget(box);
	}

	private void prefill() {
		if (existing == null) {
			this.countMode = CheckboxConfig.get().defaultCountMode;
			if (this.modeButton != null) {
				this.modeButton.setMessage(Component.literal(modeLabel()));
			}
			return;
		}

		// An auto-labelled entry shows an empty field, so saving unchanged keeps it automatic.
		if (!(existing instanceof CounterEntry counter && counter.autoLabel())) {
			this.textBox.setValue(existing.text());
		}
		this.scope = existing.scope();
		this.scopeButton.setMessage(Component.literal(scopeLabel()));

		if (existing instanceof CounterEntry counter) {
			this.idBox.setValue(counter.match().display());
			this.targetBox.setValue(Integer.toString(counter.target()));
			this.countMode = counter.countMode();
			if (this.modeButton != null) {
				this.modeButton.setMessage(Component.literal(modeLabel()));
			}
		} else if (existing instanceof TimerEntry timer) {
			long totalSeconds = timer.durationMillis() / 1000L;
			this.hoursBox.setValue(Long.toString(totalSeconds / 3600L));
			this.minutesBox.setValue(Long.toString((totalSeconds % 3600L) / 60L));
			this.secondsBox.setValue(Long.toString(totalSeconds % 60L));
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

	/** Recomputes {@link #error} and the confirm button's state. */
	private void validate() {
		error = check();
		if (confirmButton != null) {
			confirmButton.active = error == null;
		}
	}

	private String check() {
		if (textBox == null) {
			return "Loading";
		}
		if (textBox.getValue().isBlank() && type != TodoEntry.Type.COUNTER) {
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
		String text = textBox.getValue().strip();

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
				boolean auto = text.isBlank();
				CounterEntry counter = CounterEntry.create(
						auto ? "Counter" : text, scope, now,
						new EntryMatch(matchKind, idBox.getValue()),
						parseInt(targetBox.getValue()), countMode);
				if (auto) {
					counter.setAutoLabel(true);
					// Store the generated text too: a client that cannot resolve the id later
					// still shows something better than "Counter".
					counter.setText(EntryLabels.generate(counter).getString());
				}
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
				// Clearing the description hands the label back to the generator; typing one
				// takes it back.
				counter.setAutoLabel(text.isBlank());
				if (text.isBlank()) {
					counter.setText(EntryLabels.generate(counter).getString());
				}
			}
			case TimerEntry timer -> timer.setDuration(durationMillis());
			default -> {
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);

		if (error != null) {
			graphics.centeredText(this.font, Component.literal(error), this.width / 2,
					this.height - 52, 0xFFFF6666);
		}

		if (type == TodoEntry.Type.TIMER && hoursBox != null) {
			graphics.text(this.font, Component.literal("hh    mm    ss"),
					hoursBox.getX(), hoursBox.getY() - 10, 0xFFAAAAAA);
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

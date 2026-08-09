package com.drinfonty.checkbox.client.gui;

import com.drinfonty.checkbox.config.CheckboxConfig;
import com.drinfonty.checkbox.hud.HudAnchor;
import com.drinfonty.checkbox.hud.TodoHudRenderer;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * HUD appearance and behaviour settings (SPEC §4.3).
 *
 * <p>Controls live in a scrolling list rather than a fixed grid: there are enough of them that
 * a static layout would run off the bottom at high GUI scales on a short window.
 */
public class HudSettingsScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int WIDGET_HEIGHT = 20;
	private static final int GAP = 4;

	private final Screen parent;
	private SettingsList list;

	public HudSettingsScreen(Screen parent) {
		super(Component.literal("Checkbox HUD Settings"));
		this.parent = parent;
	}

	private static CheckboxConfig config() {
		return CheckboxConfig.get();
	}

	@Override
	protected void init() {
		CheckboxConfig config = config();
		int footerTop = this.height - 32;
		this.list = new SettingsList(this.minecraft, this.width, footerTop - 32, 32);
		this.addRenderableWidget(this.list);

		this.list.add(
				cycle(() -> "Anchor: " + pretty(config.anchor.name()),
						() -> config.anchor = next(config.anchor)),
				slider(scaleToFraction(config.scale, 0.5f, 2.0f),
						v -> config.scale = round1(0.5f + (float) v * 1.5f),
						() -> "Scale: " + round1(config.scale) + "x"));

		this.list.add(
				cycle(() -> "Width: " + pretty(config.widthMode.name()),
						() -> config.widthMode = config.widthMode == CheckboxConfig.WidthMode.AUTO
								? CheckboxConfig.WidthMode.FIXED
								: CheckboxConfig.WidthMode.AUTO),
				slider(scaleToFraction(config.fixedWidth, 60, 320),
						v -> config.fixedWidth = 60 + (int) Math.round(v * 260),
						() -> "Fixed width: " + config.fixedWidth + "px"));

		this.list.add(
				slider(scaleToFraction(config.maxVisibleEntries, 1, 20),
						v -> config.maxVisibleEntries = 1 + (int) Math.round(v * 19),
						() -> "Max rows: " + config.maxVisibleEntries),
				cycle(() -> "Background: " + pretty(config.backgroundStyle.name()),
						() -> config.backgroundStyle = next(config.backgroundStyle)));

		this.list.add(
				slider(config.backgroundOpacity / 100.0,
						v -> config.backgroundOpacity = (int) Math.round(v * 100),
						() -> "Opacity: " + config.backgroundOpacity + "%"),
				toggle("Text shadow", () -> config.textShadow,
						() -> config.textShadow = !config.textShadow));

		this.list.add(
				toggle("Show title", () -> config.showTitle,
						() -> config.showTitle = !config.showTitle),
				titleField(config));

		this.list.add(
				toggle("Progress bars", () -> config.showProgressBar,
						() -> config.showProgressBar = !config.showProgressBar),
				toggle("Show completed", () -> config.showCompleted,
						() -> config.showCompleted = !config.showCompleted));

		this.list.add(
				cycle(() -> "Completed: " + pretty(config.completedBehaviour.name()),
						() -> config.completedBehaviour = next(config.completedBehaviour)),
				slider(scaleToFraction(config.completedFadeSeconds, 1, 60),
						v -> config.completedFadeSeconds = 1 + (int) Math.round(v * 59),
						() -> "Fade after: " + config.completedFadeSeconds + "s"));

		this.list.add(
				toggle("Hide while a screen is open", () -> config.hideWhenScreenOpen,
						() -> config.hideWhenScreenOpen = !config.hideWhenScreenOpen),
				toggle("Hide with debug screen", () -> config.hideWithDebugScreen,
						() -> config.hideWithDebugScreen = !config.hideWithDebugScreen));

		this.list.add(
				toggle("Completion sounds", () -> config.playSounds,
						() -> config.playSounds = !config.playSounds),
				toggle("Completion toasts", () -> config.showToasts,
						() -> config.showToasts = !config.showToasts));

		this.list.add(
				toggle("Pause timers on quit", () -> config.pauseTimersOnQuit,
						() -> config.pauseTimersOnQuit = !config.pauseTimersOnQuit),
				Button.builder(Component.literal("Move HUD..."),
								b -> this.minecraft.setScreenAndShow(new HudPositionScreen(this)))
						.bounds(0, 0, 100, WIDGET_HEIGHT)
						.build());

		int half = 150;
		this.addRenderableWidget(Button.builder(Component.literal("Reset Defaults"), b -> {
			config.resetToDefaults();
			config.save();
			TodoHudRenderer.get().invalidate();
			this.rebuildWidgets();
		}).bounds(this.width / 2 - half - GAP, footerTop, half, WIDGET_HEIGHT).build());

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(this.width / 2 + GAP, footerTop, half, WIDGET_HEIGHT)
				.build());
	}

	private EditBox titleField(CheckboxConfig config) {
		EditBox box = new EditBox(this.font, 0, 0, 100, WIDGET_HEIGHT,
				Component.literal("Title text"));
		box.setMaxLength(32);
		box.setValue(config.titleText);
		box.setResponder(value -> {
			config.titleText = value.isBlank() ? "Checkbox" : value;
			TodoHudRenderer.get().invalidate();
		});
		return box;
	}

	private Button toggle(String label, BooleanSupplier state, Runnable flip) {
		return cycle(() -> label + ": " + (state.getAsBoolean() ? "on" : "off"), flip);
	}

	/** A button that mutates config and relabels itself from the new value. */
	private Button cycle(Supplier<String> label, Runnable action) {
		Button[] holder = new Button[1];
		holder[0] = Button.builder(Component.literal(label.get()), b -> {
			action.run();
			b.setMessage(Component.literal(label.get()));
			TodoHudRenderer.get().invalidate();
		}).bounds(0, 0, 100, WIDGET_HEIGHT).build();
		return holder[0];
	}

	private AbstractSliderButton slider(double initial, DoubleConsumer apply,
			Supplier<String> label) {
		return new AbstractSliderButton(0, 0, 100, WIDGET_HEIGHT,
				Component.literal(label.get()), initial) {
			@Override
			protected void updateMessage() {
				setMessage(Component.literal(label.get()));
			}

			@Override
			protected void applyValue() {
				apply.accept(this.value);
				TodoHudRenderer.get().invalidate();
			}
		};
	}

	private static double scaleToFraction(double value, double min, double max) {
		return Math.clamp((value - min) / (max - min), 0.0, 1.0);
	}

	private static float round1(float value) {
		return Math.round(value * 10f) / 10f;
	}

	private static <E extends Enum<E>> E next(E current) {
		E[] values = current.getDeclaringClass().getEnumConstants();
		return values[(current.ordinal() + 1) % values.length];
	}

	private static String pretty(String enumName) {
		String lower = enumName.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		config().save();
		TodoHudRenderer.get().invalidate();
		this.minecraft.setScreenAndShow(this.parent);
	}

	class SettingsList extends ContainerObjectSelectionList<SettingsList.Row> {
		SettingsList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
		}

		void add(AbstractWidget left, AbstractWidget right) {
			this.addEntry(new Row(left, right));
		}

		@Override
		public int getRowWidth() {
			return 310;
		}

		class Row extends ContainerObjectSelectionList.Entry<Row> {
			private final List<AbstractWidget> widgets;

			Row(AbstractWidget left, AbstractWidget right) {
				this.widgets = right == null ? List.of(left) : List.of(left, right);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return widgets;
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return widgets;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
					boolean hovered, float partialTick) {
				// Widgets are positioned here rather than at construction: the list owns the
				// row geometry and it changes with the window.
				int x = getContentX();
				int y = getContentY();
				int total = getContentWidth();
				int cell = widgets.size() == 1 ? total : (total - GAP) / 2;

				for (int i = 0; i < widgets.size(); i++) {
					AbstractWidget widget = widgets.get(i);
					widget.setX(x + i * (cell + GAP));
					widget.setY(y);
					widget.setWidth(cell);
					widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
				}
			}
		}
	}
}

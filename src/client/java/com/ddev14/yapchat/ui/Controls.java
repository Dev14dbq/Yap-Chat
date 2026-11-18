package com.ddev14.yapchat.ui;

import com.ddev14.yapchat.render.ItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.gui.Font;
import com.ddev14.yapchat.compat.Gfx;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** The controls used by the settings screen. All custom-drawn in the {@link Theme} style. */
public final class Controls {
	private Controls() {}

	// GLFW constants (avoid a direct LWJGL dependency in UI code)
	static final int KEY_BACKSPACE = 259, KEY_ENTER = 257, KEY_KP_ENTER = 335, KEY_ESCAPE = 256, KEY_V = 86;
	static final int MOD_CONTROL = 0x2, MOD_SUPER = 0x8;

	/** Item for an id like {@code minecraft:diamond}; {@code fallback} if the id is unknown. */
	/** The vanilla button click. */
	public static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	/** Item by id, or the fallback; see {@link ItemIcons} for drawing it safely. */
	public static Item itemOf(String id, Item fallback) {
		return ItemIcons.item(id, fallback);
	}

	// ── Toggle ───────────────────────────────────────────────────────────────

	/** Pill switch. */
	public static final class Toggle extends UiWidget {
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;
		private float anim;

		public Toggle(BooleanSupplier get, Consumer<Boolean> set) {
			super(24, 12);
			this.get = get;
			this.set = set;
			this.anim = get.getAsBoolean() ? 1f : 0f;
		}

		@Override
		public void render(Gfx g, Font font, int mx, int my) {
			float target = get.getAsBoolean() ? 1f : 0f;
			anim += (target - anim) * 0.35f;
			if (Math.abs(target - anim) < 0.01f) anim = target;
			boolean hover = isHovered(mx, my) && enabled;
			int track = Theme.lerpColor(hover ? Theme.CONTROL_HOVER : Theme.CONTROL_EDGE,
					hover ? Theme.ACCENT_HOVER : Theme.ACCENT, anim);
			if (!enabled) track = Theme.withAlpha(track, 0.4f);
			Theme.box(g, x, y, w, h, track);
			int knobX = x + 2 + Math.round(anim * (w - 12));
			Theme.box(g, knobX, y + 2, 8, 8, enabled ? Theme.TEXT : Theme.TEXT_FAINT);
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!enabled || !isHovered(mx, my)) return false;
			click();
			set.accept(!get.getAsBoolean());
			return true;
		}
	}

	// ── Slider ───────────────────────────────────────────────────────────────

	/** Integer slider with the value printed to its right. */
	public static final class Slider extends UiWidget {
		private final int min, max, step;
		/** Space reserved for the value text right of the track. */
		private int valueW;
		private final IntSupplier get;
		private final IntConsumer set;
		private final IntFunction<String> format;
		private boolean dragging;

		public Slider(int min, int max, int step, IntSupplier get, IntConsumer set, IntFunction<String> format) {
			super(120, 12);
			this.min = min;
			this.max = max;
			this.step = Math.max(1, step);
			this.get = get;
			this.set = set;
			this.format = format;
			Font font = Minecraft.getInstance().font;
			valueW = Math.max(font.width(format.apply(min)), font.width(format.apply(max))) + 6;
			valueW = Math.max(valueW, font.width(format.apply(get.getAsInt())) + 6);
			valueW = Math.max(valueW, 24);
			this.w = 86 + valueW;
		}

		/** Makes room for value texts wider than the ones measured at construction. */
		public Slider reserveValue(int px) {
			valueW = Math.max(valueW, px);
			w = 86 + valueW;
			return this;
		}

		private int trackX() { return x; }
		private int trackW() { return w - valueW; }

		@Override
		public void render(Gfx g, Font font, int mx, int my) {
			int value = get.getAsInt();
			float t = (value - min) / (float) (max - min);
			int tx = trackX(), tw = trackW();
			int knob = tx + Math.round(t * (tw - 5));
			boolean hover = isHovered(mx, my) || dragging;
			Theme.box(g, tx, y + 5, tw, 3, Theme.CONTROL_EDGE);
			Theme.box(g, tx, y + 5, Math.max(3, knob - tx + 2), 3, hover ? Theme.ACCENT_HOVER : Theme.ACCENT);
			Theme.box(g, knob, y, 5, 12, hover ? Theme.TEXT : 0xFFD0D0D0);
			String text = format.apply(value);
			g.text(font, text, x + w - font.width(text), y + 2, Theme.TEXT_DIM, true);
		}

		private void update(double mx) {
			float t = (float) ((mx - trackX() - 2) / (trackW() - 5));
			t = Math.max(0f, Math.min(1f, t));
			int raw = Math.round(min + t * (max - min));
			int v = min + Math.round((raw - min) / (float) step) * step;
			v = Math.max(min, Math.min(max, v));
			if (v != get.getAsInt()) set.accept(v);
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!isHovered(mx, my) || mx >= trackX() + trackW()) return false;
			dragging = true;
			update(mx);
			return true;
		}

		@Override
		public boolean mouseDragged(double mx, double my) {
			if (!dragging) return false;
			update(mx);
			return true;
		}

		@Override
		public void mouseReleased() {
			if (dragging) click(); // like vanilla sliders: the click sounds on release
			dragging = false;
		}

		@Override
		public boolean mouseScrolled(double mx, double my, double amount) {
			if (!isHovered(mx, my)) return false;
			int v = get.getAsInt() + (amount > 0 ? step : -step);
			set.accept(Math.max(min, Math.min(max, v)));
			return true;
		}
	}

	// ── Choice ───────────────────────────────────────────────────────────────

	/** "‹ value ›" selector. Left half goes back, right half forward; the wheel works too. */
	public static final class Choice<T> extends UiWidget {
		private final List<T> values;
		private final Supplier<T> get;
		private final Consumer<T> set;
		private final Function<T, Component> label;

		public Choice(List<T> values, Supplier<T> get, Consumer<T> set, Function<T, Component> label) {
			super(120, 16);
			this.values = values;
			this.get = get;
			this.set = set;
			this.label = label;
		}

		@Override
		public void render(Gfx g, Font font, int mx, int my) {
			boolean hover = isHovered(mx, my);
			Theme.framed(g, x, y, w, h, hover ? Theme.CONTROL_HOVER : Theme.CONTROL, Theme.CONTROL_EDGE);
			boolean left = hover && mx < x + w / 2;
			g.text(font, "‹", x + 5, y + 4, hover && left ? Theme.ACCENT_HOVER : Theme.TEXT_DIM, false);
			g.text(font, "›", x + w - 9, y + 4, hover && !left ? Theme.ACCENT_HOVER : Theme.TEXT_DIM, false);
			Component text = label.apply(get.get());
			g.text(font, text, x + (w - font.width(text)) / 2, y + 4, Theme.TEXT, false);
		}

		private void move(int delta) {
			int i = Math.max(0, values.indexOf(get.get()));
			set.accept(values.get(Math.floorMod(i + delta, values.size())));
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!isHovered(mx, my)) return false;
			click();
			move(mx < x + w / 2 ? -1 : 1);
			return true;
		}

		@Override
		public boolean mouseScrolled(double mx, double my, double amount) {
			if (!isHovered(mx, my)) return false;
			move(amount > 0 ? -1 : 1);
			return true;
		}
	}

	// ── Buttons ──────────────────────────────────────────────────────────────

	public enum Kind { PRIMARY, SECONDARY, DANGER }

	public static final class TextButton extends UiWidget {
		private final Supplier<Component> label;
		private final Kind kind;
		private final Runnable action;

		public TextButton(Supplier<Component> label, Kind kind, Runnable action) {
			super(Minecraft.getInstance().font.width(label.get()) + 16, 16);
			this.label = label;
			this.kind = kind;
			this.action = action;
		}

		public TextButton width(int width) {
			this.w = width;
			return this;
		}

		@Override
		public void render(Gfx g, Font font, int mx, int my) {
			boolean hover = isHovered(mx, my) && enabled;
			int textColor;
			switch (kind) {
				case PRIMARY -> {
					Theme.box(g, x, y, w, h, hover ? Theme.ACCENT_HOVER : Theme.ACCENT);
					Theme.box(g, x + 1, y + h - 1, w - 2, 1, Theme.ACCENT_DARK);
					textColor = 0xFFFFFFFF;
				}
				case DANGER -> {
					Theme.framed(g, x, y, w, h, hover ? 0xFF3A2020 : Theme.CONTROL, hover ? Theme.DANGER : Theme.DANGER_DARK);
					textColor = hover ? Theme.DANGER_HOVER : Theme.DANGER;
				}
				default -> {
					Theme.framed(g, x, y, w, h, hover ? Theme.CONTROL_HOVER : Theme.CONTROL, Theme.CONTROL_EDGE);
					textColor = Theme.TEXT;
				}
			}
			if (!enabled) textColor = Theme.TEXT_FAINT;
			Component text = label.get();
			g.text(font, text, x + (w - font.width(text)) / 2, y + 4, textColor, false);
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!enabled || !isHovered(mx, my)) return false;
			click();
			action.run();
			return true;
		}
	}

	/** Small square button with a single glyph (e.g. reset ↺). */
	public static final class IconButton extends UiWidget {
		private final String glyph;
		private final Runnable action;

		public IconButton(String glyph, Component tooltip, Runnable action) {
			super(14, 14);
			this.glyph = glyph;
			this.tooltip = tooltip;
			this.action = action;
		}

		@Override
		public void render(Gfx g, Font font, int mx, int my) {
			boolean hover = isHovered(mx, my) && enabled;
			if (hover) Theme.box(g, x, y, w, h, Theme.CONTROL_HOVER);
			int color = !enabled ? Theme.TEXT_FAINT : hover ? Theme.ACCENT_HOVER : Theme.TEXT_DIM;
			if ("reset".equals(glyph)) {
				drawBitmap(g, RESET_ICON, x + (w - 7) / 2, y + (h - 7) / 2, color);
			} else {
				g.text(font, glyph, x + (w - font.width(glyph)) / 2 + 1, y + 3, color, false);
			}
		}

		/** Circular arrow, 7x7. */
		private static final String[] RESET_ICON = {
				"..###.#",
				".#...##",
				"#...###",
				"#......",
				"#.....#",
				".#...#.",
				"..###..",
		};

		private static void drawBitmap(Gfx g, String[] rows, int x, int y, int color) {
			for (int r = 0; r < rows.length; r++)
				for (int c = 0; c < rows[r].length(); c++)
					if (rows[r].charAt(c) == '#') g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!enabled || !isHovered(mx, my)) return false;
			click();
			action.run();
			return true;
		}
	}

	// ── Text input ───────────────────────────────────────────────────────────

	public static final class TextInput extends UiWidget {
		private String value;
		private final int maxLength;
		private final Predicate<String> filter;
		private final Consumer<String> onChange;
		private final Consumer<String> onEnter;
		private final Component placeholder;
		public boolean focused;

		public TextInput(int w, String initial, int maxLength, Predicate<String> filter,
				Consumer<String> onChange, Consumer<String> onEnter, Component placeholder) {
			super(w, 16);
			this.value = initial == null ? "" : initial;
			this.maxLength = maxLength;
			this.filter = filter;
			this.onChange = onChange;
			this.onEnter = onEnter;
			this.placeholder = placeholder;
		}

		public String value() { return value; }

		public void setValue(String v) { value = v == null ? "" : v; }

		@Override
		public void render(Gfx g, Font font, int mx, int my) {
			boolean hover = isHovered(mx, my);
			Theme.framed(g, x, y, w, h, Theme.CONTROL, focused ? Theme.ACCENT : hover ? Theme.TEXT_FAINT : Theme.CONTROL_EDGE);
			int inner = w - 8;
			if (value.isEmpty() && !focused && placeholder != null) {
				g.text(font, font.plainSubstrByWidth(placeholder.getString(), inner), x + 4, y + 4, Theme.TEXT_FAINT, false);
			} else {
				String shown = value;
				while (font.width(shown) > inner - 3 && !shown.isEmpty()) shown = shown.substring(1);
				g.text(font, shown, x + 4, y + 4, Theme.TEXT, false);
				if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
					int cx = x + 4 + font.width(shown);
					g.fill(cx, y + 3, cx + 1, y + 12, Theme.ACCENT_HOVER);
				}
			}
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			focused = isHovered(mx, my);
			return focused;
		}

		@Override
		public void blur() { focused = false; }

		private void change(String next) {
			if (next.length() > maxLength || !filter.test(next)) return;
			value = next;
			if (onChange != null) onChange.accept(value);
		}

		@Override
		public boolean keyPressed(int key, int modifiers) {
			if (!focused) return false;
			switch (key) {
				case KEY_BACKSPACE -> {
					if (!value.isEmpty()) change(value.substring(0, value.length() - 1));
					return true;
				}
				case KEY_ENTER, KEY_KP_ENTER -> {
					if (onEnter != null) onEnter.accept(value);
					return true;
				}
				case KEY_ESCAPE -> {
					focused = false;
					return true;
				}
				case KEY_V -> {
					if ((modifiers & (MOD_CONTROL | MOD_SUPER)) != 0) {
						String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
						if (clip != null) change(value + clip.strip());
						return true;
					}
					return true;
				}
				default -> {
					return true; // swallow keys while typing (e.g. inventory key closing the screen)
				}
			}
		}

		@Override
		public boolean charTyped(String text) {
			if (!focused) return false;
			change(value + text);
			return true;
		}
	}

	// ── Position picker ──────────────────────────────────────────────────────

	/** A tiny screen with the six places the chat can sit. */
	public static final class PositionPicker extends UiWidget {
		private final IntSupplier get;
		private final IntConsumer set;

		public PositionPicker(IntSupplier get, IntConsumer set) {
			super(84, 48);
			this.get = get;
			this.set = set;
		}

		/** {x, y} of the chat bar for each position id (0..5). */
		private int[] spot(int pos) {
			int bw = 24;
			int left = x + 5, center = x + (w - bw) / 2, right = x + w - 5 - bw;
			int top = y + 6, bottom = y + h - 15;
			return switch (pos) {
				case 1 -> new int[]{center, top};
				case 2 -> new int[]{left, top};
				case 3 -> new int[]{left, bottom};
				case 4 -> new int[]{right, top};
				case 5 -> new int[]{right, bottom};
				default -> new int[]{center, bottom};
			};
		}

		private int hoveredSpot(double mx, double my) {
			if (!isHovered(mx, my)) return -1;
			int best = -1;
			double bestD = Double.MAX_VALUE;
			for (int p = 0; p < 6; p++) {
				int[] s = spot(p);
				double dx = mx - (s[0] + 12), dy = my - (s[1] + 3);
				double d = dx * dx + dy * dy * 2;
				if (d < bestD) { bestD = d; best = p; }
			}
			return best;
		}

		@Override
		public void render(Gfx g, Font font, int mx, int my) {
			Theme.framed(g, x, y, w, h, 0xFF0C0C0C, isHovered(mx, my) ? Theme.TEXT_FAINT : Theme.CONTROL_EDGE);
			// hotbar
			Theme.box(g, x + (w - 30) / 2, y + h - 6, 30, 3, 0xFF3A3A3A);
			int selected = get.getAsInt();
			int hover = hoveredSpot(mx, my);
			for (int p = 0; p < 6; p++) {
				int[] s = spot(p);
				int color = p == selected ? Theme.ACCENT : p == hover ? Theme.ACCENT_DARK : 0xFF3A3A3A;
				Theme.box(g, s[0], s[1], 24, 3, color);
				Theme.box(g, s[0] + (p == 2 || p == 3 ? 0 : p == 4 || p == 5 ? 8 : 4), s[1] + 4, 16, 3, color);
			}
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			int p = hoveredSpot(mx, my);
			if (p < 0) return false;
			if (p != get.getAsInt()) click();
			set.accept(p);
			return true;
		}
	}

	// ── Color swatch & item slot (open popovers) ─────────────────────────────

	public static final class ColorSwatch extends UiWidget {
		private final Supplier<String> hex;
		private final Consumer<ColorSwatch> open;

		public ColorSwatch(Supplier<String> hex, Consumer<ColorSwatch> open) {
			super(16, 16);
			this.hex = hex;
			this.open = open;
		}

		public static int parse(String hex, int fallback) {
			if (hex == null) return fallback;
			String h = hex.trim();
			if (h.startsWith("#")) h = h.substring(1);
			if (h.length() != 6) return fallback;
			try {
				return 0xFF000000 | Integer.parseInt(h, 16);
			} catch (NumberFormatException e) {
				return fallback;
			}
		}

		@Override
		public void render(Gfx g, Font font, int mx, int my) {
			boolean hover = isHovered(mx, my);
			Theme.box(g, x, y, w, h, hover ? Theme.ACCENT_HOVER : Theme.CONTROL_EDGE);
			g.fill(x + 2, y + 2, x + w - 2, y + h - 2, parse(hex.get(), 0xFF000000));
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!isHovered(mx, my)) return false;
			click();
			open.accept(this);
			return true;
		}
	}

	public static final class ItemSlot extends UiWidget {
		private final Supplier<String> id;
		private final Item fallback;
		private final Consumer<ItemSlot> open;

		public ItemSlot(Supplier<String> id, Item fallback, Consumer<ItemSlot> open) {
			super(18, 18);
			this.id = id;
			this.fallback = fallback;
			this.open = open;
		}

		@Override
		public void render(Gfx g, Font font, int mx, int my) {
			boolean hover = isHovered(mx, my) && enabled;
			Theme.framed(g, x, y, w, h, hover ? Theme.CONTROL_HOVER : Theme.CONTROL, hover ? Theme.ACCENT_HOVER : Theme.CONTROL_EDGE);
			if (enabled) ItemIcons.draw(g, itemOf(id.get(), fallback), x + 1, y + 1);
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!enabled || !isHovered(mx, my)) return false;
			click();
			open.accept(this);
			return true;
		}
	}
}

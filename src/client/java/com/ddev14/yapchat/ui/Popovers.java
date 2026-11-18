package com.ddev14.yapchat.ui;

import com.ddev14.yapchat.render.ItemIcons;
import net.minecraft.client.gui.Font;
import com.ddev14.yapchat.compat.Gfx;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Small floating panels opened from a control (color and icon pickers). */
public final class Popovers {
	private Popovers() {}

	/** Base: a framed panel that closes when clicked outside or on Escape. */
	public abstract static class Popover extends UiWidget {
		public boolean closed;
		protected final List<UiWidget> children = new ArrayList<>();

		protected Popover(int w, int h) {
			super(w, h);
		}

		/** Places the panel next to an anchor, kept inside the screen. */
		public Popover anchor(UiWidget a, int screenW, int screenH) {
			int px = a.x + a.w + 6;
			if (px + w > screenW - 4) px = a.x - w - 6;
			int py = a.y - 4;
			if (py + h > screenH - 4) py = screenH - 4 - h;
			px = Math.max(4, px);
			py = Math.max(4, py);
			at(px, py);
			layout();
			return this;
		}

		protected abstract void layout();

		protected void renderPanel(Gfx g) {
			Theme.box(g, x + 2, y + 3, w, h, Theme.SHADOW);
			Theme.framed(g, x, y, w, h, 0xF0101010, Theme.PANEL_EDGE);
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!isHovered(mx, my)) {
				closed = true;
				return true; // eat the click so nothing behind reacts
			}
			for (UiWidget c : children) c.blur();
			for (UiWidget c : children) if (c.mouseClicked(mx, my, button)) return true;
			return true;
		}

		@Override
		public boolean mouseScrolled(double mx, double my, double amount) {
			for (UiWidget c : children) if (c.mouseScrolled(mx, my, amount)) return true;
			return true;
		}

		@Override
		public boolean keyPressed(int key, int modifiers) {
			for (UiWidget c : children) if (c.keyPressed(key, modifiers)) return true;
			if (key == Controls.KEY_ESCAPE) {
				closed = true;
				return true;
			}
			return false;
		}

		@Override
		public boolean charTyped(String text) {
			for (UiWidget c : children) if (c.charTyped(text)) return true;
			return false;
		}
	}

	// ── Color ────────────────────────────────────────────────────────────────

	/** Background presets: dark tints that keep white chat text readable. */
	private static final String[] PRESETS = {
			"#000000", "#141414", "#262626", "#3A3A3A", "#0E1A2E", "#10203A",
			"#2A1416", "#1A0000", "#3A0E0E", "#4A1A10", "#2A1C10", "#3A2A0A",
			"#16260F", "#001A04", "#0F2A2E", "#0A2A22", "#1A0028", "#2A1030",
	};

	/** Bright colors for the menu accent. */
	public static final String[] ACCENT_PRESETS = {
			"#E0564B", "#F0803C", "#E8B83A", "#8BC34A", "#4CAF6E", "#3DBFA8",
			"#3FA9E0", "#4A7FE8", "#7A5CE8", "#B35CE0", "#E05C9E", "#C8C8C8",
	};

	public static final class ColorPopover extends Popover {
		private final Supplier<String> get;
		private final Consumer<String> set;
		private final Controls.TextInput hex;
		private final Component title;

		private final String[] presets;

		public ColorPopover(Component title, Supplier<String> get, Consumer<String> set) {
			this(title, get, set, PRESETS);
		}

		public ColorPopover(Component title, Supplier<String> get, Consumer<String> set, String[] presets) {
			super(6 * 16 + 5 * 3 + 16, 0);
			this.presets = presets;
			this.title = title;
			this.get = get;
			this.set = set;
			this.h = 16 + 3 * 16 + 2 * 3 + 8 + 16 + 8;
			this.hex = new Controls.TextInput(w - 16, get.get(), 7,
					s -> s.matches("#?[0-9a-fA-F]{0,6}"),
					s -> { if (s.matches("#?[0-9a-fA-F]{6}")) set.accept(normalize(s)); },
					s -> { if (s.matches("#?[0-9a-fA-F]{6}")) { set.accept(normalize(s)); closed = true; } },
					com.ddev14.yapchat.compat.Texts.literal("#RRGGBB"));
			children.add(hex);
		}

		private static String normalize(String s) {
			return ("#" + s.replace("#", "")).toUpperCase(Locale.ROOT);
		}

		private int cellX(int i) { return x + 8 + (i % 6) * 19; }
		private int cellY(int i) { return y + 16 + (i / 6) * 19; }

		@Override
		protected void layout() {
			hex.at(x + 8, y + h - 24);
		}

		@Override
		public void render(Gfx g, Font font, int mx, int my) {
			renderPanel(g);
			g.text(font, title, x + 8, y + 5, Theme.TEXT_DIM, false);
			String current = get.get();
			for (int i = 0; i < presets.length; i++) {
				int cx = cellX(i), cy = cellY(i);
				boolean hover = mx >= cx && my >= cy && mx < cx + 16 && my < cy + 16;
				boolean sel = presets[i].equalsIgnoreCase(current);
				Theme.box(g, cx, cy, 16, 16, sel ? Theme.ACCENT : hover ? Theme.TEXT : Theme.CONTROL_EDGE);
				g.fill(cx + 2, cy + 2, cx + 14, cy + 14, Controls.ColorSwatch.parse(presets[i], 0xFF000000));
			}
			hex.render(g, font, mx, my);
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (isHovered(mx, my)) {
				for (int i = 0; i < presets.length; i++) {
					int cx = cellX(i), cy = cellY(i);
					if (mx >= cx && my >= cy && mx < cx + 16 && my < cy + 16) {
						Controls.click();
						set.accept(presets[i]);
						hex.setValue(presets[i]);
						return true;
					}
				}
			}
			return super.mouseClicked(mx, my, button);
		}
	}

	// ── Icon (item) ──────────────────────────────────────────────────────────

	/** Items that make sense as message icons; shown before the user types a search. */
	private static final String[] SUGGESTED = {
			"paper", "book", "writable_book", "name_tag", "bell", "clock", "compass", "map",
			"emerald", "diamond", "netherite_ingot", "gold_ingot", "nether_star", "experience_bottle", "totem_of_undying", "firework_rocket",
			"barrier", "tnt", "redstone_torch", "lava_bucket", "red_bed", "white_bed", "lantern", "torch",
			"command_block", "repeating_command_block", "structure_block", "observer", "oak_door", "iron_door", "spyglass", "ender_eye",
			"stick", "feather", "amethyst_shard", "echo_shard", "heart_of_the_sea", "golden_apple", "cake", "player_head",
	};

	public static final class ItemPopover extends Popover {
		private static final int COLS = 8, ROWS = 5, CELL = 19;
		private final Supplier<String> get;
		private final Consumer<String> set;
		private final Controls.TextInput search;
		private final Component title;
		private List<String> results = new ArrayList<>();
		private int page;

		public ItemPopover(Component title, Supplier<String> get, Consumer<String> set) {
			super(COLS * CELL + 13, 16 + 20 + ROWS * CELL + 18);
			this.title = title;
			this.get = get;
			this.set = set;
			this.search = new Controls.TextInput(w - 16, "", 40, s -> true, s -> refresh(),
					s -> { if (!results.isEmpty()) pick(results.get(0)); },
					com.ddev14.yapchat.compat.Texts.translatable("yapchat.ui.item.search"));
			children.add(search);
			refresh();
		}

		private void refresh() {
			page = 0;
			String q = search.value().trim().toLowerCase(Locale.ROOT);
			List<String> out = new ArrayList<>();
			if (q.isEmpty()) {
				for (String s : SUGGESTED) out.add("minecraft:" + s);
			} else {
				for (ResourceLocation id : com.ddev14.yapchat.compat.Compat.itemIds()) {
					String full = id.toString();
					if (full.contains(q) && com.ddev14.yapchat.compat.Compat.item(id) != Items.AIR) out.add(full);
				}
				out.sort((a, b) -> {
					boolean pa = a.substring(a.indexOf(':') + 1).startsWith(q), pb = b.substring(b.indexOf(':') + 1).startsWith(q);
					if (pa != pb) return pa ? -1 : 1;
					return a.compareTo(b);
				});
			}
			results = out;
		}

		private void pick(String id) {
			Controls.click();
			set.accept(id);
			closed = true;
		}

		private int pages() { return Math.max(1, (results.size() + COLS * ROWS - 1) / (COLS * ROWS)); }

		private int cellX(int i) { return x + 7 + (i % COLS) * CELL; }
		private int cellY(int i) { return y + 36 + (i / COLS) * CELL; }

		@Override
		protected void layout() {
			search.at(x + 8, y + 16);
		}

		@Override
		public void render(Gfx g, Font font, int mx, int my) {
			renderPanel(g);
			g.text(font, title, x + 8, y + 5, Theme.TEXT_DIM, false);
			search.render(g, font, mx, my);
			String current = get.get();
			String hoveredId = null;
			int start = page * COLS * ROWS;
			for (int i = 0; i < COLS * ROWS && start + i < results.size(); i++) {
				String id = results.get(start + i);
				int cx = cellX(i), cy = cellY(i);
				boolean hover = mx >= cx && my >= cy && mx < cx + 18 && my < cy + 18;
				boolean sel = id.equals(current);
				Theme.box(g, cx, cy, 18, 18, sel ? Theme.ACCENT : hover ? Theme.CONTROL_HOVER : Theme.CONTROL);
				ItemIcons.draw(g, Controls.itemOf(id, Items.BARRIER), cx + 1, cy + 1);
				if (hover) hoveredId = id;
			}
			if (results.isEmpty()) {
				g.text(font, com.ddev14.yapchat.compat.Texts.translatable("yapchat.ui.item.none"), x + 8, y + 40, Theme.TEXT_FAINT, false);
			}
			String footer = pages() > 1 ? "‹ " + (page + 1) + "/" + pages() + " ›" : "";
			if (!footer.isEmpty()) g.text(font, footer, x + w - 8 - font.width(footer), y + h - 13, Theme.TEXT_DIM, false);
			if (hoveredId != null) {
				g.setTooltipForNextFrame(font, ItemIcons.name(Controls.itemOf(hoveredId, Items.BARRIER)), mx, my);
			}
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (isHovered(mx, my)) {
				int start = page * COLS * ROWS;
				for (int i = 0; i < COLS * ROWS && start + i < results.size(); i++) {
					int cx = cellX(i), cy = cellY(i);
					if (mx >= cx && my >= cy && mx < cx + 18 && my < cy + 18) {
						pick(results.get(start + i));
						return true;
					}
				}
				if (my >= y + h - 15 && pages() > 1) {
					page = Math.floorMod(page + (mx < x + w - 24 ? -1 : 1), pages());
					return true;
				}
			}
			return super.mouseClicked(mx, my, button);
		}

		@Override
		public boolean mouseScrolled(double mx, double my, double amount) {
			if (pages() > 1 && isHovered(mx, my)) {
				page = Math.floorMod(page + (amount > 0 ? -1 : 1), pages());
				return true;
			}
			return super.mouseScrolled(mx, my, amount);
		}
	}
}

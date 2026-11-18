package com.ddev14.yapchat.ui;

import com.ddev14.yapchat.YapChatHud;
import com.ddev14.yapchat.YapChatModClient;
import com.ddev14.yapchat.bubble.BubbleStyle;
import com.ddev14.yapchat.bubble.ChatBubbles;
import com.ddev14.yapchat.chat.ChatAnimationType;
import com.ddev14.yapchat.chat.ChatDisplayTiming;
import com.ddev14.yapchat.chat.ChatLayout;
import com.ddev14.yapchat.mixin.client.OptionInstanceAccessor;
import com.ddev14.yapchat.mixin.client.TooltipAccessor;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.contents.TranslatableContents;
import com.ddev14.yapchat.chat.ChatMessageEntry;
import com.ddev14.yapchat.chat.MessageBounds;
import com.ddev14.yapchat.chat.MessageType;
import com.ddev14.yapchat.pipeline.Direction;
import com.ddev14.yapchat.render.ChatHudAvatarRenderer;
import com.ddev14.yapchat.render.ItemIcons;
import com.ddev14.yapchat.render.ChatMessageBoxPainter;
import com.ddev14.yapchat.ui.Controls.Choice;
import com.ddev14.yapchat.ui.Controls.ColorSwatch;
import com.ddev14.yapchat.ui.Controls.IconButton;
import com.ddev14.yapchat.ui.Controls.ItemSlot;
import com.ddev14.yapchat.ui.Controls.Kind;
import com.ddev14.yapchat.ui.Controls.PositionPicker;
import com.ddev14.yapchat.ui.Controls.Slider;
import com.ddev14.yapchat.ui.Controls.TextButton;
import com.ddev14.yapchat.ui.Controls.Toggle;
import com.ddev14.yapchat.ui.Popovers.ColorPopover;
import com.ddev14.yapchat.ui.Popovers.ItemPopover;
import com.ddev14.yapchat.ui.Popovers.Popover;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import com.ddev14.yapchat.compat.Gfx;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.ddev14.yapchat.compat.Argb;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

/**
 * Yap Chat settings, laid out like the Sodium/Iris option screens: a category column on the left,
 * translucent option bars in the middle, and an info panel on the right that explains the hovered
 * option (plus a live chat preview on the Appearance and Messages pages).
 * Every change is applied and saved immediately.
 */
public class YapChatSettingsScreen extends Screen {

	private enum Tab {
		GENERAL("general", "minecraft:comparator"),
		APPEARANCE("appearance", "minecraft:painting"),
		MESSAGES("messages", "minecraft:name_tag"),
		BUBBLES("bubbles", "minecraft:armor_stand"),
		MINECRAFT("minecraft", "minecraft:writable_book"),
		ADVANCED("advanced", "minecraft:command_block");

		final String key;
		final String icon;

		Tab(String key, String icon) {
			this.key = key;
			this.icon = icon;
		}

		Component title() { return Component.translatable("yapchat.ui.tab." + key); }
	}

	/** One line in the option list. */
	private static final class Row {
		final Component label;
		final Component description;
		final List<UiWidget> controls;
		final int height;
		final boolean header;
		Leading leading;
		/** Free-form row (previews); drawn instead of label and controls. */
		Custom custom;
		int y; // relative to the top of the list

		Row(Component label, Component description, int height, boolean header, UiWidget... controls) {
			this.label = label;
			this.description = description;
			this.height = height;
			this.header = header;
			this.controls = Arrays.asList(controls);
		}
	}

	@FunctionalInterface
	private interface Custom {
		void draw(Gfx g, int x, int y, int w, int h, int mx, int my);
	}

	@FunctionalInterface
	private interface Leading {
		void draw(Gfx g, int x, int y);
	}

	/** Read-only value shown at the right of a row. */
	private static final class Value extends UiWidget {
		private final Supplier<String> text;

		Value(Supplier<String> text) {
			super(30, 9);
			this.text = text;
		}

		@Override
		public void render(Gfx g, Font font, int mx, int my) {
			String s = text.get();
			g.text(font, s, x + w - font.width(s), y, Theme.TEXT_DIM, true);
		}
	}

	private static final ResourceLocation LOGO = com.ddev14.yapchat.compat.Compat.id("yap-chat", "textures/gui/logo_32.png");
	private static final int SIDEBAR = 112, MARGIN = 8, ROW_H = 20, TAB_H = 20, BOTTOM = 30, TABS_Y = 38;
	private static Tab lastTab = Tab.GENERAL;

	private final Screen parent;
	private Tab tab = lastTab;

	// layout
	private int ox, oy, ow, oh;
	private int listX, listY, listW, listH;
	private Custom tabPreview;
	private int tabPreviewH;
	private int infoX, infoW;

	private final List<Row> rows = new ArrayList<>();
	private final List<UiWidget> chrome = new ArrayList<>();
	private Toggle master;
	private UiWidget resetButton;
	private long resetArmedUntil;
	private double scroll;
	private int contentHeight;
	private Popover popover;
	private Row hoveredRow, infoRow;
	private Object hoveredForTooltip;
	private long hoverSince;

	public YapChatSettingsScreen(Screen parent) {
		super(Component.translatable("yapchat.ui.title"));
		this.parent = parent;
	}

	private static YapChatHud hud() {
		return YapChatModClient.getHud();
	}

	// ── Layout ───────────────────────────────────────────────────────────────

	@Override
	protected void init() {
		// A centered block with margins on both sides (like Sodium/Iris), not the whole screen.
		ow = Math.min(width - 2 * MARGIN, 720);
		ox = (width - ow) / 2;
		oy = MARGIN;
		oh = height - 2 * MARGIN;

		// Columns: sidebar | option list (flexible) | right column with preview and description.
		listX = ox + SIDEBAR + MARGIN;
		listY = oy;
		int avail = ox + ow - listX;
		infoW = avail >= 250 + MARGIN + 6 + 170 ? Math.min(250, avail - 250 - MARGIN - 6) : 0;
		listW = infoW > 0 ? avail - infoW - MARGIN - 6 : avail - 6;
		infoX = listX + listW + 6 + MARGIN;
		listH = oy + oh - 24 - listY;

		chrome.clear();
		int by = oy + oh - 16;
		TextButton done = new TextButton(() -> Component.translatable("gui.done"), Kind.PRIMARY, this::onClose).width(90);
		chrome.add(done.at(ox + ow - done.w, by));
		// Two clicks: the first arms it ("Click again to reset"), the second within 3 s resets.
		TextButton reset = new TextButton(() -> t(System.currentTimeMillis() < resetArmedUntil ? "reset.confirm" : "reset"),
				Kind.DANGER, this::onReset);
		reset.width(Math.max(90, Math.max(font.width(t("reset")), font.width(t("reset.confirm"))) + 16));
		chrome.add(reset.at(done.x - 6 - reset.w, by));
		resetButton = reset;

		int sideW = SIDEBAR - 12;
		TextButton folder = new TextButton(() -> t("open_config"), Kind.SECONDARY, this::openConfigFolder).width(sideW);
		chrome.add(folder.at(ox + 6, oy + oh - 6 - 16));

		master = new Toggle(() -> hud().isEnabled(), v -> hud().setEnabled(v));
		master.tooltip = t("enabled.desc");
		chrome.add(master.at(ox + SIDEBAR - 6 - master.w, folder.y - 20));

		hoveredRow = infoRow = null;
		buildRows();
		clampScroll();
	}

	/** Center of the "Reset all" button in GUI coordinates (used by the in-game test). */
	public int[] resetButtonCenter() {
		for (UiWidget w : chrome) {
			if (w == resetButton) return new int[] {w.x + w.w / 2, w.y + w.h / 2};
		}
		return null;
	}

	/** Opens a tab by index (used by the in-game test). */
	public void showTab(int index) {
		switchTab(Tab.values()[Math.floorMod(index, Tab.values().length)]);
	}

	private void switchTab(Tab next) {
		if (next == tab) return;
		tab = next;
		lastTab = next;
		scroll = 0;
		popover = null;
		init();
	}

	// ── Rows per tab ─────────────────────────────────────────────────────────

	private static Component t(String key, Object... args) {
		return Component.translatable("yapchat.ui." + key, args);
	}

	private Row header(String key) {
		return new Row(t(key), null, 16, true);
	}

	private Row row(String key, UiWidget... controls) {
		return new Row(t(key), t(key + ".desc"), ROW_H, false, controls);
	}

	private void buildRows() {
		rows.clear();
		tabPreview = null;
		switch (tab) {
			case GENERAL -> {
				rows.add(header("section.chat"));
				rows.add(row("styling", new Toggle(() -> hud().isModifyMessageText(), v -> hud().setModifyMessageText(v))));
				rows.add(row("antispam", new Toggle(() -> hud().isAntiSpamEnabled(), v -> hud().setAntiSpamEnabled(v))));
				rows.add(header("section.timing"));
				rows.add(row("display_time", new Slider(1, 60, 1, () -> hud().getDisplayTimeSeconds(),
						v -> hud().setDisplayTimeSeconds(v), v -> v + t("unit.seconds").getString())));
				rows.add(row("visible", new Slider(1, 50, 1, () -> hud().getMaxVisibleMessages(),
						v -> hud().setMaxVisibleMessages(v), String::valueOf)));
				rows.add(row("history", new Slider(50, 500, 10, () -> hud().getMaxHistorySize(),
						v -> hud().setMaxHistorySize(v), String::valueOf)));
			}
			case APPEARANCE -> {
				addPreview(chatPreview(), CHAT_PREVIEW_H);
				rows.add(header("section.layout"));
				rows.add(new Row(t("position"), t("position.desc"), 54, false,
						new PositionPicker(() -> hud().getChatPosition(), v -> hud().setChatPosition(v))));
				rows.add(row("opacity", new Slider(0, 100, 5, () -> Math.round(hud().getBackgroundOpacity() * 100),
						v -> hud().setBackgroundOpacity(v / 100f), v -> v + "%")));
				rows.add(header("section.animation"));
				List<ChatAnimationType> anims = List.of(ChatAnimationType.values());
				rows.add(row("anim_in", new Choice<>(anims, () -> hud().getAnimationIn(), v -> hud().setAnimationIn(v), this::animName)));
				rows.add(row("anim_out", new Choice<>(anims, () -> hud().getAnimationOut(), v -> hud().setAnimationOut(v), this::animName)));
			}
			case MESSAGES -> {
				addPreview(chatPreview(), CHAT_PREVIEW_H);
				rows.add(header("section.types"));
				for (MessageType type : MESSAGE_ORDER) rows.add(typeRow(type));
			}
			case BUBBLES -> {
				addPreview(this::drawBubblePreview, 124);
				rows.add(header("section.bubbles"));
				rows.add(row("bubbles", new Toggle(() -> hud().isBubblesEnabled(), v -> hud().setBubblesEnabled(v))));
				rows.add(row("bubbles_own", new Toggle(() -> hud().isBubblesShowOwn(), v -> hud().setBubblesShowOwn(v))));
				rows.add(header("section.bubble_look"));
				rows.add(row("bubble_style", new Choice<>(List.of(BubbleStyle.values()), () -> hud().getBubbleStyle(),
						v -> hud().setBubbleStyle(v), v -> t("bubble_style." + v.name().toLowerCase()))));
				rows.add(row("bubble_scale", new Slider(50, 200, 10, () -> hud().getBubbleScale(),
						v -> hud().setBubbleScale(v), v -> v + "%")));
				rows.add(row("bubble_time", new Slider(2, 20, 1, () -> hud().getBubbleSeconds(),
						v -> hud().setBubbleSeconds(v), v -> v + t("unit.seconds").getString())));
				rows.add(row("bubble_distance", new Slider(4, 64, 2, () -> hud().getBubbleDistance(),
						v -> hud().setBubbleDistance(v), v -> v + t("unit.blocks").getString())));
				rows.add(row("bubble_opacity", new Slider(0, 100, 5, () -> Math.round(hud().getBubbleOpacity() * 100),
						v -> hud().setBubbleOpacity(v / 100f), v -> v + "%")));
				rows.add(row("bubble_width", new Slider(60, 300, 10, () -> hud().getBubbleMaxWidth(),
						v -> hud().setBubbleMaxWidth(v), v -> v + "px")));
			}
			case MINECRAFT -> {
				rows.add(header("section.vanilla"));
				for (OptionInstance<?> o : com.ddev14.yapchat.compat.Compat.chatOptions(minecraft.options)) {
					Row r = vanillaRow(o);
					if (r != null) rows.add(r);
				}
			}
			case ADVANCED -> {
				rows.add(header("section.menu"));
				ColorSwatch accent = new ColorSwatch(() -> hud().getAccentColor(),
						s -> openPopover(new ColorPopover(t("accent"), () -> hud().getAccentColor(),
								hex -> hud().setAccentColor(hex), Popovers.ACCENT_PRESETS), s));
				rows.add(row("accent", accent, new IconButton("reset", t("accent.reset"),
						() -> hud().setAccentColor("#E0564B"))));
				rows.add(header("section.config"));
				rows.add(new Row(t("rules"), t("rules.desc", hud().getRuleCount()), ROW_H, false,
						new Value(() -> String.valueOf(hud().getRuleCount()))));
				rows.add(new Row(t("patterns"), t("patterns.desc", hud().getServerPatternCount()), ROW_H, false,
						new Value(() -> String.valueOf(hud().getServerPatternCount()))));
				rows.add(row("reload", new TextButton(() -> t("reload.button"), Kind.SECONDARY, () -> {
					hud().loadConfig();
					init();
				})));
				rows.add(row("open_folder", new TextButton(() -> t("open_config"), Kind.SECONDARY, this::openConfigFolder)));
			}
		}
		int yy = 0;
		for (Row r : rows) {
			if (r.header && yy > 0) yy += 6;
			r.y = yy;
			yy += r.height + (r.header ? 0 : 1);
		}
		contentHeight = yy + 4;
	}

	private static final int CHAT_PREVIEW_H = 104;

	/** Wide screens: preview sits in the right column. Narrow ones: at the top of the list. */
	private void addPreview(Custom draw, int height) {
		tabPreview = draw;
		tabPreviewH = height;
		if (infoW == 0) rows.add(previewRow(draw, height));
	}

	private Row previewRow(Custom draw, int height) {
		Row r = new Row(t("preview"), null, height, false);
		r.custom = draw;
		return r;
	}

	// ── Vanilla chat options, drawn with our controls ────────────────────────

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Row vanillaRow(OptionInstance<?> raw) {
		OptionInstance o = raw;
		OptionInstanceAccessor acc = (OptionInstanceAccessor) (Object) o;
		Component rawCaption = acc.yapchat$caption();
		if (rawCaption.getString().startsWith("options.")) {
			// some options (chat delay) have no own caption string: take it from the value label "Caption: value"
			String full = acc.yapchat$toString().apply(o.get()).getString();
			int colon = full.indexOf(": ");
			if (colon > 0) rawCaption = Component.literal(full.substring(0, colon));
		}
		final Component caption = rawCaption;
		java.util.function.Function<Object, Component> label = acc.yapchat$toString();
		Component desc = null;
		try {
			Tooltip tip = acc.yapchat$tooltip().apply(o.get());
			if (tip != null) desc = ((TooltipAccessor) tip).yapchat$message();
		} catch (RuntimeException ignored) {
		}

		UiWidget control;
		Object values = o.values();
		if (o.get() instanceof Boolean) {
			control = new Toggle(() -> (Boolean) o.get(), v -> o.set(v));
		} else if (values instanceof OptionInstance.SliderableValueSet<?> raw0) {
			OptionInstance.SliderableValueSet<Object> sv = (OptionInstance.SliderableValueSet<Object>) raw0;
			control = new Slider(0, 1000, 1,
					() -> (int) Math.round(sv.toSliderValue(o.get()) * 1000),
					v -> o.set(sv.fromSliderValue(v / 1000.0)),
					v -> valueText(label.apply(o.get()), caption).getString());
			((Slider) control).reserveValue(64); // vanilla labels like "5.0 seconds"
		} else if (values instanceof OptionInstance.CycleableValueSet<?> cv) {
			List<Object> list = (List<Object>) cv.valueListSupplier().getSelectedList();
			control = new Choice<Object>(list, o::get, o::set, v -> valueText(label.apply(v), caption));
		} else {
			return null;
		}
		return new Row(caption, desc, ROW_H, false, control);
	}

	/** "Chat: Shown" → "Shown". */
	private static Component valueText(Component full, Component caption) {
		if (full.getContents() instanceof TranslatableContents tc && tc.getArgs().length == 2
				&& tc.getKey().startsWith("options.")) {
			Object v = tc.getArgs()[1];
			return v instanceof Component c ? c : Component.literal(String.valueOf(v));
		}
		String s = full.getString(), prefix = caption.getString() + ": ";
		if (s.startsWith(prefix)) return Component.literal(s.substring(prefix.length()));
		int colon = s.indexOf(": ");
		return Component.literal(colon > 0 ? s.substring(colon + 2) : s);
	}

	private static final List<MessageType> MESSAGE_ORDER = List.of(
			MessageType.PLAYER_CHAT, MessageType.WHISPER, MessageType.SYSTEM, MessageType.ERROR,
			MessageType.ACHIEVEMENT, MessageType.CHALLENGE, MessageType.JOIN_LEAVE, MessageType.SLEEP,
			MessageType.SCREENSHOT, MessageType.COMMAND_BLOCK);

	private Row typeRow(MessageType type) {
		boolean hasIcon = type != MessageType.PLAYER_CHAT && type != MessageType.WHISPER && type != MessageType.SCREENSHOT
				&& type != MessageType.JOIN_LEAVE;
		List<UiWidget> controls = new ArrayList<>();
		if (hasIcon) {
			ItemSlot slot = new ItemSlot(() -> hud().getIcon(type), Items.STICK,
					s -> openPopover(new ItemPopover(typeName(type), () -> hud().getIcon(type), id -> hud().setIcon(type, id)), s));
			slot.tooltip = t("icon.tooltip");
			controls.add(slot);
		}
		ColorSwatch swatch = new ColorSwatch(() -> hud().getBgColor(type),
				s -> openPopover(new ColorPopover(typeName(type), () -> hud().getBgColor(type), hex -> hud().setBgColor(type, hex)), s));
		swatch.tooltip = t("color.tooltip");
		controls.add(swatch);
		controls.add(new IconButton("reset", t("reset_type.tooltip"), () -> hud().resetType(type)));

		Row r = new Row(typeName(type), t("type." + type.name().toLowerCase() + ".desc"), 22, false, controls.toArray(new UiWidget[0]));
		r.leading = (g, x, y) -> drawTypeIcon(g, type, x, y);
		return r;
	}

	private Component typeName(MessageType type) {
		return t("type." + type.name().toLowerCase());
	}

	private Component animName(ChatAnimationType a) {
		return t("anim." + a.name().toLowerCase());
	}

	private String localName() {
		return minecraft.player != null ? com.ddev14.yapchat.compat.Compat.name(minecraft.player.getGameProfile()) : minecraft.getUser().getName();
	}

	private void drawTypeIcon(Gfx g, MessageType type, int x, int y) {
		switch (type) {
			case PLAYER_CHAT, WHISPER -> ChatHudAvatarRenderer.renderPlayerHeadByName(g, x + 1, y + 1, localName(), 1f);
			case SCREENSHOT -> ChatHudAvatarRenderer.renderCameraIcon(g, x + 1, y + 1, 1f);
			case JOIN_LEAVE -> ItemIcons.draw(g, Items.OAK_DOOR, x, y);
			default -> ItemIcons.draw(g, Controls.itemOf(hud().getIcon(type), Items.STICK), x, y);
		}
	}

	// ── Actions ──────────────────────────────────────────────────────────────

	private void onReset() {
		if (System.currentTimeMillis() < resetArmedUntil) {
			resetArmedUntil = 0;
			hud().resetSettings();
			init();
		} else {
			resetArmedUntil = System.currentTimeMillis() + 3000;
		}
	}

	private void openPopover(Popover p, UiWidget anchor) {
		popover = p.anchor(anchor, width, height);
	}


	private void openConfigFolder() {
		com.ddev14.yapchat.compat.Compat.openFolder(hud().getConfigPath().getParent());
	}

	@Override
	public void onClose() {
		minecraft.options.save();
		com.ddev14.yapchat.compat.Compat.setScreen(parent);
	}

	// ── Rendering ────────────────────────────────────────────────────────────

	private void renderContent(Gfx g, int mx, int my) {
		int pmx = popover != null ? -1000 : mx, pmy = popover != null ? -1000 : my;

		renderSidebar(g, pmx, pmy);
		renderList(g, pmx, pmy);
		if (infoW > 0) renderInfo(g, pmx, pmy);
		// copy: a widget action (Reset) may rebuild the screen while we iterate
		for (UiWidget w : List.copyOf(chrome)) w.render(g, font, pmx, pmy);

		if (popover != null) {
			g.nextStratum();
			g.fill(0, 0, width, height, 0x60000000);
			popover.render(g, font, mx, my);
			if (popover.closed) popover = null;
		} else {
			renderTooltip(g, mx, my);
		}
	}

	private Tab tabAt(double mx, double my) {
		if (mx < ox || mx >= ox + SIDEBAR || my < oy + TABS_Y) return null;
		int i = (int) ((my - oy - TABS_Y) / TAB_H);
		if (i < 0 || i >= Tab.values().length) return null;
		return Tab.values()[i];
	}

	private void renderSidebar(Gfx g, int mx, int my) {
		int r = ox + SIDEBAR;
		g.fill(ox, oy, r, oy + oh, Theme.SIDEBAR);

		g.blit(LOGO, ox + 8, oy + 9, 0f, 0f, 16, 16, 32, 32, 32, 32, Argb.white(1f));
		g.text(font, Component.literal("Yap Chat"), ox + 30, oy + 9, Theme.TEXT, true);
		String version = FabricLoader.getInstance().getModContainer("yap-chat")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		g.text(font, version, ox + 30, oy + 19, Theme.TEXT_FAINT, true);

		Tab hovered = tabAt(mx, my);
		for (Tab tb : Tab.values()) {
			int ty = oy + TABS_Y + tb.ordinal() * TAB_H;
			boolean active = tb == tab;
			if (active) {
				g.fill(ox, ty, r, ty + TAB_H, Theme.ACCENT_FAINT);
				g.fill(ox, ty, ox + 2, ty + TAB_H, Theme.ACCENT);
			} else if (tb == hovered) {
				g.fill(ox, ty, r, ty + TAB_H, Theme.ROW_HOVER);
			}
			ItemIcons.draw(g, Controls.itemOf(tb.icon, Items.STICK), ox + 8, ty + 2);
			g.text(font, tb.title(), ox + 30, ty + 6, active || tb == hovered ? Theme.TEXT : Theme.TEXT_DIM, true);
		}

		g.text(font, t(hud().isEnabled() ? "enabled.on" : "enabled.off"), ox + 8, master.y + 2,
				hud().isEnabled() ? Theme.TEXT : Theme.TEXT_DIM, true);
	}

	private void renderList(Gfx g, int mx, int my) {
		boolean inList = mx >= listX && mx < listX + listW && my >= listY && my < listY + listH;
		hoveredRow = null;
		g.enableScissor(listX, listY, listX + listW, listY + listH);
		int top = listY - (int) scroll;
		for (Row r : rows) {
			int ry = top + r.y;
			if (ry + r.height < listY || ry > listY + listH) {
				for (UiWidget c : r.controls) c.visible = false;
				continue;
			}
			if (r.header) {
				g.text(font, r.label, listX + 2, ry + 5, Theme.TEXT_DIM, true);
				continue;
			}

			if (r.custom != null) {
				g.fill(listX, ry, listX + listW, ry + r.height, Theme.ROW);
				r.custom.draw(g, listX + 4, ry + 4, listW - 8, r.height - 8, mx, my);
				continue;
			}
			boolean hover = inList && my >= ry && my < ry + r.height;
			if (hover) hoveredRow = r;
			g.fill(listX, ry, listX + listW, ry + r.height, hover ? Theme.ROW_ACTIVE : Theme.ROW);

			int textX = listX + 6;
			if (r.leading != null) {
				r.leading.draw(g, textX - 2, ry + (r.height - 16) / 2);
				textX += 18;
			}

			int cx = listX + listW - 5;
			for (int i = r.controls.size() - 1; i >= 0; i--) {
				UiWidget c = r.controls.get(i);
				c.visible = true;
				cx -= c.w;
				c.at(cx, ry + (r.height - c.h) / 2);
				cx -= 4;
			}
			String label = r.label.getString();
			int textMax = cx - textX - 4;
			if (font.width(label) > textMax) label = font.plainSubstrByWidth(label, textMax - font.width("…")) + "…";
			g.text(font, label, textX, ry + (r.height - 8) / 2, Theme.TEXT, true);
			for (UiWidget c : r.controls) c.render(g, font, mx, my);
		}
		g.disableScissor();
		if (hoveredRow != null) infoRow = hoveredRow;

		if (contentHeight > listH) {
			int barH = Math.max(16, listH * listH / contentHeight);
			int barY = listY + (int) ((listH - barH) * (scroll / (contentHeight - listH)));
			g.fill(listX + listW + 2, barY, listX + listW + 4, barY + barH, 0x80FFFFFF);
		}
	}

	// ── Info panel and live preview ──────────────────────────────────────────

	private void renderInfo(Gfx g, int mx, int my) {
		int top = listY;
		if (tabPreview != null) {
			g.fill(infoX, top, infoX + infoW, top + tabPreviewH, Theme.ROW);
			tabPreview.draw(g, infoX + 4, top + 4, infoW - 8, tabPreviewH - 8, mx, my);
			top += tabPreviewH + 6;
		}
		Component title = infoRow != null ? infoRow.label : tab.title();
		Component body = infoRow != null ? infoRow.description : t("info.hint");
		List<FormattedCharSequence> lines = body != null ? font.split(body, infoW - 12) : List.of();
		int boxH = 20 + lines.size() * 10 + (lines.isEmpty() ? 0 : 4);
		g.fill(infoX, top, infoX + infoW, top + boxH, Theme.ROW);
		g.text(font, title, infoX + 6, top + 7, Theme.TEXT, true);
		int ly = top + 20;
		for (FormattedCharSequence line : lines) {
			g.text(font, line, infoX + 6, ly, Theme.TEXT_DIM, true);
			ly += 10;
		}
	}

	private List<ChatMessageEntry> previewEntries() {
		String me = localName();
		List<ChatMessageEntry> list = new ArrayList<>();
		list.add(sample(Component.literal("Alex joined the game").withStyle(ChatFormatting.YELLOW), MessageType.JOIN_LEAVE, null));
		list.add(sample(Component.literal("[").withStyle(ChatFormatting.GOLD).append(Component.literal(me))
				.append("]:").append(Component.literal(" hi all!").withStyle(ChatFormatting.WHITE)), MessageType.PLAYER_CHAT, me));
		ChatMessageEntry whisper = sample(Component.literal("[Steve]:").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" psst").withStyle(ChatFormatting.WHITE)), MessageType.WHISPER, "Steve");
		whisper.direction = Direction.INCOMING;
		whisper.receiverName = me;
		list.add(whisper);
		list.add(sample(Component.literal("Got [Stone Age]").withStyle(ChatFormatting.GREEN), MessageType.ACHIEVEMENT, null));
		list.add(sample(Component.literal("Unknown command").withStyle(ChatFormatting.RED), MessageType.ERROR, null));
		return list;
	}

	private static ChatMessageEntry sample(Component text, MessageType type, String sender) {
		ChatMessageEntry e = new ChatMessageEntry(text, null, null, type, sender);
		e.timestamp = e.firstMessageTime = System.currentTimeMillis() - 10_000; // past the enter animation
		return e;
	}

	/** The preview loops: messages come in one by one with the chosen animation, then leave with the other. */
	private static final long PREVIEW_STAGGER = 450, PREVIEW_SHOW_S = 3, PREVIEW_PAUSE = 700;

	private Custom chatPreview() {
		List<ChatMessageEntry> entries = previewEntries();
		return (g, x, y, w, h, mx, my) -> {
			g.fill(x, y, x + w, y + h, 0x50000000);
			float scale = Math.min(0.8f, w / 290f);
			int virtualW = Math.round(w / scale), virtualH = Math.round(h / scale);
			int pos = hud().getChatPosition();
			boolean top = ChatLayout.isTopPosition(pos);
			long now = System.currentTimeMillis();
			long cycle = (entries.size() - 1) * PREVIEW_STAGGER + PREVIEW_SHOW_S * 1000 + PREVIEW_PAUSE;
			long start = now - Math.floorMod(now, cycle);

			g.enableScissor(x, y, x + w, y + h);
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(scale, scale);
			HashMap<Integer, MessageBounds> bounds = new HashMap<>();
			int cy = top ? 4 : virtualH - 4;
			for (int i = entries.size() - 1; i >= 0; i--) {
				ChatMessageEntry e = entries.get(i);
				e.timestamp = e.firstMessageTime = start + i * PREVIEW_STAGGER;
				if (now < e.timestamp) continue;
				ChatDisplayTiming.AnimTransform anim = ChatDisplayTiming.computeTransform(
						e, now, (int) PREVIEW_SHOW_S, hud().getAnimationIn(), hud().getAnimationOut());
				if (anim.hidden) continue;
				int hgt = ChatMessageBoxPainter.renderSingleMessage(g, font, virtualW, cy, e, anim.alpha,
						hud().getBackgroundOpacity(), bounds, pos, top, false, Integer.MIN_VALUE, anim.offsetX, anim.offsetY);
				cy += top ? hgt + 2 : -(hgt + 2);
			}
			g.pose().popMatrix();
			g.disableScissor();
		};
	}

	private void drawBubblePreview(Gfx g, int x, int y, int w, int h, int mx, int my) {
		g.fill(x, y, x + w, y + h, 0x50000000);
		int mid = x + w / 2;
		int feet = y + h - 4, headTop = feet - 72;
		g.enableScissor(x, y, x + w, y + h);
		if (minecraft.player != null) {
			com.ddev14.yapchat.compat.Compat.drawPlayer(g, mid - 30, headTop, mid + 30, feet, 36, 0.0625f,
					mx, my, minecraft.player);
		} else {
			g.pose().pushMatrix();
			g.pose().translate(mid - 16, feet - 34);
			g.pose().scale(2f, 2f);
			ChatHudAvatarRenderer.renderDefaultSteveHead(g, 0, 0, 1f);
			g.pose().popMatrix();
			headTop = feet - 36;
		}
		long life = Math.min(hud().getBubbleSeconds() * 1000L, 4000L);
		long cycle = life + 700;
		long age = Math.floorMod(System.currentTimeMillis(), cycle);
		float alpha = Math.min(1f, age / 150f) * Math.max(0f, Math.min(1f, (life - age) / 400f));
		int maxW = Math.min(hud().getBubbleMaxWidth(), w - 10);
		float opacity = hud().getBubbleOpacity();
		int bottom = headTop - 2;
		float scale = hud().getBubbleScale() / 100f;
		BubbleStyle style = hud().getBubbleStyle();
		g.pose().pushMatrix();
		g.pose().translate(mid, bottom);
		g.pose().scale(scale, scale);
		int used = ChatBubbles.drawBubble(g, font, 0, 0, t("bubble.sample2"), maxW, alpha, opacity, true, style);
		ChatBubbles.drawBubble(g, font, 0, -used - 2, t("bubble.sample1"), maxW, alpha * 0.7f, opacity, false, style);
		g.pose().popMatrix();
		g.disableScissor();
	}

	// ── Tooltips ─────────────────────────────────────────────────────────────

	private void renderTooltip(Gfx g, int mx, int my) {
		UiWidget over = null;
		for (UiWidget c : allWidgets()) {
			if (c.visible && c.tooltip != null && c.isHovered(mx, my)) over = c;
		}
		Object key = over != null ? over : (infoW == 0 ? hoveredRow : null);
		if (key != hoveredForTooltip) {
			hoveredForTooltip = key;
			hoverSince = System.currentTimeMillis();
		}
		if (key == null || System.currentTimeMillis() - hoverSince < 400) return;
		if (over != null) {
			g.setTooltipForNextFrame(font, over.tooltip, mx, my);
		} else if (hoveredRow != null && hoveredRow.description != null) {
			// no room for the info panel: descriptions become tooltips
			g.setTooltipForNextFrame(font, font.split(hoveredRow.description, 200), mx, my);
		}
	}

	private List<UiWidget> allWidgets() {
		List<UiWidget> all = new ArrayList<>(chrome); // always a copy, safe against rebuilds
		for (Row r : rows) all.addAll(r.controls);
		return all;
	}

	private List<UiWidget> visibleListControls(double my) {
		List<UiWidget> out = new ArrayList<>();
		if (my < listY || my >= listY + listH) return out;
		for (Row r : rows) for (UiWidget c : r.controls) if (c.visible) out.add(c);
		return out;
	}

	// ── Input ────────────────────────────────────────────────────────────────

	private boolean onClick(double mx, double my, int button) {
		if (popover != null) {
			popover.mouseClicked(mx, my, button);
			if (popover.closed) popover = null;
			return true;
		}
		for (UiWidget c : allWidgets()) c.blur();
		Tab clickedTab = tabAt(mx, my);
		if (clickedTab != null) {
			if (clickedTab != tab) Controls.click();
			switchTab(clickedTab);
			return true;
		}
		for (UiWidget c : List.copyOf(chrome)) if (c.mouseClicked(mx, my, button)) return true;
		for (UiWidget c : visibleListControls(my)) if (c.mouseClicked(mx, my, button)) return true;
		return false;
	}

	private boolean onDrag(double mx, double my) {
		for (UiWidget c : allWidgets()) if (c.mouseDragged(mx, my)) return true;
		return false;
	}

	private void onRelease() {
		for (UiWidget c : allWidgets()) c.mouseReleased();
	}

	private boolean onScroll(double mx, double my, double v) {
		if (popover != null) return popover.mouseScrolled(mx, my, v);
		for (UiWidget c : visibleListControls(my)) if (c.mouseScrolled(mx, my, v)) return true;
		if (mx >= listX && mx < listX + listW + 6 && my >= listY && my < listY + listH) {
			scroll -= v * 20;
			clampScroll();
			return true;
		}
		return false;
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - listH)));
	}

	private boolean onKey(int key, int modifiers) {
		if (popover != null) {
			popover.keyPressed(key, modifiers);
			if (popover.closed) popover = null;
			return true;
		}
		for (UiWidget c : allWidgets()) if (c.keyPressed(key, modifiers)) return true;
		return false;
	}

	private boolean onChar(String s) {
		if (popover != null) return popover.charTyped(s);
		for (UiWidget c : allWidgets()) if (c.charTyped(s)) return true;
		return false;
	}

	// ── Screen methods (their signatures differ between Minecraft versions) ──

	@Override
	public void render(GuiGraphics raw, int mx, int my, float delta) {
		renderBackground(raw);
		super.render(raw, mx, my, delta);
		Gfx g = Gfx.of(raw);
		renderContent(g, mx, my);
		g.flushTooltips();
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		return onClick(mx, my, button) || super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		return onDrag(mx, my) || super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		onRelease();
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double v) {
		return onScroll(mx, my, v) || super.mouseScrolled(mx, my, v);
	}

	@Override
	public boolean keyPressed(int key, int scancode, int modifiers) {
		return onKey(key, modifiers) || super.keyPressed(key, scancode, modifiers);
	}

	@Override
	public boolean charTyped(char c, int modifiers) {
		return onChar(String.valueOf(c)) || super.charTyped(c, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

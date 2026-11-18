package com.ddev14.yapchat.pipeline;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Read-only helpers for inspecting chat {@link Component} trees.
 * No game state is touched, so everything here works in unit tests.
 */
public final class Components {
	private Components() {}

	private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final Pattern LEGACY_CODE = Pattern.compile("(?i)[§&][0-9A-FK-OR]");
	private static final int RED = 0xFF5555;
	private static final int DARK_RED = 0xAA0000;

	/** True if {@code name} has the shape of a Minecraft profile name. */
	public static boolean isNick(String name) {
		return name != null && NICK.matcher(name).matches() && !name.chars().allMatch(c -> c == '_');
	}

	/** Removes legacy §/& formatting codes. */
	public static String stripCodes(String text) {
		return text == null ? "" : LEGACY_CODE.matcher(text).replaceAll("");
	}

	/** True if nothing visible remains after stripping formatting codes. */
	public static boolean isVisiblyEmpty(Component component) {
		return component == null || stripCodes(component.getString()).isBlank();
	}

	// ── Translation keys ─────────────────────────────────────────────────────

	/**
	 * Depth-first, pre-order search for the first translatable whose key matches.
	 * Looks at the root, its siblings and translation arguments, so messages that a server
	 * wrapped into {@code literal("").append(translatable(...))} are still recognised.
	 */
	public static Optional<TranslatableContents> findTranslatable(Component component, Predicate<String> keyMatches) {
		if (component == null) return Optional.empty();
		if (component.getContents() instanceof TranslatableContents tc) {
			if (keyMatches.test(tc.getKey())) return Optional.of(tc);
			for (Object arg : tc.getArgs()) {
				if (arg instanceof Component c) {
					Optional<TranslatableContents> found = findTranslatable(c, keyMatches);
					if (found.isPresent()) return found;
				}
			}
		}
		for (Component sibling : component.getSiblings()) {
			Optional<TranslatableContents> found = findTranslatable(sibling, keyMatches);
			if (found.isPresent()) return found;
		}
		return Optional.empty();
	}

	/** Argument {@code index} of a translatable as a component (strings are wrapped); null if absent. */
	public static Component arg(TranslatableContents tc, int index) {
		Object[] args = tc.getArgs();
		if (index < 0 || index >= args.length) return null;
		Object a = args[index];
		if (a instanceof Component c) return c;
		return a == null ? null : Component.literal(String.valueOf(a));
	}

	/** The last component argument of the root translatable — the body for vanilla chat decorations. */
	public static Component lastArg(Component component) {
		if (component != null && component.getContents() instanceof TranslatableContents tc && tc.getArgs().length > 0) {
			return arg(tc, tc.getArgs().length - 1);
		}
		return null;
	}

	// ── Player names ─────────────────────────────────────────────────────────

	/** A player identity found in a component. */
	public record Identity(String name, UUID id) {}

	/**
	 * Reads the profile name from a decorated name component, preferring data the server attached
	 * on purpose over the visible text:
	 * <ol>
	 *   <li>insertion (vanilla puts the plain profile name there),</li>
	 *   <li>click event {@code /tell Name}, {@code /msg Name}, {@code /w Name}, {@code /r Name},</li>
	 *   <li>show-entity hover (name and UUID),</li>
	 *   <li>visible text, if it is a single nick or ends with one ("[VIP] Name").</li>
	 * </ol>
	 */
	public static Identity identity(Component display) {
		if (display == null) return null;
		Identity[] hint = new Identity[1];
		display.visit((style, text) -> {
			Identity id = fromStyle(style);
			if (id != null) {
				hint[0] = id;
				return Optional.of(Boolean.TRUE);
			}
			return Optional.empty();
		}, Style.EMPTY);
		if (hint[0] != null) return hint[0];

		String visible = stripCodes(display.getString()).trim();
		if (isNick(visible)) return new Identity(visible, null);
		int space = visible.lastIndexOf(' ');
		if (space >= 0) {
			String last = visible.substring(space + 1).replaceAll("[^A-Za-z0-9_]", "");
			if (isNick(last)) return new Identity(last, null);
		}
		return null;
	}

	private static Identity fromStyle(Style style) {
		String insertion = style.getInsertion();
		if (isNick(insertion)) return new Identity(insertion, null);

		ClickEvent click = style.getClickEvent();
		String command = null;
		if (click != null && (click.getAction() == ClickEvent.Action.SUGGEST_COMMAND
				|| click.getAction() == ClickEvent.Action.RUN_COMMAND)) command = click.getValue();
		if (command != null) {
			String[] parts = command.trim().split("\\s+");
			if (parts.length >= 2 && parts[0].matches("(?i)/(tell|msg|w|whisper|m|t|pm|r|reply)") && isNick(parts[1])) {
				return new Identity(parts[1], null);
			}
		}

		HoverEvent hover = style.getHoverEvent();
		HoverEvent.EntityTooltipInfo info = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_ENTITY);
		if (info != null) {
			String name = info.name == null ? null : stripCodes(info.name.getString()).trim();
			if (isNick(name)) return new Identity(name, info.id);
		}
		return null;
	}

	// ── Text positions ───────────────────────────────────────────────────────

	/**
	 * The text a player sees ({@link Component#getString()} without legacy codes) plus a map from
	 * every visible character back to its index in {@code getString()}.
	 */
	public record VisibleText(String text, int[] rawIndex, int rawLength) {
		public static VisibleText of(Component component) {
			String raw = component.getString();
			StringBuilder sb = new StringBuilder(raw.length());
			int[] map = new int[raw.length() + 1];
			int n = 0;
			for (int i = 0; i < raw.length(); i++) {
				char c = raw.charAt(i);
				if ((c == '§' || c == '&') && i + 1 < raw.length()
						&& "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(raw.charAt(i + 1)) >= 0) {
					i++;
					continue;
				}
				sb.append(c);
				map[n++] = i;
			}
			map[n] = raw.length();
			int[] trimmed = new int[n + 1];
			System.arraycopy(map, 0, trimmed, 0, n + 1);
			return new VisibleText(sb.toString(), trimmed, raw.length());
		}

		/** Index in {@code getString()} of visible character {@code visibleIndex}. */
		public int toRaw(int visibleIndex) {
			return rawIndex[Math.max(0, Math.min(visibleIndex, rawIndex.length - 1))];
		}
	}

	/**
	 * Copy of {@code component} from character {@code start} (index in {@code getString()}) to the end,
	 * keeping every piece's style — colors, hover and click events survive.
	 */
	public static Component sliceFrom(Component component, int start) {
		MutableComponent out = Component.empty();
		int[] pos = {0};
		component.visit((style, text) -> {
			int from = pos[0];
			int to = from + text.length();
			if (to > start && !text.isEmpty()) {
				out.append(Component.literal(text.substring(Math.max(0, start - from))).setStyle(style));
			}
			pos[0] = to;
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	// ── Styling ──────────────────────────────────────────────────────────────

	/**
	 * True if every visible piece of the message is red or dark red — how vanilla and most plugins
	 * mark failures. Pieces made of legacy {@code §c}/{@code &c} codes count too.
	 */
	public static boolean isStyledAsError(Component component) {
		List<Integer> colors = new ArrayList<>();
		boolean[] uncolored = {false};
		component.visit((style, text) -> {
			String visible = stripCodes(text);
			if (visible.isBlank()) return Optional.empty();
			TextColor color = style.getColor();
			Integer legacy = leadingLegacyColor(text);
			if (legacy != null) colors.add(legacy);
			else if (color != null) colors.add(color.getValue());
			else uncolored[0] = true;
			return Optional.empty();
		}, Style.EMPTY);
		if (colors.isEmpty() || uncolored[0]) return false;
		for (int c : colors) {
			if (c != RED && c != DARK_RED) return false;
		}
		return true;
	}

	private static Integer leadingLegacyColor(String text) {
		Integer found = null;
		for (int i = 0; i + 1 < text.length(); i++) {
			char c = text.charAt(i);
			if (c != '§' && c != '&') continue;
			char code = Character.toLowerCase(text.charAt(i + 1));
			if (code == 'c') found = RED;
			else if (code == '4') found = DARK_RED;
			else if ("0123456789abdef".indexOf(code) >= 0) return 0xFFFFFF; // another color = not all red
		}
		return found;
	}
}

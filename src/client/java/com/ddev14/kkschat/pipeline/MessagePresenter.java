package com.ddev14.kkschat.pipeline;

import com.ddev14.kkschat.chat.LegacyAmpersandFormatting;
import com.ddev14.kkschat.chat.MessageType;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a {@link ClassifiedMessage} into the component shown in the HUD.
 *
 * <p>Rule of thumb: never rebuild what the server already formatted. Only vanilla-decorated
 * player chat and whispers get the mod's own layout; the body and names are always the
 * server's own components, so colors, ranks, hover and click events are kept.
 */
public final class MessagePresenter {
	private MessagePresenter() {}

	private static final Pattern LEGACY = Pattern.compile("(?i)&[0-9a-fk-or]");

	/**
	 * @param modify false = show the message exactly as the server sent it
	 */
	public static Component present(ClassifiedMessage msg, boolean modify) {
		Component original = msg.origin().content();
		if (!modify) return original;

		return switch (msg.type()) {
			case WHISPER -> whisper(msg);
			case PLAYER_CHAT -> playerChat(msg);
			case CHALLENGE -> tint(original, ChatFormatting.LIGHT_PURPLE, LegacyAmpersandFormatting::applyLegacyColorCodesForChallenge);
			case ACHIEVEMENT -> tint(original, ChatFormatting.GREEN, LegacyAmpersandFormatting::applyLegacyColorCodesForAchievement);
			default -> convertLegacy(prettifyIds(original), LegacyAmpersandFormatting::applyLegacyColorCodesForSystem);
		};
	}

	// ── Whisper ──────────────────────────────────────────────────────────────

	private static Component whisper(ClassifiedMessage msg) {
		boolean outgoing = msg.direction() == Direction.OUTGOING;
		Component name = outgoing
				? nameOrNull(msg.receiverDisplay(), msg.receiverName())
				: nameOrNull(msg.senderDisplay(), msg.senderName());
		if (msg.body() == null || name == null) {
			return convertLegacy(msg.origin().content(), LegacyAmpersandFormatting::applyLegacyColorCodesForSystem);
		}
		MutableComponent prefix = Component.translatable(outgoing ? "kkschat.whisper.to" : "kkschat.whisper.from", name)
				.withStyle(ChatFormatting.GRAY);
		return prefix.append(Component.literal(" ")).append(body(msg.body()));
	}

	// ── Player chat ──────────────────────────────────────────────────────────

	private static Component playerChat(ClassifiedMessage msg) {
		// Vanilla decoration ("<Name> text"): replace it with the mod's "[Name]: text".
		boolean vanillaDecorated = msg.tier() == ClassifiedMessage.Tier.CHAT_TYPE
				|| msg.tier() == ClassifiedMessage.Tier.TRANSLATION_KEY;
		Component name = nameOrNull(msg.senderDisplay(), msg.senderName());
		if (vanillaDecorated && msg.body() != null && name != null) {
			return Component.literal("[").withStyle(ChatFormatting.GOLD)
					.append(name)
					.append(Component.literal("]:"))
					.append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
					.append(body(msg.body()));
		}
		// A plugin's own chat format: keep it exactly, only translate &-codes in plain text.
		return convertLegacy(msg.origin().content(), text -> LegacyAmpersandFormatting.applyLegacyColorCodes(text, ChatFormatting.WHITE));
	}

	/** Name component in gold unless the server already colored it. */
	private static Component nameOrNull(Component display, String plainName) {
		if (display != null && !display.getString().isBlank()) {
			return Component.empty().withStyle(ChatFormatting.GOLD).append(display);
		}
		if (plainName != null) return Component.literal(plainName).withStyle(ChatFormatting.GOLD);
		return null;
	}

	/** Message body in white unless styled by the server; plain text with &-codes is colorized. */
	private static Component body(Component body) {
		if (isPlainWithLegacyCodes(body)) {
			return LegacyAmpersandFormatting.applyLegacyColorCodes(body.getString(), ChatFormatting.WHITE);
		}
		return Component.empty().withStyle(ChatFormatting.WHITE).append(body);
	}

	// ── Helpers ──────────────────────────────────────────────────────────────

	private static Component tint(Component original, ChatFormatting color, java.util.function.Function<String, Component> legacy) {
		if (isPlainWithLegacyCodes(original)) {
			return LegacyAmpersandFormatting.copyClickHandlers(original, legacy.apply(original.getString()));
		}
		return original.copy().setStyle(original.getStyle().withColor(color));
	}

	private static Component convertLegacy(Component original, java.util.function.Function<String, Component> legacy) {
		if (isPlainWithLegacyCodes(original)) {
			return LegacyAmpersandFormatting.copyClickHandlers(original, legacy.apply(original.getString()));
		}
		return original;
	}

	/**
	 * Only literal text without server styling is converted: a styled component already
	 * says how it should look, and an "&" in it is just an ampersand.
	 */
	private static boolean isPlainWithLegacyCodes(Component c) {
		if (!LEGACY.matcher(c.getString()).find()) return false;
		boolean[] styled = {false};
		c.visit((style, text) -> {
			if (style.getColor() != null) styled[0] = true;
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return !styled[0] && (c.getContents() instanceof PlainTextContents || c.getContents() == PlainTextContents.EMPTY);
	}

	private static final Map<String, String> MINECRAFT_ID_TO_KEY;
	static {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("minecraft:overworld", "dimension.minecraft.overworld");
		m.put("minecraft:the_nether", "dimension.minecraft.the_nether");
		m.put("minecraft:the_end", "dimension.minecraft.the_end");
		m.put("minecraft:day", "kkschat.time.day");
		m.put("minecraft:noon", "kkschat.time.noon");
		m.put("minecraft:sunset", "kkschat.time.sunset");
		m.put("minecraft:dusk", "kkschat.time.dusk");
		m.put("minecraft:night", "kkschat.time.night");
		m.put("minecraft:midnight", "kkschat.time.midnight");
		m.put("minecraft:sunrise", "kkschat.time.sunrise");
		m.put("minecraft:dawn", "kkschat.time.dawn");
		MINECRAFT_ID_TO_KEY = Map.copyOf(m);
	}

	/** Replaces raw ids like {@code minecraft:night} in plain system text with localized names. */
	private static Component prettifyIds(Component component) {
		String text = component.getString();
		if (!text.contains("minecraft:") || !component.getSiblings().isEmpty()
				|| !(component.getContents() instanceof PlainTextContents)) {
			return component;
		}
		String result = text;
		Language lang = Language.getInstance();
		for (Map.Entry<String, String> e : MINECRAFT_ID_TO_KEY.entrySet()) {
			if (!result.contains(e.getKey())) continue;
			String path = e.getKey().substring(e.getKey().indexOf(':') + 1).replace('_', ' ');
			String fallback = Character.toUpperCase(path.charAt(0)) + path.substring(1);
			result = result.replace(e.getKey(), lang.getOrDefault(e.getValue(), fallback));
		}
		return result.equals(text) ? component : Component.literal(result).setStyle(component.getStyle());
	}
}

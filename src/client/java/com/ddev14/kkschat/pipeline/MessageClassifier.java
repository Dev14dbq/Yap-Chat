package com.ddev14.kkschat.pipeline;

import com.ddev14.kkschat.chat.MessageType;
import com.ddev14.kkschat.pipeline.ClassifiedMessage.Tier;
import com.ddev14.kkschat.pipeline.Components.Identity;
import com.ddev14.kkschat.pipeline.Components.VisibleText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;

/**
 * Decides what a message is. Checks run from the most reliable source of truth to the least,
 * and the first one that answers wins:
 *
 * <ol>
 *   <li><b>Chat type</b> of a player chat packet — the server says exactly what it is.</li>
 *   <li><b>Translation keys</b> anywhere in the component tree — language independent.</li>
 *   <li><b>Server patterns</b> — regexes for plugin formats (built-in + from the config).</li>
 *   <li><b>Error styling</b> — the whole message is red.</li>
 *   <li>Otherwise it is a plain {@link MessageType#SYSTEM} message.</li>
 * </ol>
 *
 * There is deliberately no guessing by single words such as "error", "left" or "bed":
 * those words appear in ordinary sentences and used to produce wrong types and icons.
 *
 * <p>Pure function of its inputs — safe to unit test without a running game.
 */
public final class MessageClassifier {
	private MessageClassifier() {}

	/**
	 * What the classifier may know about the game.
	 *
	 * @param localPlayerName profile name of the local player (may be null)
	 * @param isOnlinePlayer  true for names in the tab list
	 * @param userPatterns    patterns from the config, tried before the built-in ones
	 */
	public record Context(String localPlayerName, Predicate<String> isOnlinePlayer, List<ServerPattern> userPatterns) {
		public Context {
			if (isOnlinePlayer == null) isOnlinePlayer = n -> false;
			if (userPatterns == null) userPatterns = List.of();
		}
	}

	/** Words servers use for "the local player" inside private message formats. */
	private static final Set<String> SELF_WORDS = Set.of(
			"me", "you", "я", "мне", "меня", "вы", "ты", "вам", "тебе", "ich", "mir", "moi", "yo", "mí", "eu", "mim", "ja", "mnie", "ben", "bana");

	// Vanilla chat type ids (stable across 26.x).
	private static final String CT_MSG_IN = "minecraft:msg_command_incoming";
	private static final String CT_MSG_OUT = "minecraft:msg_command_outgoing";
	private static final String CT_TEAM_IN = "minecraft:team_msg_command_incoming";
	private static final String CT_TEAM_OUT = "minecraft:team_msg_command_outgoing";

	public static ClassifiedMessage classify(IncomingMessage in, Context ctx) {
		if (in.source() == Source.PLAYER_CHAT && in.chatTypeId() != null) {
			return byChatType(in, ctx);
		}
		ClassifiedMessage byKey = byTranslationKey(in, ctx);
		if (byKey != null) return byKey;

		ClassifiedMessage byPattern = byPattern(in, ctx, ctx.userPatterns());
		if (byPattern == null) byPattern = byPattern(in, ctx, ServerPattern.DEFAULTS);
		if (byPattern != null) return byPattern;

		if (in.source() != Source.PLAYER_CHAT && Components.isStyledAsError(in.content())) {
			return ClassifiedMessage.simple(MessageType.ERROR, in, Tier.ERROR_STYLE);
		}
		return ClassifiedMessage.simple(MessageType.SYSTEM, in, Tier.FALLBACK);
	}

	// ── 1. Chat type ─────────────────────────────────────────────────────────

	private static ClassifiedMessage byChatType(IncomingMessage in, Context ctx) {
		Component body = in.body() != null ? in.body() : Components.lastArg(in.content());
		Identity sender = senderIdentity(in);
		String type = in.chatTypeId();

		if (type.equals(CT_MSG_OUT) || type.equals(CT_TEAM_OUT)) {
			Identity target = Components.identity(in.targetDisplay());
			MessageType mt = type.equals(CT_MSG_OUT) ? MessageType.WHISPER : MessageType.PLAYER_CHAT;
			return new ClassifiedMessage(mt, Direction.OUTGOING,
					ctx.localPlayerName(), null, in.senderDisplay(),
					target != null ? target.name() : null, in.targetDisplay(),
					body, in, Tier.CHAT_TYPE);
		}
		MessageType mt = type.equals(CT_MSG_IN) ? MessageType.WHISPER : MessageType.PLAYER_CHAT;
		Direction dir = type.equals(CT_MSG_IN) || type.equals(CT_TEAM_IN) ? Direction.INCOMING : Direction.NONE;
		return new ClassifiedMessage(mt, dir,
				sender != null ? sender.name() : null, sender != null ? sender.id() : in.senderId(), in.senderDisplay(),
				null, null, body, in, Tier.CHAT_TYPE);
	}

	private static Identity senderIdentity(IncomingMessage in) {
		if (Components.isNick(in.senderProfileName())) {
			return new Identity(in.senderProfileName(), in.senderId());
		}
		Identity fromDisplay = Components.identity(in.senderDisplay());
		if (fromDisplay != null && fromDisplay.id() == null && in.senderId() != null) {
			return new Identity(fromDisplay.name(), in.senderId());
		}
		return fromDisplay;
	}

	// ── 2. Translation keys ──────────────────────────────────────────────────

	private static boolean isKnownKey(String key) {
		return key.startsWith("commands.message.display.")
				|| key.equals("chat.type.text") || key.equals("chat.type.emote") || key.equals("chat.type.announcement")
				|| key.equals("chat.type.team.text") || key.equals("chat.type.team.sent")
				|| key.equals("chat.type.admin")
				|| key.startsWith("chat.type.advancement.")
				|| key.startsWith("multiplayer.player.joined") || key.equals("multiplayer.player.left")
				|| key.startsWith("block.minecraft.bed.") || key.startsWith("sleep.")
				|| key.startsWith("screenshot.")
				|| isErrorKey(key);
	}

	/** Vanilla keys for command failures. */
	private static boolean isErrorKey(String key) {
		return key.startsWith("command.unknown.") || key.startsWith("command.expected.")
				|| key.startsWith("command.context.")
				|| key.equals("command.exception") || key.equals("command.failed")
				|| key.startsWith("commands.generic.") && !key.equals("commands.generic.dimension")
				|| key.startsWith("argument.") || key.startsWith("parsing.")
				|| key.startsWith("permissions.requires.");
	}

	private static ClassifiedMessage byTranslationKey(IncomingMessage in, Context ctx) {
		Optional<TranslatableContents> found = Components.findTranslatable(in.content(), MessageClassifier::isKnownKey);
		if (found.isEmpty()) return null;
		TranslatableContents tc = found.get();
		String key = tc.getKey();

		switch (key) {
			case "commands.message.display.incoming" -> {
				Component name = Components.arg(tc, 0);
				Identity who = Components.identity(name);
				return new ClassifiedMessage(MessageType.WHISPER, Direction.INCOMING,
						who != null ? who.name() : null, who != null ? who.id() : null, name,
						null, null, Components.arg(tc, 1), in, Tier.TRANSLATION_KEY);
			}
			case "commands.message.display.outgoing" -> {
				Component name = Components.arg(tc, 0);
				Identity who = Components.identity(name);
				return new ClassifiedMessage(MessageType.WHISPER, Direction.OUTGOING,
						ctx.localPlayerName(), null, null,
						who != null ? who.name() : null, name, Components.arg(tc, 1), in, Tier.TRANSLATION_KEY);
			}
			case "chat.type.text", "chat.type.emote", "chat.type.announcement" -> {
				return playerLine(in, Components.arg(tc, 0), Components.arg(tc, 1));
			}
			case "chat.type.team.text" -> {
				return playerLine(in, Components.arg(tc, 1), Components.arg(tc, 2));
			}
			case "chat.type.team.sent" -> {
				return new ClassifiedMessage(MessageType.PLAYER_CHAT, Direction.OUTGOING,
						ctx.localPlayerName(), null, Components.arg(tc, 1),
						null, null, Components.arg(tc, 2), in, Tier.TRANSLATION_KEY);
			}
			case "chat.type.admin" -> {
				return ClassifiedMessage.simple(MessageType.COMMAND_BLOCK, in, Tier.TRANSLATION_KEY);
			}
			case "chat.type.advancement.challenge" -> {
				return ClassifiedMessage.simple(MessageType.CHALLENGE, in, Tier.TRANSLATION_KEY);
			}
			default -> {
				if (key.startsWith("chat.type.advancement.")) {
					return ClassifiedMessage.simple(MessageType.ACHIEVEMENT, in, Tier.TRANSLATION_KEY);
				}
				if (key.startsWith("multiplayer.player.")) {
					return ClassifiedMessage.simple(MessageType.JOIN_LEAVE, in, Tier.TRANSLATION_KEY);
				}
				if (key.startsWith("block.minecraft.bed.") || key.startsWith("sleep.")) {
					return ClassifiedMessage.simple(MessageType.SLEEP, in, Tier.TRANSLATION_KEY);
				}
				if (key.startsWith("screenshot.")) {
					return ClassifiedMessage.simple(MessageType.SCREENSHOT, in, Tier.TRANSLATION_KEY);
				}
				return ClassifiedMessage.simple(MessageType.ERROR, in, Tier.TRANSLATION_KEY);
			}
		}
	}

	private static ClassifiedMessage playerLine(IncomingMessage in, Component name, Component body) {
		Identity who = Components.identity(name);
		return new ClassifiedMessage(MessageType.PLAYER_CHAT, Direction.NONE,
				who != null ? who.name() : null, who != null ? who.id() : null, name,
				null, null, body, in, Tier.TRANSLATION_KEY);
	}

	// ── 3. Server patterns ───────────────────────────────────────────────────

	private static ClassifiedMessage byPattern(IncomingMessage in, Context ctx, List<ServerPattern> patterns) {
		if (patterns.isEmpty()) return null;
		VisibleText visible = VisibleText.of(in.content());
		String full = visible.text();
		int lead = 0;
		while (lead < full.length() && Character.isWhitespace(full.charAt(lead))) lead++;
		String text = full.strip();
		if (text.isEmpty()) return null;

		for (ServerPattern p : patterns) {
			Matcher m = p.regex().matcher(text);
			if (!m.matches()) continue;
			ClassifiedMessage result = fromMatch(p, m, in, ctx, visible, lead);
			if (result != null) return result;
		}
		return null;
	}

	private static ClassifiedMessage fromMatch(ServerPattern p, Matcher m, IncomingMessage in, Context ctx,
			VisibleText visible, int lead) {
		Component body = null;
		int bodyStart = groupStart(m, "body");
		if (bodyStart >= 0) {
			body = Components.sliceFrom(in.content(), visible.toRaw(lead + bodyStart));
		}

		switch (p.type()) {
			case WHISPER -> {
				String from = clean(group(m, "from"));
				String to = clean(group(m, "to"));
				boolean fromSelf = isSelf(from, ctx);
				boolean toSelf = isSelf(to, ctx);
				if (from != null && !fromSelf && !Components.isNick(from)) return null;
				if (to != null && !toSelf && !Components.isNick(to)) return null;
				if (from == null && to == null) return null;

				if (from != null && fromSelf && to != null && !toSelf) {
					return whisperOut(to, body, in, ctx);
				}
				if (from == null) {
					return toSelf ? null : whisperOut(to, body, in, ctx);
				}
				if (fromSelf) return null; // "[me -> me]" is nonsense
				return new ClassifiedMessage(MessageType.WHISPER, Direction.INCOMING,
						from, null, null, toSelf || to == null ? ctx.localPlayerName() : to, null,
						body, in, Tier.SERVER_PATTERN);
			}
			case PLAYER_CHAT -> {
				String name = clean(group(m, "name"));
				if (!Components.isNick(name) || !ctx.isOnlinePlayer().test(name)) return null;
				return new ClassifiedMessage(MessageType.PLAYER_CHAT, Direction.NONE,
						name, null, null, null, null, body, in, Tier.SERVER_PATTERN);
			}
			case JOIN_LEAVE -> {
				String name = clean(group(m, "name"));
				if (name != null && !Components.isNick(name)) return null;
				return ClassifiedMessage.simple(MessageType.JOIN_LEAVE, in, Tier.SERVER_PATTERN);
			}
			default -> {
				return ClassifiedMessage.simple(p.type(), in, Tier.SERVER_PATTERN);
			}
		}
	}

	private static ClassifiedMessage whisperOut(String to, Component body, IncomingMessage in, Context ctx) {
		return new ClassifiedMessage(MessageType.WHISPER, Direction.OUTGOING,
				ctx.localPlayerName(), null, null, to, null, body, in, Tier.SERVER_PATTERN);
	}

	private static boolean isSelf(String name, Context ctx) {
		if (name == null) return false;
		return SELF_WORDS.contains(name.toLowerCase(Locale.ROOT))
				|| ctx.localPlayerName() != null && name.equalsIgnoreCase(ctx.localPlayerName());
	}

	/** Strips ranks like "[VIP] " that a plugin left inside the name group. */
	private static String clean(String raw) {
		if (raw == null) return null;
		String s = raw.replaceAll("\\[[^\\]]*\\]", " ").trim();
		int space = s.lastIndexOf(' ');
		if (space >= 0) s = s.substring(space + 1);
		return s.isEmpty() ? null : s;
	}

	private static String group(Matcher m, String name) {
		try {
			return m.group(name);
		} catch (IllegalArgumentException noSuchGroup) {
			return null;
		}
	}

	private static int groupStart(Matcher m, String name) {
		try {
			return m.start(name);
		} catch (IllegalArgumentException noSuchGroup) {
			return -1;
		}
	}
}

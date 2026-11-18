package com.ddev14.yapchat.pipeline;

import com.ddev14.yapchat.chat.MessageType;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A text format used by a server or plugin, matched against the visible message text.
 *
 * <p>Named groups the classifier understands:
 * <ul>
 *   <li>{@code from} — author of a private message,</li>
 *   <li>{@code to} — recipient of a private message,</li>
 *   <li>{@code name} — author of a chat line / player who joined or left,</li>
 *   <li>{@code body} — the message text after the decoration.</li>
 * </ul>
 * For {@link MessageType#PLAYER_CHAT} the {@code name} group must be a player who is online,
 * otherwise the pattern is ignored — that keeps lines like {@code "Error: ..."} out of player chat.
 */
public record ServerPattern(String id, Pattern regex, MessageType type) {

	/** Same shape as in the config file. */
	public static final class Config {
		public String id;
		public String regex;
		public String type;

		public Config() {}

		public Config(String id, String regex, String type) {
			this.id = id;
			this.regex = regex;
			this.type = type;
		}
	}

	/** Compiles a config entry; throws {@link IllegalArgumentException} with a readable reason. */
	public static ServerPattern compile(Config c) {
		if (c == null || c.regex == null || c.type == null) {
			throw new IllegalArgumentException("pattern needs \"regex\" and \"type\"");
		}
		MessageType type;
		try {
			type = MessageType.valueOf(c.type.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("unknown type \"" + c.type + "\"");
		}
		try {
			return new ServerPattern(c.id != null ? c.id : c.regex, Pattern.compile(c.regex), type);
		} catch (PatternSyntaxException e) {
			throw new IllegalArgumentException("bad regex: " + e.getDescription());
		}
	}

	private static final String NICK = "[A-Za-z0-9_]{3,16}";
	private static final String RANKS = "(?:\\[[^\\]]{1,24}\\]\\s*)*";
	private static final String ARROW = "(?:->|→|»|>>|➡|⇒)";

	private static ServerPattern p(String id, String regex, MessageType type) {
		return new ServerPattern(id, Pattern.compile(regex), type);
	}

	/** Built-in formats of common servers and plugins. User patterns from the config run first. */
	public static final List<ServerPattern> DEFAULTS = List.of(
			// EssentialsX / CMI / most /msg plugins: "[Steve -> me] hi", "[me → Alex] hi"
			p("bracket-arrow",
					"^\\[" + RANKS + "(?<from>[^\\]]+?)\\s*" + ARROW + "\\s*" + RANKS + "(?<to>[^\\]]+?)\\]\\s*:?\\s*(?<body>.*)$",
					MessageType.WHISPER),
			// "Steve -> me: hi" without brackets
			p("plain-arrow",
					"^" + RANKS + "(?<from>" + NICK + "|[^\\s:]{1,8})\\s*" + ARROW + "\\s*(?<to>" + NICK + "|[^\\s:]{1,8})\\s*[:»]\\s*(?<body>.*)$",
					MessageType.WHISPER),
			// Hypixel and similar: "From [MVP+] Notch: hi" / "To [VIP] Jeb_: hi"
			p("from", "^(?:From|от|От)\\s+" + RANKS + "(?<from>" + NICK + ")\\s*[:»]\\s*(?<body>.*)$", MessageType.WHISPER),
			p("to", "^(?:To|кому|Кому)\\s+" + RANKS + "(?<to>" + NICK + ")\\s*[:»]\\s*(?<body>.*)$", MessageType.WHISPER),
			// Vanilla whisper text that a proxy/plugin already flattened into plain text
			p("vanilla-en-in", "^(?<from>" + NICK + ") whispers to you: (?<body>.*)$", MessageType.WHISPER),
			p("vanilla-en-out", "^You whisper to (?<to>" + NICK + "): (?<body>.*)$", MessageType.WHISPER),
			p("vanilla-ru-in", "^(?<from>" + NICK + ") шепчет вам: (?<body>.*)$", MessageType.WHISPER),
			p("vanilla-ru-out", "^Вы шепчете (?<to>" + NICK + "): (?<body>.*)$", MessageType.WHISPER),

			// Command block feedback flattened to text: "[@: Set the time to 1000]"
			p("command-block", "^\\[@: (?<body>.*)\\]$", MessageType.COMMAND_BLOCK),

			// Join / leave plugins: "[+] Steve", "[-] Steve", flattened vanilla text
			p("join-plus", "^\\[[+\\-]\\]\\s*" + RANKS + "(?<name>" + NICK + ")$", MessageType.JOIN_LEAVE),
			p("vanilla-en-join", "^(?<name>" + NICK + ") (?:joined|left) the game$", MessageType.JOIN_LEAVE),

			// Chat formats; only accepted when "name" is an online player
			p("angle", "^" + RANKS + "<" + RANKS + "(?<name>" + NICK + ")>\\s?(?<body>.*)$", MessageType.PLAYER_CHAT),
			p("ranked-separator",
					"^" + RANKS + "(?:[^\\sA-Za-z0-9_\\[]{1,3}\\s*)?(?<name>" + NICK + ")\\s*(?:" + RANKS + ")?(?:»|>>|:|→|\\||›|>)\\s*(?<body>.+)$",
					MessageType.PLAYER_CHAT)
	);
}

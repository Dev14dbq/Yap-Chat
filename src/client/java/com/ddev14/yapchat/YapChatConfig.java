package com.ddev14.yapchat;

import com.ddev14.yapchat.chat.ChatAnimationType;
import com.ddev14.yapchat.chat.ChatRule;
import com.ddev14.yapchat.pipeline.ServerPattern;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Сохранение и загрузка настроек Yap Chat в/из JSON-файла.
 */
final class YapChatConfig {
	private YapChatConfig() {}

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.setLenient()
			.create();
	private static final Path CONFIG_PATH = migrateLegacy(FabricLoader.getInstance()
			.getConfigDir().resolve("yap-chat.json"));

	/** The mod used to be called KKS Chat: carry an old kks-chat.json over once. */
	private static Path migrateLegacy(Path path) {
		Path legacy = path.resolveSibling("kks-chat.json");
		if (!Files.exists(path) && Files.exists(legacy)) {
			try {
				Files.move(legacy, path);
			} catch (IOException e) {
				try {
					Files.copy(legacy, path);
				} catch (IOException ignored) {
				}
			}
		}
		return path;
	}

	private static final Type RULES_TYPE = new TypeToken<List<ChatRule>>(){}.getType();

	private static class Data {
		float backgroundOpacity;
		int displayTimeSeconds;
		int maxHistorySize;
		int maxVisibleMessages;
		int chatPosition;
		boolean antiSpamEnabled;
		boolean modifyMessageText;
		boolean enabled = true;
		// Иконки (item ID)
		String iconSystem       = "minecraft:stick";
		String iconError        = "minecraft:barrier";
		String iconSleep        = "minecraft:red_bed";
		String iconAchievement  = "minecraft:emerald";
		String iconChallenge    = "minecraft:netherite_ingot";
		String iconCommandBlock = "minecraft:command_block";
		String iconWhisper      = "minecraft:paper";
		String iconJoinLeave    = "minecraft:oak_door";
		// Rules
		List<ChatRule> rules    = new ArrayList<>();
		// Chat bubbles above players
		Boolean bubblesEnabled;
		Boolean bubblesShowOwn;
		Integer bubbleSeconds;
		Integer bubbleDistance;
		Float bubbleOpacity;
		Integer bubbleMaxWidth;
		Integer bubbleScale;
		String accentColor;
		String bubbleStyle;
		// Server / plugin message formats
		List<ServerPattern.Config> serverPatterns = new ArrayList<>();
		// Animation
		String animationIn  = "FADE";
		String animationOut = "FADE";
		// Background colors per message type (type name → hex string)
		Map<String, String> bgColors = null; // null = use defaults
	}

	private static String nonNull(String value, String def) {
		return value != null && !value.isBlank() ? value : def;
	}

	static Path path() {
		return CONFIG_PATH;
	}

	static void save(YapChatHud hud) {
		try {
			String cfg = "{\n"

				+ "  // ── General ─────────────────────────────────────────────────────────\n"
				+ "  // Enable or disable Yap Chat entirely.\n"
				+ "  // When disabled, the vanilla Minecraft chat is used instead.\n"
				+ "  \"enabled\": " + hud.isEnabled() + ",\n"

				+ "\n"
				+ "  // Modify message text (stylization, reformatting).\n"
				+ "  // false = show every message exactly as the server sent it (types and icons still work).\n"
				+ "  \"modifyMessageText\": " + hud.isModifyMessageText() + ",\n"

				+ "\n"
				+ "  // Suppress duplicate messages and merge them into one entry with a repeat counter.\n"
				+ "  \"antiSpamEnabled\": " + hud.isAntiSpamEnabled() + ",\n"

				+ "\n"
				+ "  // ── Appearance ───────────────────────────────────────────────────────\n"
				+ "  // Background opacity of message boxes (0.0 = fully transparent, 1.0 = fully opaque).\n"
				+ "  \"backgroundOpacity\": " + hud.backgroundOpacity + ",\n"

				+ "\n"
				+ "  // Chat position on screen.\n"
				+ "  // 0 = center-bottom, 1 = center-top, 2 = left-top,\n"
				+ "  // 3 = left-bottom,   4 = right-top,  5 = right-bottom\n"
				+ "  \"chatPosition\": " + hud.getChatPosition() + ",\n"

				+ "\n"
				+ "  // ── History ──────────────────────────────────────────────────────────\n"
				+ "  // How long (in seconds) a message stays visible in the closed overlay.\n"
				+ "  \"displayTimeSeconds\": " + hud.displayTimeSeconds + ",\n"

				+ "\n"
				+ "  // Maximum number of messages stored in history.\n"
				+ "  \"maxHistorySize\": " + hud.maxHistorySize + ",\n"

				+ "\n"
				+ "  // Maximum number of messages visible at once when the chat is open.\n"
				+ "  \"maxVisibleMessages\": " + hud.maxVisibleMessages + ",\n"

				+ "\n"
				+ "  // ── Icons ────────────────────────────────────────────────────────────\n"
				+ "  // Item IDs for icons displayed next to each message type.\n"
				+ "  // Use any valid Minecraft item ID, e.g. \"minecraft:diamond\".\n"
				+ "  \"iconSystem\":       \"" + hud.iconSystem       + "\",\n"
				+ "  \"iconError\":        \"" + hud.iconError        + "\",\n"
				+ "  \"iconSleep\":        \"" + hud.iconSleep        + "\",\n"
				+ "  \"iconAchievement\":  \"" + hud.iconAchievement  + "\",\n"
				+ "  \"iconChallenge\":    \"" + hud.iconChallenge    + "\",\n"
				+ "  \"iconCommandBlock\": \"" + hud.iconCommandBlock + "\",\n"
				+ "  \"iconWhisper\":      \"" + hud.iconWhisper      + "\",\n"
				+ "  \"iconJoinLeave\":    \"" + hud.iconJoinLeave    + "\",\n"

				+ "\n"
				+ "  // ── Animation ────────────────────────────────────────────────────────\n"
				+ "  // Animation when a message appears (enter) and disappears (leave).\n"
				+ "  // Values: FADE, SLIDE_LEFT, SLIDE_RIGHT, SLIDE_UP, SLIDE_DOWN, BOUNCE, NONE\n"
				+ "  \"animationIn\":  \"" + hud.animationIn.name()  + "\",\n"
				+ "  \"animationOut\": \"" + hud.animationOut.name() + "\",\n"

				+ "\n"
				+ "  // ── Background colors ─────────────────────────────────────────────────\n"
				+ "  // Background color per message type. Hex string, e.g. \"#1A0028\".\n"
				+ "  // Use \"#000000\" for the default black background.\n"
				+ "  \"bgColors\": " + GSON.toJson(hud.bgColors) + ",\n"

				+ "\n"
				+ "  // ── Chat bubbles ─────────────────────────────────────────────────────\n"
				+ "  // Show what players write in a bubble above their head.\n"
				+ "  \"bubblesEnabled\": " + hud.bubblesEnabled + ",\n"
				+ "  // Also show your own bubble (visible in third-person view).\n"
				+ "  \"bubblesShowOwn\": " + hud.bubblesShowOwn + ",\n"
				+ "  // Seconds a bubble stays (2-20), max distance in blocks (4-64).\n"
				+ "  \"bubbleSeconds\": " + hud.bubbleSeconds + ",\n"
				+ "  \"bubbleDistance\": " + hud.bubbleDistance + ",\n"
				+ "  // Bubble background opacity (0.0-1.0) and max text width in pixels (60-300).\n"
				+ "  \"bubbleOpacity\": " + hud.bubbleOpacity + ",\n"
				+ "  \"bubbleMaxWidth\": " + hud.bubbleMaxWidth + ",\n"
				+ "  // Bubble size in percent (50-200) and look: DARK, WHITE, TOOLTIP or NAMETAG.\n"
				+ "  \"bubbleScale\": " + hud.bubbleScale + ",\n"
				+ "  \"bubbleStyle\": \"" + hud.bubbleStyle.name() + "\",\n"
				+ "  // Accent color of the settings screen.\n"
				+ "  \"accentColor\": \"" + hud.accentColor + "\",\n"

				+ "\n"
				+ "  // ── Server message formats ─────────────────────────────────────────────\n"
				+ "  // Teach Yap Chat the chat / private-message format of your server.\n"
				+ "  // Checked before the built-in formats (EssentialsX, CMI, Hypixel-style, vanilla).\n"
				+ "  // \"regex\" is matched against the whole visible message text. Named groups:\n"
				+ "  //   from / to — author / recipient of a private message (\"me\", \"я\" or your nick = you)\n"
				+ "  //   name      — author of a chat line (must be online) or player who joined/left\n"
				+ "  //   body      — the message itself\n"
				+ "  // \"type\" is a MessageType name (see Rules below).\n"
				+ "  //\n"
				+ "  // Example — \"✉ Steve » you: hi\":\n"
				+ "  //   { \"id\": \"my-server-pm\", \"regex\": \"^✉ (?<from>\\\\w+) » (?<to>\\\\w+): (?<body>.*)$\", \"type\": \"WHISPER\" }\n"
				+ "  \"serverPatterns\": " + GSON.toJson(hud.serverPatternConfigs) + ",\n"

				+ "\n"
				+ "  // ── Rules ────────────────────────────────────────────────────────────\n"
				+ "  // Rules are evaluated in order for every incoming message.\n"
				+ "  // Each rule has an \"if\" block (conditions) and a \"then\" block (actions).\n"
				+ "  //\n"
				+ "  // Conditions (\"if\"):\n"
				+ "  //   type        — MessageType name: PLAYER_CHAT, WHISPER, SYSTEM, ERROR, SLEEP,\n"
				+ "  //                  ACHIEVEMENT, CHALLENGE, COMMAND_BLOCK, JOIN_LEAVE, SCREENSHOT\n"
				+ "  //   contains    — text contains this string (case-sensitive)\n"
				+ "  //   startsWith  — text starts with this string\n"
				+ "  //   endsWith    — text ends with this string\n"
				+ "  //   regex       — full Java regex; first capture group becomes the matched fragment\n"
				+ "  //   matchType   — \"ALL\" (default, AND) or \"ANY\" (OR)\n"
				+ "  //\n"
				+ "  // Actions (\"then\"):\n"
				+ "  //   icon         — override icon (Minecraft item ID, e.g. \"minecraft:diamond\")\n"
				+ "  //   hide         — hide the message entirely (true/false)\n"
				+ "  //   noStyle      — disable stylization for this message (true/false)\n"
				+ "  //   displayTime  — override display time in seconds for this message\n"
				+ "  //   color        — tint the whole message (hex, e.g. \"#FFD700\")\n"
				+ "  //   replaceText  — replace the entire message text with this string\n"
				+ "  //   replaceMatch — replace the matched fragment (from contains/regex)\n"
				+ "  //   colorMatch   — color the matched fragment only (hex)\n"
				+ "  //   stopAfter    — stop processing further rules after this one (true/false)\n"
				+ "  //\n"
				+ "  // Example — hide command-block output:\n"
				+ "  //   { \"if\": { \"type\": \"COMMAND_BLOCK\" }, \"then\": { \"hide\": true } }\n"
				+ "  //\n"
				+ "  // Example — gold icon + color for VIP players:\n"
				+ "  //   { \"if\": { \"contains\": \"[VIP]\" }, \"then\": { \"icon\": \"minecraft:gold_ingot\", \"colorMatch\": \"#FFD700\" } }\n"
				+ "  \"rules\": " + GSON.toJson(hud.rules) + "\n"

				+ "}\n";

			Files.writeString(CONFIG_PATH, cfg, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LoggerFactory.getLogger("YapChatConfig").warn("Failed to save config", e);
		}
	}

	static void load(YapChatHud hud) {
		if (!Files.exists(CONFIG_PATH)) return;
		try {
			String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
			JsonReader reader = new JsonReader(new StringReader(json));
			reader.setLenient(true);
			Data d = GSON.fromJson(reader, Data.class);
			if (d == null) return;
			hud.backgroundOpacity = Math.max(0f, Math.min(1f, d.backgroundOpacity));
			hud.displayTimeSeconds = Math.max(1, Math.min(60, d.displayTimeSeconds));
			hud.maxHistorySize = Math.max(50, Math.min(500, d.maxHistorySize));
			hud.maxVisibleMessages = Math.max(1, Math.min(50, d.maxVisibleMessages));
			hud.chatPosition = Math.max(0, Math.min(5, d.chatPosition));
			hud.antiSpamEnabled = d.antiSpamEnabled;
			hud.modifyMessageText = d.modifyMessageText;
			hud.enabled = d.enabled;
			hud.iconSystem       = nonNull(d.iconSystem,       "minecraft:stick");
			hud.iconError        = nonNull(d.iconError,        "minecraft:barrier");
			hud.iconSleep        = nonNull(d.iconSleep,        "minecraft:red_bed");
			hud.iconAchievement  = nonNull(d.iconAchievement,  "minecraft:emerald");
			hud.iconChallenge    = nonNull(d.iconChallenge,    "minecraft:netherite_ingot");
			hud.iconCommandBlock = nonNull(d.iconCommandBlock, "minecraft:command_block");
			hud.iconWhisper      = nonNull(d.iconWhisper,      "minecraft:paper");
			hud.iconJoinLeave    = nonNull(d.iconJoinLeave,    "minecraft:oak_door");
			hud.rules.clear();
			if (d.rules != null) hud.rules.addAll(d.rules);
			if (d.bubblesEnabled != null) hud.bubblesEnabled = d.bubblesEnabled;
			if (d.bubblesShowOwn != null) hud.bubblesShowOwn = d.bubblesShowOwn;
			if (d.bubbleSeconds != null) hud.bubbleSeconds = Math.max(2, Math.min(20, d.bubbleSeconds));
			if (d.bubbleDistance != null) hud.bubbleDistance = Math.max(4, Math.min(64, d.bubbleDistance));
			if (d.bubbleOpacity != null) hud.bubbleOpacity = Math.max(0f, Math.min(1f, d.bubbleOpacity));
			if (d.bubbleMaxWidth != null) hud.bubbleMaxWidth = Math.max(60, Math.min(300, d.bubbleMaxWidth));
			if (d.accentColor != null && d.accentColor.trim().matches("#?[0-9a-fA-F]{6}")) {
				String hex = d.accentColor.trim();
				hud.setAccentSilently(hex.startsWith("#") ? hex.toUpperCase(java.util.Locale.ROOT) : "#" + hex.toUpperCase(java.util.Locale.ROOT));
			}
			if (d.bubbleScale != null) hud.bubbleScale = Math.max(50, Math.min(200, d.bubbleScale));
			if (d.bubbleStyle != null) {
				try {
					hud.bubbleStyle = com.ddev14.yapchat.bubble.BubbleStyle.valueOf(d.bubbleStyle.trim().toUpperCase(java.util.Locale.ROOT));
				} catch (IllegalArgumentException ignored) {
				}
			}
			hud.serverPatterns.clear();
			hud.serverPatternConfigs.clear();
			if (d.serverPatterns != null) {
				for (ServerPattern.Config c : d.serverPatterns) {
					if (c == null) continue;
					hud.serverPatternConfigs.add(c);
					try {
						hud.serverPatterns.add(ServerPattern.compile(c));
					} catch (IllegalArgumentException e) {
						LoggerFactory.getLogger("YapChatConfig").warn("Ignoring server pattern {}: {}", c.id, e.getMessage());
					}
				}
			}
			if (d.animationIn != null) {
				try { hud.animationIn = ChatAnimationType.valueOf(d.animationIn.toUpperCase()); }
				catch (IllegalArgumentException ignored) { hud.animationIn = ChatAnimationType.FADE; }
			}
			if (d.animationOut != null) {
				try { hud.animationOut = ChatAnimationType.valueOf(d.animationOut.toUpperCase()); }
				catch (IllegalArgumentException ignored) { hud.animationOut = ChatAnimationType.FADE; }
			}
			if (d.bgColors != null && !d.bgColors.isEmpty()) {
				// Start from defaults, then override with saved values
				hud.bgColors = YapChatHud.defaultBgColors();
				hud.bgColors.putAll(d.bgColors);
			}
		} catch (IOException e) {
			LoggerFactory.getLogger("YapChatConfig").warn("Failed to load config", e);
		}
	}
}

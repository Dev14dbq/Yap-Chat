package com.ddev14.kkschat.pipeline;

import com.ddev14.kkschat.chat.MessageType;
import com.ddev14.kkschat.pipeline.ClassifiedMessage.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixtures are real message shapes from vanilla servers and common plugins.
 * Each test that is named after a bug reproduces a case the old text-guessing code got wrong.
 */
class MessageClassifierTest {

	private static final String ME = "Softfoxxy";
	private static final Set<String> ONLINE = Set.of("Steve", "Alex", "Notch", "Jeb_", ME);
	private static final MessageClassifier.Context CTX =
			new MessageClassifier.Context(ME, n -> ONLINE.stream().anyMatch(o -> o.equalsIgnoreCase(n)), List.of());

	// ── helpers ──────────────────────────────────────────────────────────────

	private static ClassifiedMessage system(Component c) {
		return MessageClassifier.classify(IncomingMessage.system(c, false), CTX);
	}

	private static ClassifiedMessage system(String text) {
		return system(Component.literal(text));
	}

	private static ClassifiedMessage chat(String chatType, Component senderDisplay, Component target,
			String profile, String body) {
		Component bodyC = Component.literal(body);
		Component full = Component.translatable("chat.type.text", senderDisplay, bodyC);
		IncomingMessage in = new IncomingMessage(full, Source.PLAYER_CHAT, chatType, senderDisplay, target,
				profile != null ? UUID.nameUUIDFromBytes(profile.getBytes()) : null, profile, bodyC, false);
		return MessageClassifier.classify(in, CTX);
	}

	/** A player name as vanilla decorates it: visible text plus the profile name in the insertion. */
	private static MutableComponent vanillaName(String name) {
		return Component.literal(name).setStyle(Style.EMPTY
				.withInsertion(name)
				.withClickEvent(new ClickEvent.SuggestCommand("/tell " + name + " ")));
	}

	private static Component red(String text) {
		return Component.literal(text).withStyle(ChatFormatting.RED);
	}

	// ── 1. Chat type (player chat packets) ───────────────────────────────────

	@Nested
	class ChatTypes {
		@Test
		void incomingVanillaWhisper() {
			ClassifiedMessage m = chat("minecraft:msg_command_incoming", vanillaName("Steve"), null, "Steve", "hi");
			assertEquals(MessageType.WHISPER, m.type());
			assertEquals(Direction.INCOMING, m.direction());
			assertEquals("Steve", m.senderName());
			assertEquals("hi", m.body().getString());
			assertEquals(Tier.CHAT_TYPE, m.tier());
		}

		@Test
		void outgoingVanillaWhisperNamesTheRecipient() {
			ClassifiedMessage m = chat("minecraft:msg_command_outgoing", vanillaName(ME), vanillaName("Alex"), ME, "yo");
			assertEquals(MessageType.WHISPER, m.type());
			assertEquals(Direction.OUTGOING, m.direction());
			assertEquals(ME, m.senderName());
			assertEquals("Alex", m.receiverName());
		}

		@Test
		void bugOrdinaryChatWithKeywordsStaysChat() {
			// Old code: "left" → JOIN_LEAVE, "error" → ERROR, "bed" → SLEEP.
			ClassifiedMessage m = chat("minecraft:chat", vanillaName("Steve"), null, "Steve",
					"I left my bed, no error here, tell me when you whisper");
			assertEquals(MessageType.PLAYER_CHAT, m.type());
			assertEquals("Steve", m.senderName());
		}

		@Test
		void disguisedChatWithoutProfileUsesDisplayName() {
			ClassifiedMessage m = chat("minecraft:chat", vanillaName("Notch"), null, null, "hello");
			assertEquals(MessageType.PLAYER_CHAT, m.type());
			assertEquals("Notch", m.senderName());
		}

		@Test
		void rankedDisplayNameStillResolvesProfileName() {
			Component display = Component.literal("[Admin] ").append(vanillaName("Jeb_"));
			ClassifiedMessage m = chat("minecraft:chat", display, null, null, "hi");
			assertEquals("Jeb_", m.senderName());
		}
	}

	// ── 2. Translation keys ──────────────────────────────────────────────────

	@Nested
	class TranslationKeys {
		@Test
		void joinWrappedInLiteralIsFound() {
			// Plugins often wrap vanilla messages: literal("").append(translatable(...)).
			Component c = Component.empty().append(
					Component.translatable("multiplayer.player.joined", vanillaName("Steve")).withStyle(ChatFormatting.YELLOW));
			assertEquals(MessageType.JOIN_LEAVE, system(c).type());
		}

		@Test
		void advancementKinds() {
			assertEquals(MessageType.ACHIEVEMENT,
					system(Component.translatable("chat.type.advancement.task", vanillaName("Steve"), Component.literal("Stone Age"))).type());
			assertEquals(MessageType.ACHIEVEMENT,
					system(Component.translatable("chat.type.advancement.goal", vanillaName("Steve"), Component.literal("Sky's the Limit"))).type());
			assertEquals(MessageType.CHALLENGE,
					system(Component.translatable("chat.type.advancement.challenge", vanillaName("Steve"), Component.literal("Arbalistic"))).type());
		}

		@Test
		void whisperKeysCarryDirectionAndNames() {
			ClassifiedMessage in = system(Component.translatable("commands.message.display.incoming",
					vanillaName("Alex"), Component.literal("psst")));
			assertEquals(MessageType.WHISPER, in.type());
			assertEquals(Direction.INCOMING, in.direction());
			assertEquals("Alex", in.senderName());
			assertEquals("psst", in.body().getString());

			ClassifiedMessage out = system(Component.translatable("commands.message.display.outgoing",
					vanillaName("Alex"), Component.literal("ok")));
			assertEquals(Direction.OUTGOING, out.direction());
			assertEquals("Alex", out.receiverName());
			assertEquals(ME, out.senderName());
		}

		@Test
		void chatTypeTextViaSystemChannel() {
			ClassifiedMessage m = system(Component.translatable("chat.type.text", vanillaName("Steve"), Component.literal("hey")));
			assertEquals(MessageType.PLAYER_CHAT, m.type());
			assertEquals("Steve", m.senderName());
			assertEquals("hey", m.body().getString());
		}

		@Test
		void bedMessagesFromActionBar() {
			IncomingMessage in = IncomingMessage.system(Component.translatable("block.minecraft.bed.not_safe"), true);
			assertEquals(MessageType.SLEEP, MessageClassifier.classify(in, CTX).type());
		}

		@Test
		void commandBlockAndScreenshotAndErrors() {
			assertEquals(MessageType.COMMAND_BLOCK,
					system(Component.translatable("chat.type.admin", Component.literal("@"), Component.literal("Set the time"))).type());
			assertEquals(MessageType.SCREENSHOT,
					system(Component.translatable("screenshot.success", Component.literal("2026-11-18.png"))).type());
			assertEquals(MessageType.ERROR,
					system(Component.translatable("command.unknown.command").withStyle(ChatFormatting.RED)).type());
			// Second line of a command error: "tell @s hi<--[HERE]"
			assertEquals(MessageType.ERROR, system(Component.empty().withStyle(ChatFormatting.GRAY)
					.append(Component.literal("tell @s hi"))
					.append(Component.translatable("command.context.here").withStyle(ChatFormatting.RED))).type());
		}

		@Test
		void unrelatedKeysAreSystem() {
			// A death message mentions players but is neither chat nor join.
			ClassifiedMessage m = system(Component.translatable("death.attack.player", vanillaName("Steve"), vanillaName("Alex")));
			assertEquals(MessageType.SYSTEM, m.type());
		}
	}

	// ── 3. Server patterns ───────────────────────────────────────────────────

	@Nested
	class ServerPatterns {
		@Test
		void essentialsIncoming() {
			ClassifiedMessage m = system("[Steve -> me] are you there?");
			assertEquals(MessageType.WHISPER, m.type());
			assertEquals(Direction.INCOMING, m.direction());
			assertEquals("Steve", m.senderName());
			assertEquals("are you there?", m.body().getString());
		}

		@Test
		void essentialsOutgoingRussian() {
			ClassifiedMessage m = system("[я -> Alex] привет");
			assertEquals(Direction.OUTGOING, m.direction());
			assertEquals("Alex", m.receiverName());
			assertEquals("привет", m.body().getString());
		}

		@Test
		void essentialsWithLocalNickAndRanks() {
			ClassifiedMessage m = system("[[VIP] Notch → " + ME + "] gg");
			assertEquals(Direction.INCOMING, m.direction());
			assertEquals("Notch", m.senderName());
		}

		@Test
		void hypixelFromAndTo() {
			ClassifiedMessage from = system("From [MVP+] Notch: hello");
			assertEquals(Direction.INCOMING, from.direction());
			assertEquals("Notch", from.senderName());
			assertEquals("hello", from.body().getString());

			ClassifiedMessage to = system("To [VIP] Jeb_: hi back");
			assertEquals(Direction.OUTGOING, to.direction());
			assertEquals("Jeb_", to.receiverName());
		}

		@Test
		void pluginChatFormatWithOnlinePlayer() {
			ClassifiedMessage m = system("[Admin] Steve » hello everyone");
			assertEquals(MessageType.PLAYER_CHAT, m.type());
			assertEquals("Steve", m.senderName());
			assertEquals("hello everyone", m.body().getString());
		}

		@Test
		void pluginChatFormatWithUnknownNameIsNotChat() {
			assertEquals(MessageType.SYSTEM, system("[Info] Restart » in 5 minutes").type());
			assertEquals(MessageType.SYSTEM, system("Error: something broke").type());
		}

		@Test
		void angleBracketsLiteral() {
			ClassifiedMessage m = system("<Alex> sup");
			assertEquals(MessageType.PLAYER_CHAT, m.type());
			assertEquals("Alex", m.senderName());
		}

		@Test
		void bugSentenceStartingWithYouIsNotOutgoingWhisper() {
			// Old code: anything starting with "вы "/"you " was an outgoing whisper.
			assertEquals(MessageType.SYSTEM, system("Вы все молодцы: отлично сыграли").type());
			assertEquals(MessageType.SYSTEM, system("You have been teleported: spawn").type());
		}

		@Test
		void bugKeywordsInServerTextAreNotGuessed() {
			// Old code: "left" → JOIN_LEAVE, "bed" → SLEEP, "advancement" → ACHIEVEMENT.
			assertEquals(MessageType.SYSTEM, system("You left the queue").type());
			assertEquals(MessageType.SYSTEM, system("Your bed has been set").type());
			assertEquals(MessageType.SYSTEM, system("New advancement pack installed").type());
		}

		@Test
		void joinPlus() {
			assertEquals(MessageType.JOIN_LEAVE, system("[+] Steve").type());
			assertEquals(MessageType.JOIN_LEAVE, system("[-] Steve").type());
		}

		@Test
		void legacyCodesAreIgnoredForMatchingAndBodyKeepsStyle() {
			Component c = Component.literal("§7[§cSteve §7-> §cme§7] ")
					.append(Component.literal("click me").withStyle(Style.EMPTY
							.withColor(ChatFormatting.AQUA)
							.withClickEvent(new ClickEvent.RunCommand("/spawn"))));
			ClassifiedMessage m = system(c);
			assertEquals(MessageType.WHISPER, m.type());
			assertEquals("Steve", m.senderName());
			assertEquals("click me", m.body().getString());
			Style[] bodyStyle = new Style[1];
			m.body().visit((style, text) -> {
				if (!text.isBlank()) bodyStyle[0] = style;
				return java.util.Optional.empty();
			}, Style.EMPTY);
			assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), bodyStyle[0].getColor());
			assertInstanceOf(ClickEvent.RunCommand.class, bodyStyle[0].getClickEvent());
		}

		@Test
		void userPatternsRunFirst() {
			ServerPattern custom = ServerPattern.compile(
					new ServerPattern.Config("mine", "^✉ (?<from>\\w+) » (?<to>\\w+): (?<body>.*)$", "WHISPER"));
			MessageClassifier.Context ctx = new MessageClassifier.Context(ME, n -> true, List.of(custom));
			ClassifiedMessage m = MessageClassifier.classify(
					IncomingMessage.system(Component.literal("✉ Steve » you: hi"), false), ctx);
			assertEquals(MessageType.WHISPER, m.type());
			assertEquals(Direction.INCOMING, m.direction());
			assertEquals("Steve", m.senderName());
		}

		@Test
		void badUserPatternIsRejectedWithReason() {
			IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
					ServerPattern.compile(new ServerPattern.Config("x", "([", "WHISPER")));
			assertTrue(e.getMessage().startsWith("bad regex"));
			assertThrows(IllegalArgumentException.class, () ->
					ServerPattern.compile(new ServerPattern.Config("x", ".*", "NOPE")));
		}
	}

	// ── 4. Error styling and fallback ────────────────────────────────────────

	@Nested
	class Styling {
		@Test
		void allRedIsError() {
			assertEquals(MessageType.ERROR, system(red("You don't have permission to do that.")).type());
			assertEquals(MessageType.ERROR, system("§cYou don't have permission.").type());
		}

		@Test
		void partlyRedIsNotError() {
			Component c = Component.literal("Server: ").withStyle(ChatFormatting.GOLD).append(red("restart soon"));
			assertEquals(MessageType.SYSTEM, system(c).type());
		}

		@Test
		void redPlayerChatIsStillChat() {
			ClassifiedMessage m = chat("minecraft:chat", vanillaName("Steve"), null, "Steve", "angry text");
			assertEquals(MessageType.PLAYER_CHAT, m.type());
		}
	}

	// ── Components helpers ───────────────────────────────────────────────────

	@Nested
	class Helpers {
		@Test
		void nickShape() {
			assertTrue(Components.isNick("Jeb_"));
			assertFalse(Components.isNick("ab"));
			assertFalse(Components.isNick("___"));
			assertFalse(Components.isNick("Стив"));
		}

		@Test
		void visiblyEmpty() {
			assertTrue(Components.isVisiblyEmpty(Component.literal("§r&7  ")));
			assertFalse(Components.isVisiblyEmpty(Component.literal("&7x")));
		}

		@Test
		void sliceKeepsStyles() {
			Component c = Component.literal("abc").withStyle(ChatFormatting.RED)
					.append(Component.literal("def").withStyle(ChatFormatting.BLUE));
			Component s = Components.sliceFrom(c, 2);
			assertEquals("cdef", s.getString());
		}
	}
}

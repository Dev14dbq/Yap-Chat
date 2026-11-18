package com.ddev14.yapchat.gametest;

import com.ddev14.yapchat.YapChatModClient;
import com.ddev14.yapchat.chat.ChatMessageEntry;
import com.ddev14.yapchat.chat.MessageType;
import com.ddev14.yapchat.pipeline.Direction;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Real client, real world: sends every kind of message through the actual game and checks
 * the type, direction and sender Yap Chat assigned. Fails the run on the first mismatch.
 */
public class YapChatClientGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("yap-chat-gametest");

	private record Expect(String what, String command, boolean chat, MessageType type, Direction direction, String contains) {}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		// Settings opened from the title screen (Mod Menu) must not crash: no world, item data not bound yet.
		ctx.runOnClient(mc -> mc.setScreen(new com.ddev14.yapchat.ui.YapChatSettingsScreen(mc.screen)));
		for (int i = 0; i < 7; i++) {
			final int tab = i;
			ctx.runOnClient(mc -> ((com.ddev14.yapchat.ui.YapChatSettingsScreen) mc.screen).showTab(tab));
			ctx.waitTicks(3);
		}
		ctx.runOnClient(mc -> ((com.ddev14.yapchat.ui.YapChatSettingsScreen) mc.screen).showTab(0));
		ctx.waitTicks(3);
		ctx.takeScreenshot("yap-chat-settings-title");
		ctx.runOnClient(mc -> ((com.ddev14.yapchat.ui.YapChatSettingsScreen) mc.screen).onClose());
		ctx.waitTicks(3);

		try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(ui -> ui.setAllowCommands(true)).create()) {
			sp.getClientLevel().waitForChunksRender();
			String me = ctx.computeOnClient(mc -> mc.player.getGameProfile().name());
			sp.getServer().runCommand("gamerule show_advancement_messages true");
			ctx.waitTicks(2);
			ctx.waitTicks(5);

			List<Expect> cases = List.of(
					new Expect("player chat with trigger words", "I left my bed, no error", true,
							MessageType.PLAYER_CHAT, Direction.NONE, "I left my bed"),
					new Expect("vanilla /tell outgoing", "tell @s secret", false,
							MessageType.WHISPER, Direction.OUTGOING, "secret"),
					new Expect("vanilla /tell incoming", null, false,
							MessageType.WHISPER, Direction.INCOMING, "secret"),
					new Expect("/say", "say hello all", false,
							MessageType.PLAYER_CHAT, Direction.NONE, "hello all"),
					new Expect("essentials-style whisper", "tellraw @s \"[Steve -> me] plugin pm\"", false,
							MessageType.WHISPER, Direction.INCOMING, "plugin pm"),
					new Expect("plain system text with trigger words", "tellraw @s \"You left the queue, bed error\"", false,
							MessageType.SYSTEM, Direction.NONE, "You left the queue"),
					new Expect("red text", "tellraw @s {\"text\":\"No permission\",\"color\":\"red\"}", false,
							MessageType.ERROR, Direction.NONE, "No permission"),
					new Expect("wrapped vanilla join", "tellraw @s {\"text\":\"\",\"extra\":[{\"translate\":\"multiplayer.player.joined\",\"with\":[\"Alex\"]}]}", false,
							MessageType.JOIN_LEAVE, Direction.NONE, "Alex"),
					new Expect("advancement", "advancement grant @s only minecraft:story/mine_stone", false,
							MessageType.ACHIEVEMENT, Direction.NONE, null),
					new Expect("unknown command", "thiscommanddoesnotexist", false,
							MessageType.ERROR, Direction.NONE, null)
			);

			List<String> failures = new ArrayList<>();
			int before = history().size();
			for (Expect e : cases) {
				if (e.command() != null) {
					if (e.chat()) {
						ctx.runOnClient(mc -> mc.getConnection().sendChat(e.command()));
					} else {
						ctx.runOnClient(mc -> mc.getConnection().sendCommand(e.command()));
					}
					ctx.waitTicks(2);
					ctx.waitTicks(2);
					ctx.waitTicks(5);
				}
				List<ChatMessageEntry> h = history();
				ChatMessageEntry found = null;
				for (int i = before; i < h.size(); i++) {
					ChatMessageEntry entry = h.get(i);
					if (entry.type == e.type() && entry.direction == e.direction()
							&& (e.contains() == null || entry.message.getString().contains(e.contains()))) {
						found = entry;
						before = i + 1;
						break;
					}
				}
				if (found == null) {
					StringBuilder sb = new StringBuilder("FAIL " + e.what() + ": expected " + e.type() + "/" + e.direction() + ", history since last match:");
					for (int i = before; i < h.size(); i++) {
						ChatMessageEntry x = h.get(i);
						sb.append("\n    ").append(x.type).append('/').append(x.direction).append(" sender=").append(x.senderName)
								.append(" receiver=").append(x.receiverName).append(" | ").append(x.message.getString());
					}
					failures.add(sb.toString());
				} else {
					if (e.what().equals("essentials-style whisper")) ctx.takeScreenshot("yap-chat-players");
					LOG.info("OK   {} -> {}/{} sender={} receiver={} | {}", e.what(), found.type, found.direction,
							found.senderName, found.receiverName, found.message.getString());
				}
			}

			ChatMessageEntry outgoing = history().stream()
					.filter(x -> x.type == MessageType.WHISPER && x.direction == Direction.OUTGOING).findFirst().orElse(null);
			if (outgoing != null && !me.equalsIgnoreCase(outgoing.receiverName)) {
				failures.add("FAIL /tell @s outgoing receiver: expected " + me + ", got " + outgoing.receiverName);
			}

			ctx.takeScreenshot("yap-chat-overlay");

			// Chat bubble over our own head in third person.
			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			ctx.runOnClient(mc -> mc.getConnection().sendChat("bubble check, hello!"));
			ctx.waitTicks(10);
			ctx.takeScreenshot("yap-chat-bubble");
			ctx.runOnClient(mc -> com.ddev14.yapchat.YapChatModClient.getHud().setBubbleStyle(com.ddev14.yapchat.bubble.BubbleStyle.WHITE));
			ctx.waitTicks(2);
			ctx.takeScreenshot("yap-chat-bubble-white");
			ctx.runOnClient(mc -> com.ddev14.yapchat.YapChatModClient.getHud().setBubbleStyle(com.ddev14.yapchat.bubble.BubbleStyle.TOOLTIP));
			ctx.waitTicks(2);
			ctx.takeScreenshot("yap-chat-bubble-tooltip");
			ctx.runOnClient(mc -> com.ddev14.yapchat.YapChatModClient.getHud().setBubbleStyle(com.ddev14.yapchat.bubble.BubbleStyle.DARK));
			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));

			// "Reset all" resets after two clicks with a real mouse.
			ctx.runOnClient(mc -> com.ddev14.yapchat.YapChatModClient.getHud().setDisplayTimeSeconds(33));
			ctx.runOnClient(mc -> mc.setScreen(new com.ddev14.yapchat.ui.YapChatSettingsScreen(null)));
			ctx.waitTicks(3);
			double[] pos = ctx.computeOnClient(mc -> {
				int[] c = ((com.ddev14.yapchat.ui.YapChatSettingsScreen) mc.screen).resetButtonCenter();
				double sc = mc.getWindow().getGuiScale();
				return new double[] {c[0] * sc, c[1] * sc};
			});
			ctx.getInput().setCursorPos(pos[0], pos[1]);
			ctx.waitTicks(2);
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(3);
			ctx.takeScreenshot("yap-chat-reset-armed");
			ctx.getInput().pressMouse(0);
			ctx.waitTicks(3);
			int afterReset = ctx.computeOnClient(mc -> com.ddev14.yapchat.YapChatModClient.getHud().getDisplayTimeSeconds());
			if (afterReset != 5) failures.add("FAIL Reset all by two clicks: show time is " + afterReset + ", expected 5");
			ctx.runOnClient(mc -> mc.setScreen(null));

			// Clickable choices like Hypixel's NPC options: a click type the mod used to ignore
			// (show_dialog), sent twice so the repeats are merged into one "x2" box.
			String choice = "tellraw @a [\"\",{\"text\":\"Select an option: \",\"color\":\"yellow\"},"
					+ "{\"text\":\"[Yeah]\",\"color\":\"green\",\"click_event\":{\"action\":\"show_dialog\",\"dialog\":\"minecraft:server_links\"}}]";
			ctx.runOnClient(mc -> {
				com.ddev14.yapchat.YapChatModClient.getHud().clearChat();
				com.ddev14.yapchat.YapChatModClient.getHud().setAntiSpamEnabled(true);
			});
			sp.getServer().runCommand(choice);
			sp.getServer().runCommand(choice);
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
			ctx.waitTicks(5);
			double[] choicePos = ctx.computeOnClient(mc -> {
				com.ddev14.yapchat.chat.MessageBounds b = com.ddev14.yapchat.YapChatModClient.getHud().lastMessageBounds();
				if (b == null || !b.component.getString().contains("Select an option")) return null;
				int x = b.textX + mc.font.width("Select an option: ") + mc.font.width("[Yeah]") / 2;
				int y = b.textY + mc.font.lineHeight / 2;
				double sc = mc.getWindow().getGuiScale();
				return new double[] {x * sc, y * sc};
			});
			int repeats = history().get(history().size() - 1).repeatCount;
			if (choicePos == null) {
				failures.add("FAIL clickable choice: message was not drawn");
			} else {
				ctx.getInput().setCursorPos(choicePos[0], choicePos[1]);
				ctx.waitTicks(2);
				ctx.takeScreenshot("yap-chat-click-choice");
				ctx.getInput().pressMouse(0);
				ctx.waitTicks(5);
				String opened = ctx.computeOnClient(mc -> mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
				LOG.info("Clickable choice (x{}): click opened {}", repeats, opened);
				if (!opened.contains("Dialog")) failures.add("FAIL clickable choice (x" + repeats + "): click opened " + opened + ", expected a dialog");
			}
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(2);

			// Hover text on a message is shown (screenshot check).
			sp.getServer().runCommand("tellraw @a [\"\",{\"text\":\"Hover me: \",\"color\":\"yellow\"},"
					+ "{\"text\":\"[Stats]\",\"color\":\"aqua\",\"hover_event\":{\"action\":\"show_text\",\"value\":\"Hover text works\"}}]");
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
			ctx.waitTicks(5);
			double[] hoverPos = ctx.computeOnClient(mc -> {
				com.ddev14.yapchat.chat.MessageBounds b = com.ddev14.yapchat.YapChatModClient.getHud().lastMessageBounds();
				if (b == null || !b.component.getString().contains("Hover me")) return null;
				double sc = mc.getWindow().getGuiScale();
				return new double[] {(b.textX + mc.font.width("Hover me: ") + 8) * sc, (b.textY + 4) * sc};
			});
			if (hoverPos == null) {
				failures.add("FAIL hover text: message was not drawn");
			} else {
				ctx.getInput().setCursorPos(hoverPos[0], hoverPos[1]);
				ctx.waitTicks(3);
				ctx.takeScreenshot("yap-chat-hover-text");
			}
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(2);

			// Vanilla "Chat: Commands Only" hides player chat.
			ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.SYSTEM));
			ctx.runOnClient(mc -> mc.getConnection().sendChat("should stay hidden"));
			ctx.waitTicks(2);
			ctx.waitTicks(2);
			ctx.waitTicks(3);
			boolean leaked = history().stream().anyMatch(en -> en.message.getString().contains("should stay hidden"));
			if (leaked) failures.add("FAIL chat visibility Commands Only: player chat was still shown");
			ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL));

			// Vanilla "Chat Settings" must open our screen instead.
			ctx.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.options.ChatOptionsScreen(null, mc.options)));
			ctx.waitTicks(3);
			boolean replaced = ctx.computeOnClient(mc -> mc.screen instanceof com.ddev14.yapchat.ui.YapChatSettingsScreen);
			if (!replaced) failures.add("FAIL vanilla Chat Settings was not replaced by the Yap Chat screen");
			for (int i = 0; i < 7; i++) {
				final int tab = i;
				ctx.runOnClient(mc -> {
					if (mc.screen instanceof com.ddev14.yapchat.ui.YapChatSettingsScreen s) s.showTab(tab);
				});
				ctx.waitTicks(i == 1 ? 20 : 3);
				ctx.takeScreenshot("yap-chat-settings-" + tab);
			}
			// Bigger GUI area: info panel next to the list.
			ctx.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeGui(); });
			for (int tab : new int[] {1, 3, 4}) {
				ctx.runOnClient(mc -> {
					if (mc.screen instanceof com.ddev14.yapchat.ui.YapChatSettingsScreen s) s.showTab(tab);
				});
				ctx.waitTicks(20);
				ctx.takeScreenshot("yap-chat-settings-wide-" + tab);
			}
			ctx.runOnClient(mc -> { mc.options.guiScale().set(0); mc.resizeGui(); mc.setScreen(null); });

			if (!failures.isEmpty()) {
				failures.forEach(LOG::error);
				throw new AssertionError(failures.size() + " message(s) classified wrong, see log");
			}
			LOG.info("All {} in-game message checks passed", cases.size());
		}
	}

	private static List<ChatMessageEntry> history() {
		return YapChatModClient.getHud().getHistorySnapshot();
	}
}

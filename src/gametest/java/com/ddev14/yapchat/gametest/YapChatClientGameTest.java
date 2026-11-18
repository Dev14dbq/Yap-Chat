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
		ctx.runOnClient(mc -> mc.gui.setScreen(new com.ddev14.yapchat.ui.YapChatSettingsScreen(mc.gui.screen())));
		for (int i = 0; i < 7; i++) {
			final int tab = i;
			ctx.runOnClient(mc -> ((com.ddev14.yapchat.ui.YapChatSettingsScreen) mc.gui.screen()).showTab(tab));
			ctx.waitTicks(3);
		}
		ctx.runOnClient(mc -> ((com.ddev14.yapchat.ui.YapChatSettingsScreen) mc.gui.screen()).showTab(0));
		ctx.waitTicks(3);
		ctx.takeScreenshot("yap-chat-settings-title");
		ctx.runOnClient(mc -> ((com.ddev14.yapchat.ui.YapChatSettingsScreen) mc.gui.screen()).onClose());
		ctx.waitTicks(3);

		try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(ui -> ui.setAllowCommands(true)).create()) {
			sp.getConnection().waitForChunksRender();
			String me = ctx.computeOnClient(mc -> mc.player.getGameProfile().name());
			sp.getServer().runCommand("gamerule show_advancement_messages true");
			sp.getConnection().waitForClientboundPackets();
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
					sp.getConnection().waitForServerboundPackets();
					sp.getConnection().waitForClientboundPackets();
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
			ctx.runOnClient(mc -> mc.gui.setScreen(new com.ddev14.yapchat.ui.YapChatSettingsScreen(null)));
			ctx.waitTicks(3);
			double[] pos = ctx.computeOnClient(mc -> {
				int[] c = ((com.ddev14.yapchat.ui.YapChatSettingsScreen) mc.gui.screen()).resetButtonCenter();
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
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

			// Vanilla "Chat Settings" must open our screen instead.
			ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.options.ChatOptionsScreen(null, mc.options)));
			ctx.waitTicks(3);
			boolean replaced = ctx.computeOnClient(mc -> mc.gui.screen() instanceof com.ddev14.yapchat.ui.YapChatSettingsScreen);
			if (!replaced) failures.add("FAIL vanilla Chat Settings was not replaced by the Yap Chat screen");
			for (int i = 0; i < 7; i++) {
				final int tab = i;
				ctx.runOnClient(mc -> {
					if (mc.gui.screen() instanceof com.ddev14.yapchat.ui.YapChatSettingsScreen s) s.showTab(tab);
				});
				ctx.waitTicks(i == 1 ? 20 : 3);
				ctx.takeScreenshot("yap-chat-settings-" + tab);
			}
			// Bigger GUI area: info panel next to the list.
			ctx.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeGui(); });
			for (int tab : new int[] {1, 3, 4}) {
				ctx.runOnClient(mc -> {
					if (mc.gui.screen() instanceof com.ddev14.yapchat.ui.YapChatSettingsScreen s) s.showTab(tab);
				});
				ctx.waitTicks(20);
				ctx.takeScreenshot("yap-chat-settings-wide-" + tab);
			}
			ctx.runOnClient(mc -> { mc.options.guiScale().set(0); mc.resizeGui(); mc.gui.setScreen(null); });

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

package com.ddev14.yapchat.autotest;

import com.ddev14.yapchat.YapChatModClient;
import com.ddev14.yapchat.bubble.BubbleStyle;
import com.ddev14.yapchat.chat.ChatMessageEntry;
import com.ddev14.yapchat.chat.MessageType;
import com.ddev14.yapchat.compat.Compat;
import com.ddev14.yapchat.pipeline.Direction;
import com.ddev14.yapchat.ui.YapChatSettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Dev-only in-game check for Minecraft versions without Fabric's client gametest API.
 * Started with {@code ./gradlew runAutotest}: opens the settings screen on the title screen,
 * creates a world, sends every kind of message, checks how Yap Chat classified them, takes
 * screenshots (run-autotest/screenshots) and quits. The result is the last "AUTOTEST" log line.
 */
public class YapChatAutoTest implements ClientModInitializer {
	static final Logger LOG = LogManager.getLogger("yap-chat-autotest");

	private record Expect(String what, String command, boolean chat, MessageType type, Direction direction, String contains) {}

	private final List<BooleanSupplier> steps = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();
	private int step, wait, shots;
	private int before;

	@Override
	public void onInitializeClient() {
		if (!"1".equals(System.getProperty("yap.autotest"))) return;
		build();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
	}

	private void tick() {
		if (wait > 0) {
			wait--;
			return;
		}
		if (step >= steps.size()) return;
		try {
			if (steps.get(step).getAsBoolean()) step++;
		} catch (Throwable t) {
			LOG.error("AUTOTEST step {} crashed", step, t);
			failures.add("step " + step + " crashed: " + t);
			step = steps.size() - 1; // jump to the report
		}
	}

	private void then(Runnable r) { steps.add(() -> { r.run(); return true; }); }
	private void until(BooleanSupplier c) { steps.add(c); }
	private void sleep(int ticks) { steps.add(() -> { wait = ticks; return true; }); }

	private void shot(String name) {
		shot(name, true);
	}

	/** Screenshot; in-world shots first close anything a stray click may have opened. */
	private void shot(String name, boolean keepScreen) {
		if (!keepScreen) then(() -> { if (mc().level != null && mc().screen != null) Compat.setScreen(null); });
		sleep(2);
		then(() -> AutoTestCompat.screenshot(String.format("%02d_%s", shots++, name)));
		sleep(2);
	}

	private static Minecraft mc() { return Minecraft.getInstance(); }

	private static List<ChatMessageEntry> history() {
		return YapChatModClient.getHud().getHistorySnapshot();
	}

	private void build() {
		until(() -> mc().screen instanceof TitleScreen);
		then(() -> mc().options.pauseOnLostFocus = false);
		sleep(40);
		// settings from the title screen (as from Mod Menu): no world yet
		then(() -> Compat.setScreen(new YapChatSettingsScreen(mc().screen)));
		for (int i = 0; i < 6; i++) {
			final int tab = i;
			then(() -> ((YapChatSettingsScreen) mc().screen).showTab(tab));
			sleep(5);
		}
		then(() -> ((YapChatSettingsScreen) mc().screen).showTab(0));
		shot("settings-title");
		then(() -> mc().screen.onClose());
		sleep(5);

		// world
		then(AutoTestCompat::createWorld);
		until(() -> mc().level != null && mc().player != null && mc().screen == null);
		sleep(60);
		then(AutoTestCompat::opSelf);
		sleep(10);
		then(() -> before = history().size());

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
						MessageType.ERROR, Direction.NONE, null));
		for (Expect e : cases) {
			if (e.command() != null) {
				then(() -> {
					if (e.chat()) AutoTestCompat.sendChat(e.command());
					else AutoTestCompat.sendCommand(e.command());
				});
				sleep(15);
			}
			then(() -> check(e));
		}
		shot("overlay", false);

		// chat open: full history
		then(() -> Compat.setScreen(new ChatScreen("")));
		shot("chat-open");
		then(() -> Compat.setScreen(null));

		// bubbles over our own head in third person
		then(() -> mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		then(() -> AutoTestCompat.sendChat("bubble check, hello!"));
		sleep(10);
		shot("bubble-dark", false);
		then(() -> YapChatModClient.getHud().setBubbleStyle(BubbleStyle.WHITE));
		shot("bubble-white", false);
		then(() -> YapChatModClient.getHud().setBubbleStyle(BubbleStyle.TOOLTIP));
		shot("bubble-tooltip", false);
		then(() -> YapChatModClient.getHud().setBubbleStyle(BubbleStyle.NAMETAG));
		shot("bubble-nametag", false);
		then(() -> YapChatModClient.getHud().setBubbleStyle(BubbleStyle.DARK));
		then(() -> mc().options.setCameraType(CameraType.FIRST_PERSON));

		// six positions
		for (int p = 1; p < 6; p++) {
			final int pos = p;
			then(() -> YapChatModClient.getHud().setChatPosition(pos));
			then(() -> AutoTestCompat.sendCommand("tellraw @s \"position " + pos + "\""));
			sleep(5);
			shot("position-" + pos, false);
		}
		then(() -> YapChatModClient.getHud().setChatPosition(0));

		// vanilla "Chat Settings" opens our screen
		then(() -> Compat.setScreen(AutoTestCompat.vanillaChatOptions()));
		sleep(3);
		then(() -> {
			if (!(mc().screen instanceof YapChatSettingsScreen)) failures.add("vanilla Chat Settings was not replaced");
		});
		for (int i = 0; i < 6; i++) {
			final int tab = i;
			then(() -> {
				if (mc().screen instanceof YapChatSettingsScreen s) s.showTab(tab);
			});
			sleep(tab == 1 ? 20 : 3);
			shot("settings-" + tab);
		}
		// "Reset all" needs two clicks
		then(() -> YapChatModClient.getHud().setDisplayTimeSeconds(33));
		then(() -> AutoTestCompat.click(((YapChatSettingsScreen) mc().screen).resetButtonCenter()));
		shot("reset-armed");
		then(() -> AutoTestCompat.click(((YapChatSettingsScreen) mc().screen).resetButtonCenter()));
		sleep(3);
		then(() -> {
			int t = YapChatModClient.getHud().getDisplayTimeSeconds();
			if (t != 5) failures.add("Reset all by two clicks: show time " + t + ", expected 5");
		});
		// item picker popover
		then(() -> ((YapChatSettingsScreen) mc().screen).showTab(2));
		sleep(3);
		then(() -> Compat.setScreen(null));

		then(() -> {
			if (failures.isEmpty()) LOG.info("AUTOTEST PASSED: {} message checks, {} screenshots", cases.size(), shots);
			else {
				failures.forEach(f -> LOG.error("AUTOTEST FAIL {}", f));
				LOG.error("AUTOTEST FAILED: {} problem(s)", failures.size());
			}
		});
		sleep(10);
		then(() -> mc().stop());
	}

	private void check(Expect e) {
		List<ChatMessageEntry> h = history();
		for (int i = before; i < h.size(); i++) {
			ChatMessageEntry entry = h.get(i);
			if (entry.type == e.type() && entry.direction == e.direction()
					&& (e.contains() == null || entry.message.getString().contains(e.contains()))) {
				before = i + 1;
				LOG.info("AUTOTEST OK   {} -> {}/{} sender={} receiver={} | {}", e.what(), entry.type, entry.direction,
						entry.senderName, entry.receiverName, entry.message.getString());
				return;
			}
		}
		StringBuilder sb = new StringBuilder(e.what() + ": expected " + e.type() + "/" + e.direction() + ", got:");
		for (int i = before; i < h.size(); i++) {
			ChatMessageEntry x = h.get(i);
			sb.append("\n    ").append(x.type).append('/').append(x.direction).append(" sender=").append(x.senderName)
					.append(" | ").append(x.message.getString());
		}
		failures.add(sb.toString());
	}
}

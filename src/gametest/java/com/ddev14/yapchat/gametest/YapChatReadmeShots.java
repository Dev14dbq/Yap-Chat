package com.ddev14.yapchat.gametest;

import com.ddev14.yapchat.YapChatModClient;
import com.ddev14.yapchat.bubble.BubbleStyle;
import com.ddev14.yapchat.ui.YapChatSettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/**
 * Screenshots for the README. Runs only with YAP_SHOTS=1:
 * {@code YAP_SHOTS=1 ./gradlew runClientGameTest}
 */
public class YapChatReadmeShots implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!"1".equals(System.getenv("YAP_SHOTS"))) return;

		ctx.getInput().resizeWindow(1600, 900);
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(3);
			mc.resizeGui();
			YapChatModClient.getHud().resetSettings();
			YapChatModClient.getHud().setDisplayTimeSeconds(60);
		});

		try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(ui -> ui.setAllowCommands(true)).create()) {
			sp.getClientLevel().waitForChunksRender();
			sp.getServer().runCommand("time set 1000");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamerule show_advancement_messages true");
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				mc.player.setYRot(30f);
				mc.player.setXRot(-5f);
			});
			ctx.waitTicks(10);

			// 1. The chat: player chat, a system line, an advancement, an error, more chat.
			chat(ctx, "Hi! The chat looks a lot nicer now");
			command(ctx, "seed");
			command(ctx, "advancement grant @s only minecraft:story/mine_stone");
			command(ctx, "gamemode");
			chat(ctx, "Every type gets its own icon and color");
			ctx.waitTicks(120); // let the advancement toast leave
			ctx.takeScreenshot("readme-chat");

			// 2. Bubbles in three styles, third person from the front.
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.runOnClient(mc -> YapChatModClient.getHud().setDisplayTimeSeconds(1));
			ctx.waitTicks(40); // let the chat fade so the bubble is clear
			for (BubbleStyle style : new BubbleStyle[] {BubbleStyle.DARK, BubbleStyle.WHITE, BubbleStyle.TOOLTIP}) {
				ctx.runOnClient(mc -> YapChatModClient.getHud().setBubbleStyle(style));
				chat(ctx, "Hey! This is a chat bubble");
				ctx.waitTicks(20);
				ctx.takeScreenshot("readme-bubble-" + style.name().toLowerCase());
			}
			ctx.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				YapChatModClient.getHud().setBubbleStyle(BubbleStyle.WHITE);
			});

			// 3. The settings screen, at GUI scale 2 so the preview column is shown.
			ctx.getInput().resizeWindow(1600, 720);
			ctx.runOnClient(mc -> {
				mc.options.guiScale().set(2);
				mc.resizeGui();
				mc.setScreen(new YapChatSettingsScreen(null));
			});
			int[] tabs = {0, 1, 2, 3, 4};
			String[] names = {"general", "appearance", "messages", "bubbles", "minecraft"};
			for (int i = 0; i < tabs.length; i++) {
				final int tab = tabs[i];
				ctx.runOnClient(mc -> ((YapChatSettingsScreen) mc.screen).showTab(tab));
				ctx.waitTicks(10);
				if (tab == 1 || tab == 2) waitForFullPreview(ctx);
				ctx.takeScreenshot("readme-settings-" + names[i]);
			}
			// Accent color in another color.
			ctx.runOnClient(mc -> {
				YapChatModClient.getHud().setAccentColor("#4A7FE8");
				((YapChatSettingsScreen) mc.screen).showTab(5);
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("readme-settings-advanced-blue");
			ctx.runOnClient(mc -> ((YapChatSettingsScreen) mc.screen).showTab(1));
			waitForFullPreview(ctx);
			ctx.takeScreenshot("readme-settings-appearance-blue");
			ctx.runOnClient(mc -> {
				mc.setScreen(null);
				YapChatModClient.getHud().resetSettings();
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
		}
	}

	/** The preview loops every 5.5 s; all five messages are on screen between ~2.0 and ~2.9 s. */
	private static void waitForFullPreview(ClientGameTestContext ctx) {
		for (int i = 0; i < 200; i++) {
			long phase = ctx.computeOnClient(mc -> System.currentTimeMillis() % 5500);
			if (phase > 2200 && phase < 2700) return;
			ctx.waitTicks(1);
		}
	}

	private static void chat(ClientGameTestContext ctx, String text) {
		ctx.runOnClient(mc -> mc.getConnection().sendChat(text));
		ctx.waitTicks(6);
	}

	private static void command(ClientGameTestContext ctx, String cmd) {
		ctx.runOnClient(mc -> mc.getConnection().sendCommand(cmd));
		ctx.waitTicks(6);
	}
}

package com.ddev14.yapchat.autotest;

import com.ddev14.yapchat.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ChatOptionsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

import java.lang.reflect.Method;

/** Version-specific bits of the dev autotest (this file: 1.16.5). */
final class AutoTestCompat {
	private AutoTestCompat() {}

	static void screenshot(String name) {
		Minecraft mc = Minecraft.getInstance();
		Screenshot.grab(mc.gameDirectory, name + ".png", mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getMainRenderTarget(), c -> YapChatAutoTest.LOG.info("shot {}", name));
	}

	static void createWorld() {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreen(CreateWorldScreen.create(mc.screen));
		mc.execute(() -> {
			try {
				CreateWorldScreen s = (CreateWorldScreen) mc.screen;
				java.lang.reflect.Field f = CreateWorldScreen.class.getDeclaredField("commands");
				f.setAccessible(true);
				f.setBoolean(s, true);
				Method m = CreateWorldScreen.class.getDeclaredMethod("onCreate");
				m.setAccessible(true);
				m.invoke(s);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	static void opSelf() {
		Minecraft mc = Minecraft.getInstance();
		mc.getSingleplayerServer().execute(() ->
				mc.getSingleplayerServer().getPlayerList().op(mc.player.getGameProfile()));
	}

	static void sendChat(String text) {
		Minecraft.getInstance().player.chat(text);
	}

	static void sendCommand(String command) {
		Compat.sendCommand(command);
	}

	static Screen vanillaChatOptions() {
		return new ChatOptionsScreen(null, Minecraft.getInstance().options);
	}

	/** Left click at a GUI position on the current screen. */
	static void click(int[] pos) {
		Screen s = Minecraft.getInstance().screen;
		s.mouseClicked(pos[0], pos[1], 0);
		s.mouseReleased(pos[0], pos[1], 0);
	}
}

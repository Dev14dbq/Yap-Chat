package com.ddev14.yapchat.autotest;

import com.ddev14.yapchat.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ChatOptionsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

import java.lang.reflect.Method;

/** Version-specific bits of the dev autotest (this file: 1.20.1). */
final class AutoTestCompat {
	private AutoTestCompat() {}

	static void screenshot(String name) {
		Minecraft mc = Minecraft.getInstance();
		Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> YapChatAutoTest.LOG.info("shot {}", name));
	}

	static void createWorld() {
		Minecraft mc = Minecraft.getInstance();
		CreateWorldScreen.openFresh(mc, mc.screen);
		mc.execute(() -> {
			try {
				CreateWorldScreen s = (CreateWorldScreen) mc.screen;
				s.getUiState().setAllowCheats(true);
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
		Minecraft.getInstance().player.connection.sendChat(text);
	}

	static void sendCommand(String command) {
		Minecraft.getInstance().player.connection.sendCommand(command);
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

	/** Click event JSON for tellraw (this version's key names). */
	static String clickJson(String action, String value) {
		return "\"clickEvent\":{\"action\":\"" + action + "\",\"value\":\"" + value + "\"}";
	}

	static String hoverTextJson(String text) {
		return "\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"" + text + "\"}";
	}

	/** Moves the real mouse cursor to a GUI position, so hover tooltips are drawn. */
	static void moveMouse(int x, int y) {
		Minecraft mc = Minecraft.getInstance();
		double sc = mc.getWindow().getGuiScale();
		// An unfocused window ignores glfwSetCursorPos on macOS, so set the game's own copy too
		// (dev run only: the fields have their Mojang names here).
		try {
			for (String f : new String[] {"xpos", "ypos"}) {
				java.lang.reflect.Field field = net.minecraft.client.MouseHandler.class.getDeclaredField(f);
				field.setAccessible(true);
				field.setDouble(mc.mouseHandler, (f.equals("xpos") ? x : y) * sc);
			}
		} catch (ReflectiveOperationException e) {
			org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), x * sc, y * sc);
		}
	}

	static void setChatVisibility(net.minecraft.world.entity.player.ChatVisiblity v) {
		Minecraft.getInstance().options.chatVisibility().set(v);
	}
}

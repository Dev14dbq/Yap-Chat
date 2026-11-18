package com.ddev14.yapchat;

import com.ddev14.yapchat.chat.ChatMessageEntry;
import com.ddev14.yapchat.chat.MessageBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Map;

/**
 * Клики по ссылкам в чате и разворачивание повторов.
 */
public final class ChatHudClickDispatch {
	private ChatHudClickDispatch() {}

	public static boolean handleMessageClick(YapChatHud hud, double mouseX, double mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(com.ddev14.yapchat.compat.Compat.screen() instanceof ChatScreen)) {
			return false;
		}

		int mx = (int) mouseX;
		int my = (int) mouseY;

		MessageBounds collapsedBounds = hud.messageBounds.get(-1);
		if (collapsedBounds != null && collapsedBounds.contains(mx, my)) {
			hud.collapsedMessagesExpanded = !hud.collapsedMessagesExpanded;
			hud.messageBounds.clear();
			return true;
		}

		for (Map.Entry<Integer, MessageBounds> boundsEntry : hud.messageBounds.entrySet()) {
			int index = boundsEntry.getKey();
			MessageBounds bounds = boundsEntry.getValue();

			if (index == -1) {
				continue;
			}

			if (index < 1000 && bounds.contains(mx, my)) {
				int historyIndex = bounds.historyIndex;
				if (historyIndex >= 0 && historyIndex < hud.messageHistory.size()) {
					ChatMessageEntry entry = hud.messageHistory.get(historyIndex);
					if (entry != null) {
						Style style = styleIn(minecraft.font, bounds, mx, my);
						if (style != null && clickStyle(minecraft, style)) {
							return true;
						}
						// Not on a clickable part of the text: the box itself reacts.
						if (entry.isAchievement() && entry.message != null) {
							hud.shouldOpenAdvancements = true;
							return true;
						}
						if (entry.repeatCount > 1) {
							entry.isExpanded = !entry.isExpanded;
							hud.messageBounds.clear();
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	/** Style of the text under the mouse in any drawn message, or null. */
	public static Style styleUnderMouse(YapChatHud hud, int mx, int my) {
		Font font = Minecraft.getInstance().font;
		for (Map.Entry<Integer, MessageBounds> e : hud.messageBounds.entrySet()) {
			if (e.getKey() < 0 || e.getKey() >= 1000) continue;
			MessageBounds b = e.getValue();
			if (!b.contains(mx, my)) continue;
			Style style = styleIn(font, b, mx, my);
			if (style != null) return style;
		}
		return null;
	}

	/** Style of the character under the mouse inside one message's text, or null. */
	private static Style styleIn(Font font, MessageBounds bounds, int mx, int my) {
		if (bounds.component == null || bounds.textX <= 0 || bounds.textY <= 0) return null;
		if (mx < bounds.textX || mx >= bounds.textX + bounds.maxTextWidth
				|| my < bounds.textY || my >= bounds.textY + bounds.height) return null;
		List<FormattedCharSequence> lines = font.split(bounds.component, bounds.maxTextWidth);
		int lineIndex = (my - bounds.textY) / font.lineHeight;
		if (lineIndex < 0 || lineIndex >= lines.size()) return null;
		return styleAtWidth(font, lines.get(lineIndex), mx - bounds.textX);
	}

	/**
	 * Hands the click to vanilla's chat screen, so every click type works the way it does
	 * without the mod: dialogs and custom actions (Hypixel NPC choices), command confirmation,
	 * shift-click insertion. Falls back to the mod's own handler if that fails.
	 */
	private static boolean clickStyle(Minecraft minecraft, Style style) {
		if (style.getClickEvent() == null && style.getInsertion() == null) return false;
		if (com.ddev14.yapchat.compat.Compat.screen() instanceof ChatScreen chatScreen) {
			try {
				return chatScreen.handleComponentClicked(style);
			} catch (RuntimeException | LinkageError e) {
				// fall through to the mod's own handler
			}
		}
		if (style.getClickEvent() == null) return false;
		handleClickEvent(style.getClickEvent());
		return true;
	}

	private static Style styleAtWidth(Font font, FormattedCharSequence line, int targetX) {
		final Style[] result = {null};
		final int[] px = {0};
		line.accept((index, style, codePoint) -> {
			int w = font.width(FormattedCharSequence.codepoint(codePoint, style)); // bold is wider
			px[0] += w;
			if (px[0] > targetX) {
				result[0] = style;
				return false;
			}
			return true;
		});
		return result[0];
	}

	/** Runs a click event of a chat message, like vanilla chat does. */
	public static void handleClickEvent(ClickEvent clickEvent) {
		if (clickEvent == null) return;

		Minecraft minecraft = Minecraft.getInstance();
		String value = clickEvent.getValue();
		switch (clickEvent.getAction()) {
			case RUN_COMMAND -> {
				if (minecraft.player != null) com.ddev14.yapchat.compat.Compat.sendCommand(value);
			}
			case SUGGEST_COMMAND -> {
				if (com.ddev14.yapchat.compat.Compat.screen() instanceof ChatScreen chatScreen) {
					String cmd = value.startsWith("/") ? value : "/" + value;
					chatScreen.sendMessage(cmd);
				}
			}
			case OPEN_URL -> {
				if (com.ddev14.yapchat.compat.Compat.chatLinks()) {
					try {
						java.awt.Desktop.getDesktop().browse(java.net.URI.create(value));
					} catch (Exception e) {
						minecraft.keyboardHandler.setClipboard(value);
					}
				}
			}
			case OPEN_FILE -> {
				try {
					java.io.File file = new java.io.File(value);
					if (file.exists()) java.awt.Desktop.getDesktop().open(file);
				} catch (Exception ignored) {
				}
			}
			case COPY_TO_CLIPBOARD -> minecraft.keyboardHandler.setClipboard(value);
			default -> { }
		}
	}
}

package com.ddev14.yapchat.render;

import com.ddev14.yapchat.YapChatHud;
import com.ddev14.yapchat.YapChatModClient;
import com.ddev14.yapchat.chat.ChatLayout;
import com.ddev14.yapchat.chat.ChatMessageEntry;
import com.ddev14.yapchat.chat.MessageBounds;
import com.ddev14.yapchat.chat.MessageType;
import com.ddev14.yapchat.chat.RuleEngine;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.Font;
import com.ddev14.yapchat.compat.Gfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Map;

/**
 * Размеры блока сообщения, фон и текст.
 */
public final class ChatMessageBoxPainter {
	private ChatMessageBoxPainter() {}

	/**
	 * Вычисляет X-координату блока сообщения в зависимости от позиции чата.
	 * 0=центр-низ, 1=центр-верх, 2=лево-верх, 3=лево-низ, 4=право-верх, 5=право-низ
	 */
	public static int calculateX(int screenWidth, int boxWidth, int chatPosition) {
		return switch (chatPosition) {
			case 2, 3 -> ChatLayout.SIDE_MARGIN;
			case 4, 5 -> screenWidth - boxWidth - ChatLayout.SIDE_MARGIN;
			default -> (screenWidth - boxWidth) / 2;
		};
	}

	public static int calculateMessageWidth(Font font, Component component) {
		int fullTextWidth = font.width(component);
		int avatarAreaWidth = ChatLayout.AVATAR_SIZE;
		int leftNonText = ChatLayout.HORIZONTAL_PADDING + avatarAreaWidth + 7;
		int rightNonText = ChatLayout.HORIZONTAL_PADDING;
		int totalNonTextWidth = leftNonText + rightNonText;
		int logicalTextWidth = Math.min(ChatLayout.MAX_BOX_WIDTH - totalNonTextWidth, fullTextWidth);
		return logicalTextWidth + totalNonTextWidth;
	}

	/**
	 * @param startY    при topAnchor=false — нижняя граница блока; при topAnchor=true — верхняя
	 * @param topAnchor true для позиций «сверху», false для позиций «снизу»
	 * @param chatOpen  true — чат открыт (включаем hover-tooltip через ActiveTextCollector)
	 * @param boundsKey ключ для записи MessageBounds в карту; Integer.MIN_VALUE — не записывать
	 * @param offsetX   дополнительный сдвиг по X (анимация)
	 * @param offsetY   дополнительный сдвиг по Y (анимация)
	 */
	public static int renderSingleMessage(Gfx guiGraphics, Font font, int screenWidth, int startY,
			ChatMessageEntry entry, float alpha, float backgroundOpacity,
			Map<Integer, MessageBounds> messageBounds, int chatPosition, boolean topAnchor,
			boolean chatOpen, int boundsKey, int offsetX, int offsetY) {
		Component component = entry.message;
		if (component == null || component.getString().isEmpty()) {
			return 0;
		}

		if (entry.repeatCount > 1 && !entry.isExpanded) {
			MutableComponent withSuffix = component.copy();
			MutableComponent suffix = com.ddev14.yapchat.compat.Texts.literal(" x" + entry.repeatCount)
					.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
			withSuffix.append(suffix);
			component = withSuffix;
		}

		int fullTextWidth = font.width(component);

		boolean isJoinMessage = entry.isJoinMessage();
		boolean whisperHeads = usesWhisperHeads(entry);
		int avatarAreaWidth = isJoinMessage ? 0
				: whisperHeads ? whisperHeadsWidth(font)
				: ChatLayout.AVATAR_SIZE;

		int leftNonText = isJoinMessage ? ChatLayout.HORIZONTAL_PADDING : (ChatLayout.HORIZONTAL_PADDING + avatarAreaWidth + 7);
		int rightNonText = ChatLayout.HORIZONTAL_PADDING;
		int totalNonTextWidth = leftNonText + rightNonText;

		int logicalTextWidth = Math.min(ChatLayout.MAX_BOX_WIDTH - totalNonTextWidth, fullTextWidth);

		int boxWidth = logicalTextWidth + totalNonTextWidth;
		int x = calculateX(screenWidth, boxWidth, chatPosition) + offsetX;

		int textAreaStartX = x + leftNonText;
		int maxTextWidth = logicalTextWidth;

		List<FormattedCharSequence> lines = font.split(component, maxTextWidth);
		if (lines.isEmpty()) {
			return 0;
		}

		int textHeight = font.lineHeight * lines.size();
		int contentHeight = isJoinMessage ? textHeight : Math.max(ChatLayout.AVATAR_SIZE, textHeight);
		int boxHeight = contentHeight + ChatLayout.VERTICAL_PADDING * 2;
		int y = (topAnchor ? startY : startY - boxHeight) + offsetY;

		int baseAlpha = (int) (backgroundOpacity * 255.0f);
		int animatedAlpha = (int) (baseAlpha * alpha);

		// Background color: config map → type default
		YapChatHud hudForBg = YapChatModClient.getHud();
		int bgBase = resolveBgColor(entry.type, hudForBg);
		guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, (animatedAlpha << 24) | bgBase);

		// Цветная вертикальная полоска-акцент для достижений/испытаний
		if (entry.type == MessageType.CHALLENGE || entry.type == MessageType.ACHIEVEMENT) {
			int accentRgb = entry.type == MessageType.CHALLENGE ? 0xAA00CC : 0x00AA22;
			guiGraphics.fill(x, y, x + 3, y + boxHeight, (animatedAlpha << 24) | accentRgb);
		}

		int avatarX = x + ChatLayout.HORIZONTAL_PADDING;
		int avatarY = y + (boxHeight - ChatLayout.AVATAR_SIZE) / 2;

		if (entry.isCollapsed) {
			ChatHudAvatarRenderer.renderClockIcon(guiGraphics, avatarX, avatarY, alpha);
		} else if (whisperHeads) {
			renderWhisperHeads(guiGraphics, font, entry, avatarX, avatarY, alpha);
		} else if (!isJoinMessage) {
			renderAvatar(guiGraphics, entry, avatarX, avatarY, alpha);
		}

		int textX = textAreaStartX;
		int totalTextHeight = font.lineHeight * lines.size();
		int avatarCenterY = avatarY + ChatLayout.AVATAR_SIZE / 2;
		int textY = avatarCenterY - totalTextHeight / 2;

		if (chatOpen) {
			// Открытый чат: используем ActiveTextCollector с TOOLTIP_AND_CURSOR,
			// чтобы движок автоматически показывал hover-эффекты (SHOW_TEXT, SHOW_ITEM,
			// SHOW_ENTITY) и менял курсор над кликабельным текстом.
			guiGraphics.hoverableLines(font, lines, textX, textY, alpha);
		} else {
			// Закрытый HUD-оверлей: просто рисуем текст с alpha-fade, hover не нужен.
			int textAlpha = (int) (255 * alpha);
			int textBaseColor = switch (entry.type) {
				case CHALLENGE   -> 0xFF55FF;
				case ACHIEVEMENT -> 0x55FF55;
				default          -> 0xFFFFFF;
			};
			int textColorWithAlpha = (textAlpha << 24) | textBaseColor;
			for (int i = 0; i < lines.size(); i++) {
				guiGraphics.text(font, lines.get(i), textX, textY + i * font.lineHeight,
						textColorWithAlpha, false);
			}
		}

		if (boundsKey != Integer.MIN_VALUE) {
			messageBounds.put(boundsKey, new MessageBounds(
					x, y, boxWidth, boxHeight, boundsKey,
					textX, textY, maxTextWidth, entry.message));
		}

		return boxHeight;
	}

	/**
	 * Resolves the background RGB color for a message type.
	 * Checks the config map first; falls back to built-in defaults.
	 */
	private static int resolveBgColor(MessageType type, YapChatHud hud) {
		if (hud != null && hud.bgColors != null) {
			String hex = hud.bgColors.get(type.name());
			if (hex != null) {
				Integer parsed = RuleEngine.parseHexColor(hex);
				if (parsed != null) return parsed;
			}
		}
		return switch (type) {
			case CHALLENGE   -> 0x1A0028;
			case ACHIEVEMENT -> 0x001A04;
			case ERROR       -> 0x1A0000;
			default          -> 0x000000;
		};
	}

	/**
	 * Icon left of the message. Order: icon from a rule → the other player's head for
	 * player chat and whispers → the configured icon of the message type.
	 */
	private static void renderAvatar(Gfx g, ChatMessageEntry entry, int x, int y, float alpha) {
		YapChatHud hud = YapChatModClient.getHud();
		if (entry.iconOverride != null) {
			ChatHudAvatarRenderer.renderIconById(g, x, y, alpha, entry.iconOverride, Items.STICK);
			return;
		}
		if (entry.type == MessageType.PLAYER_CHAT || entry.type == MessageType.WHISPER) {
			boolean outgoingWhisper = entry.type == MessageType.WHISPER
					&& entry.direction == com.ddev14.yapchat.pipeline.Direction.OUTGOING;
			PlayerInfo info = outgoingWhisper ? entry.receiverInfo : entry.senderInfo;
			String name = outgoingWhisper ? entry.receiverName : entry.senderName;
			if (info != null) {
				ChatHudAvatarRenderer.renderPlayerHead(g, x, y, info, alpha);
				return;
			}
			if (name != null && !name.isEmpty()) {
				ChatHudAvatarRenderer.renderPlayerHeadByName(g, x, y, name, alpha);
				return;
			}
		}
		switch (entry.type) {
			case CHALLENGE     -> ChatHudAvatarRenderer.renderIconById(g, x, y, alpha, hud != null ? hud.getIconChallenge() : null, Items.NETHERITE_INGOT);
			case ACHIEVEMENT   -> ChatHudAvatarRenderer.renderIconById(g, x, y, alpha, hud != null ? hud.getIconAchievement() : null, Items.EMERALD);
			case ERROR         -> ChatHudAvatarRenderer.renderIconById(g, x, y, alpha, hud != null ? hud.getIconError() : null, Items.BARRIER);
			case SLEEP         -> ChatHudAvatarRenderer.renderIconById(g, x, y, alpha, hud != null ? hud.getIconSleep() : null, Items.RED_BED);
			case SCREENSHOT    -> ChatHudAvatarRenderer.renderCameraIcon(g, x, y, alpha);
			case COMMAND_BLOCK -> ChatHudAvatarRenderer.renderIconById(g, x, y, alpha, hud != null ? hud.getIconCommandBlock() : null, Items.COMMAND_BLOCK);
			case WHISPER       -> ChatHudAvatarRenderer.renderIconById(g, x, y, alpha, hud != null ? hud.getIconWhisper() : null, Items.PAPER);
			case JOIN_LEAVE    -> ChatHudAvatarRenderer.renderIconById(g, x, y, alpha, hud != null ? hud.getIconJoinLeave() : null, Items.OAK_DOOR);
			default            -> ChatHudAvatarRenderer.renderIconById(g, x, y, alpha, hud != null ? hud.getIconSystem() : null, Items.STICK);
		}
	}

	// ── Whisper: [sender head] → [receiver head] ─────────────────────────────

	private static final String WHISPER_ARROW = "→";
	private static final int WHISPER_ARROW_GAP = 3;

	private static boolean usesWhisperHeads(ChatMessageEntry entry) {
		return entry.type == MessageType.WHISPER && entry.iconOverride == null && !entry.isCollapsed;
	}

	private static int whisperHeadsWidth(Font font) {
		return ChatLayout.AVATAR_SIZE * 2 + WHISPER_ARROW_GAP * 2 + font.width(WHISPER_ARROW);
	}

	/**
	 * Who wrote to whom: author's head, an arrow, recipient's head.
	 * Incoming: other player → you. Outgoing: you → other player.
	 */
	private static void renderWhisperHeads(Gfx g, Font font, ChatMessageEntry entry,
			int x, int y, float alpha) {
		renderHead(g, x, y, alpha, entry.senderInfo, entry.senderName);

		int arrowX = x + ChatLayout.AVATAR_SIZE + WHISPER_ARROW_GAP;
		int arrowY = y + (ChatLayout.AVATAR_SIZE - font.lineHeight) / 2 + 1;
		int arrowColor = ((int) (255 * alpha) << 24) | 0xAAAAAA;
		g.text(font, WHISPER_ARROW, arrowX, arrowY, arrowColor, false);

		int receiverX = arrowX + font.width(WHISPER_ARROW) + WHISPER_ARROW_GAP;
		renderHead(g, receiverX, y, alpha, entry.receiverInfo, entry.receiverName);
	}

	private static void renderHead(Gfx g, int x, int y, float alpha, PlayerInfo info, String name) {
		if (info != null) {
			ChatHudAvatarRenderer.renderPlayerHead(g, x, y, info, alpha);
		} else {
			ChatHudAvatarRenderer.renderPlayerHeadByName(g, x, y, name, alpha);
		}
	}
}

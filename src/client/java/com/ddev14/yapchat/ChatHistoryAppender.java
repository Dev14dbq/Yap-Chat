package com.ddev14.yapchat;

import com.ddev14.yapchat.chat.ChatMessageEntry;
import com.ddev14.yapchat.chat.MessageType;
import com.ddev14.yapchat.chat.RuleEngine;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Добавление строк в историю чата {@link YapChatHud#messageHistory}.
 * <p>
 * Суммирование (x2, x3) срабатывает только для <b>полностью идентичных</b> сообщений
 * (одинаковый текст + одинаковый тип + одинаковый отправитель).
 * Когда {@link YapChatHud#isAntiSpamEnabled()} == false суммирование отключается полностью.
 */
public final class ChatHistoryAppender {

	private ChatHistoryAppender() {}

	/**
	 * Adds a message to the history. {@code candidate} is a new, not yet stored entry;
	 * if it repeats the previous message it is merged into it instead.
	 *
	 * @return the stored entry (new or the one it was merged into), or null if a rule hid it
	 */
	public static ChatMessageEntry addLine(YapChatHud hud, ChatMessageEntry candidate) {
		if (candidate == null || candidate.message == null) return null;

		if (hud.isAntiSpamEnabled() && !hud.messageHistory.isEmpty()) {
			ChatMessageEntry last = hud.messageHistory.get(hud.messageHistory.size() - 1);
			if (isIdentical(last, candidate)) {
				return mergeInto(last, candidate);
			}
		}

		ChatMessageEntry entry = candidate;

		// Apply config rules
		if (!hud.rules.isEmpty()) {
			RuleEngine.Result rr = RuleEngine.apply(hud.rules, entry);
			if (rr.hide) return null;
			if (rr.iconOverride != null)       entry.iconOverride        = rr.iconOverride;
			if (rr.noStyle)                    entry.noStyle             = true;
			if (rr.displayTimeOverride > 0)    entry.displayTimeOverride = (int) rr.displayTimeOverride;
		}

		hud.messageHistory.add(entry);
		trimToMaxHistory(hud);
		hud.scrollOffset = 0;
		return entry;
	}

	/**
	 * Разворачивает все сгруппированные записи (repeatCount &gt; 1) в отдельные сообщения.
	 * Вызывается при отключении антиспама.
	 */
	public static void explodeGroups(YapChatHud hud) {
		List<ChatMessageEntry> expanded = new ArrayList<>(hud.messageHistory.size() * 2);
		for (ChatMessageEntry entry : hud.messageHistory) {
			if (entry.repeatCount > 1) {
				if (entry.expandedMessages != null && !entry.expandedMessages.isEmpty()) {
					for (ChatMessageEntry sub : entry.expandedMessages) {
						sub.repeatCount = 1;
						sub.expandedMessages = null;
						sub.isExpanded = false;
						expanded.add(sub);
					}
				} else {
					for (int i = 0; i < entry.repeatCount; i++) {
						expanded.add(entry.copySingle());
					}
				}
			} else {
				entry.expandedMessages = null;
				entry.isExpanded = false;
				expanded.add(entry);
			}
		}
		hud.messageHistory.clear();
		hud.messageHistory.addAll(expanded);
		trimToMaxHistory(hud);
	}

	/**
	 * Перегруппировывает соседние идентичные сообщения обратно.
	 * Вызывается при включении антиспама.
	 */
	public static void regroupIdentical(YapChatHud hud) {
		List<ChatMessageEntry> source = new ArrayList<>(hud.messageHistory);
		hud.messageHistory.clear();
		for (ChatMessageEntry entry : source) {
			if (!hud.messageHistory.isEmpty()) {
				ChatMessageEntry last = hud.messageHistory.get(hud.messageHistory.size() - 1);
				if (isIdentical(last, entry)) {
					mergeInto(last, entry);
					continue;
				}
			}
			hud.messageHistory.add(entry);
		}
	}

	/** Обрезка истории до {@link YapChatHud#maxHistorySize}. */
	public static void trimToMaxHistory(YapChatHud hud) {
		while (hud.messageHistory.size() > hud.maxHistorySize) {
			hud.messageHistory.remove(0);
		}
	}

	// ── internal helpers ──────────────────────────────────────────────────────

	/** Same kind, same people, same direction and same visible text. */
	private static boolean isIdentical(ChatMessageEntry last, ChatMessageEntry next) {
		if (last.type != next.type) return false;
		if (last.direction != next.direction) return false;
		if (!java.util.Objects.equals(last.receiverName, next.receiverName)) return false;
		if (!last.message.getString().equals(next.message.getString())) return false;
		return sameSender(last.senderInfo, next.senderInfo)
				&& java.util.Objects.equals(last.senderName, next.senderName);
	}

	private static boolean sameSender(PlayerInfo a, PlayerInfo b) {
		if (a == null && b == null) return true;
		if (a != null && b != null) return com.ddev14.yapchat.compat.Compat.uuid(a.getProfile()).equals(com.ddev14.yapchat.compat.Compat.uuid(b.getProfile()));
		return false;
	}

	private static ChatMessageEntry mergeInto(ChatMessageEntry last, ChatMessageEntry next) {
		last.repeatCount++;
		last.timestamp = System.currentTimeMillis();

		if (last.expandedMessages == null) {
			last.expandedMessages = new ArrayList<>();
			last.expandedMessages.add(last.copySingle());
		}
		last.expandedMessages.add(next);

		if (last.senderInfo == null && next.senderInfo != null) {
			last.updateSenderInfo(next.senderInfo);
		}
		if (last.receiverInfo == null && next.receiverInfo != null) {
			last.receiverInfo = next.receiverInfo;
		}
		return last;
	}
}

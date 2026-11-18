package com.ddev14.kkschat.pipeline;

import com.ddev14.kkschat.chat.MessageType;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Result of classification: what the message is and who is involved.
 *
 * @param type            exactly one message type
 * @param direction       for whispers: incoming or outgoing; NONE otherwise
 * @param senderName      profile name of the author (null if not a player message or unknown)
 * @param senderId        author UUID if known
 * @param senderDisplay   styled author name as the server sent it (keeps team colors, hover, click)
 * @param receiverName    profile name of the recipient of an outgoing whisper
 * @param receiverDisplay styled recipient name
 * @param body            message body without the name decoration, with original styling; null = unknown
 * @param origin          what was captured
 * @param tier            which rule decided the type (for debugging and tests)
 */
public record ClassifiedMessage(
		MessageType type,
		Direction direction,
		String senderName,
		UUID senderId,
		Component senderDisplay,
		String receiverName,
		Component receiverDisplay,
		Component body,
		IncomingMessage origin,
		Tier tier) {

	/** Which classification level produced the result. */
	public enum Tier {
		/** Vanilla chat type of a player chat packet. */
		CHAT_TYPE,
		/** A known translation key somewhere in the component tree. */
		TRANSLATION_KEY,
		/** A server/plugin text pattern. */
		SERVER_PATTERN,
		/** The whole message is styled as an error (red). */
		ERROR_STYLE,
		/** Nothing matched. */
		FALLBACK
	}

	static ClassifiedMessage simple(MessageType type, IncomingMessage origin, Tier tier) {
		return new ClassifiedMessage(type, Direction.NONE, null, null, null, null, null, null, origin, tier);
	}
}

package com.ddev14.yapchat.pipeline;

import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Everything the game told us about a message, captured once at the entry point.
 * Nothing here is guessed: every field comes straight from the packet / Fabric event.
 *
 * @param content       the full message as the vanilla chat would show it
 * @param source        channel the message arrived through
 * @param chatTypeId    vanilla chat type id (e.g. {@code minecraft:msg_command_incoming}); null for system messages
 * @param senderDisplay decorated sender name from the chat type binding; null if unknown
 * @param targetDisplay decorated target name (outgoing private messages); null if none
 * @param senderId      sender profile UUID; null if unknown
 * @param senderProfileName sender profile name; null if unknown
 * @param body          message body without the name decoration (player chat only); null if unknown
 * @param overlay       true if the server meant this for the action bar
 */
public record IncomingMessage(
		Component content,
		Source source,
		String chatTypeId,
		Component senderDisplay,
		Component targetDisplay,
		UUID senderId,
		String senderProfileName,
		Component body,
		boolean overlay) {

	public static IncomingMessage system(Component content, boolean overlay) {
		return new IncomingMessage(content, Source.SERVER_SYSTEM, null, null, null, null, null, null, overlay);
	}

	public static IncomingMessage client(Component content) {
		return new IncomingMessage(content, Source.CLIENT_SYSTEM, null, null, null, null, null, null, false);
	}
}

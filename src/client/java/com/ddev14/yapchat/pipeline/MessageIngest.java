package com.ddev14.yapchat.pipeline;

import com.ddev14.yapchat.YapChatHud;
import com.ddev14.yapchat.chat.MessageType;
import com.ddev14.yapchat.skin.PlayerInfoLookup;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.sounds.SoundEvents;

import java.util.UUID;

/**
 * The only entry point for incoming messages. Captures what the game knows,
 * classifies, presents, and stores the result in the HUD history.
 *
 * <p>Each {@code on*} method returns whether vanilla should keep processing the message.
 */
public final class MessageIngest {
	private MessageIngest() {}

	/**
	 * Set when a server system message was stored here and vanilla is about to add the same message
	 * to its own chat (mod disabled). The ChatComponent hook then skips it instead of storing it twice.
	 */
	private static boolean vanillaAddsCapturedSystemMessage;

	/** Fabric {@code ALLOW_CHAT}: player chat packets, including vanilla /msg and /teammsg. */
	public static boolean onPlayerChat(YapChatHud hud, Component message, PlayerChatMessage signed,
			GameProfile sender, ChatType.Bound params) {
		// Vanilla "Chat: Commands Only / Hidden": player chat is not shown (no bubble either).
		if (net.minecraft.client.Minecraft.getInstance().options.chatVisibility().get()
				!= net.minecraft.world.entity.player.ChatVisiblity.FULL) {
			return false;
		}
		String chatTypeId = params == null ? null
				: com.ddev14.yapchat.compat.Compat.chatTypeId(params);
		Component body = null;
		if (signed != null) {
			body = com.ddev14.yapchat.compat.Compat.chatBody(signed);
		}
		IncomingMessage in = new IncomingMessage(message, Source.PLAYER_CHAT, chatTypeId,
				params != null ? params.name() : null,
				params != null ? com.ddev14.yapchat.compat.Compat.chatTarget(params) : null,
				sender != null ? com.ddev14.yapchat.compat.Compat.uuid(sender) : null,
				sender != null ? com.ddev14.yapchat.compat.Compat.name(sender) : null,
				body, false);
		store(hud, classify(hud, in));
		return !hud.isEnabled();
	}

	/** Fabric {@code ALLOW_GAME}: server system messages and action bar text. */
	public static boolean onGameMessage(YapChatHud hud, Component message, boolean overlay) {
		ClassifiedMessage msg = classify(hud, IncomingMessage.system(message, overlay));
		if (overlay) {
			// The action bar belongs to vanilla. Only bed/sleep notices are worth keeping in chat.
			if (msg.type() != MessageType.SLEEP) return true;
			store(hud, msg);
			return !hud.isEnabled();
		}
		store(hud, msg);
		boolean vanillaContinues = !hud.isEnabled();
		vanillaAddsCapturedSystemMessage = vanillaContinues;
		return vanillaContinues;
	}

	/**
	 * {@code ChatComponent.addServerSystemMessage}: system messages that did not come through
	 * Fabric's event (or the event's own message on its way into vanilla chat).
	 *
	 * @return true to cancel the vanilla call
	 */
	public static boolean onServerSystemMessage(YapChatHud hud, Component message) {
		if (vanillaAddsCapturedSystemMessage) {
			vanillaAddsCapturedSystemMessage = false;
			return false;
		}
		store(hud, classify(hud, IncomingMessage.system(message, false)));
		return hud.isEnabled();
	}

	/**
	 * {@code ChatComponent.addClientSystemMessage}: messages created on the client by other mods,
	 * screenshots, debug keys.
	 *
	 * @return true to cancel the vanilla call
	 */
	public static boolean onClientSystemMessage(YapChatHud hud, Component message) {
		store(hud, classify(hud, IncomingMessage.client(message)));
		return hud.isEnabled();
	}

	// ── internals ────────────────────────────────────────────────────────────

	/**
	 * {@code ChatComponent.addMessage(Component)} on versions without separate client and server
	 * system-message methods: the event's own message on its way into vanilla chat passes, anything
	 * else is treated as a client message.
	 *
	 * @return true to cancel the vanilla call
	 */
	public static boolean onVanillaAddMessage(YapChatHud hud, Component message) {
		if (vanillaAddsCapturedSystemMessage) {
			vanillaAddsCapturedSystemMessage = false;
			return false;
		}
		return onClientSystemMessage(hud, message);
	}

	private static ClassifiedMessage classify(YapChatHud hud, IncomingMessage in) {
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener connection = mc.getConnection();
		String localName = mc.player != null ? com.ddev14.yapchat.compat.Compat.name(mc.player.getGameProfile()) : mc.getUser().getName();
		MessageClassifier.Context ctx = new MessageClassifier.Context(
				localName,
				name -> connection != null && PlayerInfoLookup.getPlayerInfoByName(name) != null,
				hud.getServerPatterns());
		return MessageClassifier.classify(in, ctx);
	}

	private static void store(YapChatHud hud, ClassifiedMessage msg) {
		if (Components.isVisiblyEmpty(msg.origin().content())) return;

		Component display = MessagePresenter.present(msg, hud.isModifyMessageText());
		PlayerInfo senderInfo = lookup(msg.senderId(), msg.senderName());
		PlayerInfo receiverInfo = lookup(null, msg.receiverName());
		hud.addMessage(msg, display, senderInfo, receiverInfo);

		if (msg.type() == MessageType.PLAYER_CHAT && msg.body() != null) {
			com.ddev14.yapchat.bubble.ChatBubbles.add(msg.senderId(), msg.senderName(), msg.body());
		}

		if (msg.type() == MessageType.CHALLENGE) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && mc.level != null) {
				mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
			}
		}
	}

	private static PlayerInfo lookup(UUID id, String name) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) return null;
		if (id != null) {
			PlayerInfo byId = connection.getPlayerInfo(id);
			if (byId != null) return byId;
		}
		return name != null ? PlayerInfoLookup.getPlayerInfoByName(name) : null;
	}
}

package com.ddev14.kkschat.pipeline;

import com.ddev14.kkschat.KksChatHud;
import com.ddev14.kkschat.chat.MessageType;
import com.ddev14.kkschat.skin.PlayerInfoLookup;
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
	public static boolean onPlayerChat(KksChatHud hud, Component message, PlayerChatMessage signed,
			GameProfile sender, ChatType.Bound params) {
		String chatTypeId = params == null ? null
				: params.chatType().unwrapKey().map(k -> k.identifier().toString()).orElse(null);
		Component body = null;
		if (signed != null) {
			body = signed.unsignedContent() != null ? signed.unsignedContent() : signed.decoratedContent();
		}
		IncomingMessage in = new IncomingMessage(message, Source.PLAYER_CHAT, chatTypeId,
				params != null ? params.name() : null,
				params != null ? params.targetName().orElse(null) : null,
				sender != null ? sender.id() : null,
				sender != null ? sender.name() : null,
				body, false);
		store(hud, classify(hud, in));
		return !hud.isEnabled();
	}

	/** Fabric {@code ALLOW_GAME}: server system messages and action bar text. */
	public static boolean onGameMessage(KksChatHud hud, Component message, boolean overlay) {
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
	public static boolean onServerSystemMessage(KksChatHud hud, Component message) {
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
	public static boolean onClientSystemMessage(KksChatHud hud, Component message) {
		store(hud, classify(hud, IncomingMessage.client(message)));
		return hud.isEnabled();
	}

	// ── internals ────────────────────────────────────────────────────────────

	private static ClassifiedMessage classify(KksChatHud hud, IncomingMessage in) {
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener connection = mc.getConnection();
		String localName = mc.player != null ? mc.player.getGameProfile().name() : mc.getUser().getName();
		MessageClassifier.Context ctx = new MessageClassifier.Context(
				localName,
				name -> connection != null && connection.getPlayerInfoIgnoreCase(name) != null,
				hud.getServerPatterns());
		return MessageClassifier.classify(in, ctx);
	}

	private static void store(KksChatHud hud, ClassifiedMessage msg) {
		if (Components.isVisiblyEmpty(msg.origin().content())) return;

		Component display = MessagePresenter.present(msg, hud.isModifyMessageText());
		PlayerInfo senderInfo = lookup(msg.senderId(), msg.senderName());
		PlayerInfo receiverInfo = lookup(null, msg.receiverName());
		hud.addMessage(msg, display, senderInfo, receiverInfo);

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

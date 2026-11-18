package com.ddev14.yapchat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import com.ddev14.yapchat.pipeline.MessageIngest;

public class YapChatModClient implements ClientModInitializer {

	private static final YapChatHud HUD = createHud();

	private static YapChatHud createHud() {
		YapChatHud hud = new YapChatHud();
		hud.loadConfig();
		YapChatConfig.save(hud);
		return hud;
	}
	
	public static YapChatHud getHud() {
		return HUD;
	}

	@Override
	public void onInitializeClient() {
		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath("yap-chat", "chat_hud"),
			(guiGraphics, deltaTracker) -> HUD.onHudRender(guiGraphics)
		);
		// Bubble looks come from resource packs; re-read them when packs change.
		net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
				.registerReloadListener(Identifier.fromNamespaceAndPath("yap-chat", "bubble_styles"),
						com.ddev14.yapchat.bubble.BubbleSkins.INSTANCE);
		HudElementRegistry.addFirst(
			Identifier.fromNamespaceAndPath("yap-chat", "chat_bubbles"),
			(guiGraphics, deltaTracker) -> com.ddev14.yapchat.bubble.ChatBubbles.render(HUD, guiGraphics, deltaTracker)
		);

		// Every message goes through MessageIngest; the return value tells Fabric
		// whether vanilla should still show it (only when Yap Chat is disabled).
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
				MessageIngest.onGameMessage(HUD, message, overlay));

		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, chatType, timestamp) ->
				MessageIngest.onPlayerChat(HUD, message, signedMessage, sender, chatType));

		// Обработка открытия экрана достижений теперь происходит через MinecraftMixin
		// который перехватывает handleKeybinds и открывает экран достижений

		// Обработка F3+D для очистки чата теперь происходит через ChatComponentMixin
		// который перехватывает вызов clearMessages() в ChatComponent
	}
}


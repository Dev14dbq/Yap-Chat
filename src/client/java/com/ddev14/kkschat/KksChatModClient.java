package com.ddev14.kkschat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import com.ddev14.kkschat.pipeline.MessageIngest;

public class KksChatModClient implements ClientModInitializer {

	private static final KksChatHud HUD = createHud();

	private static KksChatHud createHud() {
		KksChatHud hud = new KksChatHud();
		hud.loadConfig();
		KksChatConfig.save(hud);
		return hud;
	}
	
	public static KksChatHud getHud() {
		return HUD;
	}

	@Override
	public void onInitializeClient() {
		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath("kks-chat", "chat_hud"),
			(guiGraphics, deltaTracker) -> HUD.onHudRender(guiGraphics)
		);

		// Every message goes through MessageIngest; the return value tells Fabric
		// whether vanilla should still show it (only when KKS Chat is disabled).
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


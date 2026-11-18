package com.ddev14.yapchat;

import com.ddev14.yapchat.compat.Compat;
import com.ddev14.yapchat.compat.Gfx;
import com.ddev14.yapchat.pipeline.MessageIngest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;

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
		HudElementRegistry.addLast(Compat.id("yap-chat", "chat_hud"),
				(graphics, delta) -> HUD.onHudRender(Gfx.of(graphics)));
		// Bubble looks come from resource packs; re-read them when packs change.
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(Compat.id("yap-chat", "bubble_styles"),
				com.ddev14.yapchat.bubble.BubbleSkins.INSTANCE);
		HudElementRegistry.addFirst(Compat.id("yap-chat", "chat_bubbles"),
				(graphics, delta) -> com.ddev14.yapchat.bubble.ChatBubbles.render(HUD, Gfx.of(graphics),
						delta.getGameTimeDeltaPartialTick(false)));

		// Every message goes through MessageIngest; the return value tells Fabric
		// whether vanilla should still show it (only when Yap Chat is disabled).
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
				MessageIngest.onGameMessage(HUD, message, overlay));

		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, chatType, timestamp) ->
				MessageIngest.onPlayerChat(HUD, message, signedMessage, sender, chatType));
	}
}

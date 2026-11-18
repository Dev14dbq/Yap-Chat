package com.ddev14.yapchat;

import com.ddev14.yapchat.compat.Compat;
import com.ddev14.yapchat.compat.Gfx;
import com.ddev14.yapchat.pipeline.MessageIngest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
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
		// Bubbles first, so the chat is drawn over them.
		HudRenderCallback.EVENT.register((graphics, delta) -> {
			Gfx g = Gfx.of(graphics);
			com.ddev14.yapchat.bubble.ChatBubbles.render(HUD, g, delta);
			HUD.onHudRender(g);
			g.flushTooltips();
		});
		// Bubble looks come from resource packs; re-read them when packs change.
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				com.ddev14.yapchat.bubble.BubbleSkins.INSTANCE);

		// Incoming messages: mixin.client.ChatListenerMixin → MessageIngest.
	}
}

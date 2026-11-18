package com.ddev14.yapchat.mixin.client;

import com.ddev14.yapchat.YapChatModClient;
import com.ddev14.yapchat.pipeline.MessageIngest;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Incoming chat. Before 1.19 every server message (player chat included) arrives here as one
 * component; the classifier tells player chat, whispers and system text apart by their
 * translation keys and formats.
 */
@Mixin(Gui.class)
public class ChatListenerMixin {
	@Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
	private void yapchat$chat(ChatType type, Component message, UUID sender, CallbackInfo ci) {
		// Vanilla "Chat: Commands Only / Hidden": player chat is not shown (no bubble either).
		if (type == ChatType.CHAT && net.minecraft.client.Minecraft.getInstance().options.chatVisibility
				!= net.minecraft.world.entity.player.ChatVisiblity.FULL) {
			ci.cancel();
			return;
		}
		if (!MessageIngest.onGameMessage(YapChatModClient.getHud(), message, type == ChatType.GAME_INFO)) ci.cancel();
	}
}

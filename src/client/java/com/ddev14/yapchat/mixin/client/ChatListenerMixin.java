package com.ddev14.yapchat.mixin.client;

import com.ddev14.yapchat.YapChatModClient;
import com.ddev14.yapchat.pipeline.MessageIngest;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

/**
 * Incoming chat. Fabric API for 1.19.2 has no client message events, so the same places
 * Fabric hooks on newer versions are hooked here: system messages, player chat and
 * server-sent player chat without a sender (e.g. /say from the console).
 */
@Mixin(ChatListener.class)
public class ChatListenerMixin {
	@Inject(method = "handleSystemMessage", at = @At("HEAD"), cancellable = true)
	private void yapchat$system(Component message, boolean overlay, CallbackInfo ci) {
		if (!MessageIngest.onGameMessage(YapChatModClient.getHud(), message, overlay)) ci.cancel();
	}

	@Inject(method = "showMessageToPlayer", at = @At("HEAD"), cancellable = true)
	private void yapchat$player(ChatType.Bound bound, PlayerChatMessage signed, Component decorated, PlayerInfo sender,
			boolean onlyShowSecureChat, Instant received, CallbackInfoReturnable<Boolean> cir) {
		if (!MessageIngest.onPlayerChat(YapChatModClient.getHud(), decorated, signed,
				sender != null ? sender.getProfile() : null, bound)) cir.setReturnValue(true);
	}

	@Inject(method = "processNonPlayerChatMessage", at = @At("HEAD"), cancellable = true)
	private void yapchat$nonPlayer(ChatType.Bound bound, PlayerChatMessage signed, Component decorated,
			CallbackInfoReturnable<Boolean> cir) {
		if (!MessageIngest.onPlayerChat(YapChatModClient.getHud(), decorated, signed, null, bound)) cir.setReturnValue(true);
	}
}

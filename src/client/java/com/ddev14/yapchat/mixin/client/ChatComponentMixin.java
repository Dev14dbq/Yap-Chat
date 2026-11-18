package com.ddev14.yapchat.mixin.client;

import com.ddev14.yapchat.YapChatModClient;
import com.ddev14.yapchat.pipeline.MessageIngest;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin для перехвата очистки чата (F3+D) и всех сообщений, добавляемых в чат
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	
	@Inject(
		method = "clearMessages",
		at = @At("HEAD")
	)
	private void onClearMessages(boolean clearHistory, CallbackInfo ci) {
		// Когда стандартный чат очищается (F3+D), очищаем и наш кастомный чат
		YapChatModClient.getHud().clearChat();
	}
	
	/**
	 * System and client-side messages (server messages that skipped Fabric's ALLOW_GAME event,
	 * other mods, screenshots, debug keys). Player chat uses the three-argument overload.
	 */
	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
	private void yapchat$onAddMessage(Component message, CallbackInfo ci) {
		if (MessageIngest.onVanillaAddMessage(YapChatModClient.getHud(), message)) {
			ci.cancel();
		}
	}
}

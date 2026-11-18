package com.ddev14.kkschat.mixin.client;

import com.ddev14.kkschat.KksChatModClient;
import com.ddev14.kkschat.pipeline.MessageIngest;
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
		KksChatModClient.getHud().clearChat();
	}
	
	/** Client-side messages (other mods, screenshots, debug keys). */
	@Inject(method = "addClientSystemMessage", at = @At("HEAD"), cancellable = true)
	private void kkschat$onAddClientSystemMessage(Component message, CallbackInfo ci) {
		if (MessageIngest.onClientSystemMessage(KksChatModClient.getHud(), message)) {
			ci.cancel();
		}
	}

	/** Server system messages that reach the chat without Fabric's ALLOW_GAME event. */
	@Inject(method = "addServerSystemMessage", at = @At("HEAD"), cancellable = true)
	private void kkschat$onAddServerSystemMessage(Component message, CallbackInfo ci) {
		if (MessageIngest.onServerSystemMessage(KksChatModClient.getHud(), message)) {
			ci.cancel();
		}
	}
}

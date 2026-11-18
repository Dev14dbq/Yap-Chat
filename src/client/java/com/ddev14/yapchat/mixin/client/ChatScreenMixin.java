package com.ddev14.yapchat.mixin.client;

import com.ddev14.yapchat.YapChatHud;
import com.ddev14.yapchat.YapChatModClient;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

	protected ChatScreenMixin(Component title) {
		super(title);
	}

	@Inject(at = @At("HEAD"), method = "mouseScrolled", cancellable = false)
	private void onMouseScrolled(double mouseX, double mouseY, double deltaY, CallbackInfoReturnable<Boolean> cir) {
		// Перехватываем прокрутку мыши для истории чата
		YapChatHud hud = YapChatModClient.getHud();
		if (hud != null) {
			// Используем deltaY для вертикальной прокрутки
			hud.handleMouseScroll(deltaY);
		}
	}
	
	@Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
	private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		// Обрабатываем клик по сжатым сообщениям
		if (button == 0) { // Левая кнопка мыши
			YapChatHud hud = YapChatModClient.getHud();
			if (hud != null) {
				if (hud.handleMessageClick(mouseX, mouseY)) {
					// Клик обработан, отменяем дальнейшую обработку
					cir.setReturnValue(true);
					cir.cancel();
				}
			}
		}
	}
	
	@Inject(at = @At("HEAD"), method = "removed")
	private void onChatClose(CallbackInfo ci) {
		// Сворачиваем развернутые сообщения при закрытии чата
		YapChatHud hud = YapChatModClient.getHud();
		if (hud != null) {
			hud.collapseMessagesOnChatClose();
		}
	}
}

package com.ddev14.yapchat.mixin.client;

import com.ddev14.yapchat.YapChatHud;
import com.ddev14.yapchat.YapChatModClient;
import com.ddev14.yapchat.ChatHudClickDispatch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
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
	private void onMouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
		// Перехватываем прокрутку мыши для истории чата
		YapChatHud hud = YapChatModClient.getHud();
		if (hud != null) {
			// Используем deltaY для вертикальной прокрутки
			hud.handleMouseScroll(deltaY);
		}
	}
	
	@Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
	private void onMouseClicked(MouseButtonEvent event, boolean handled, CallbackInfoReturnable<Boolean> cir) {
		// Обрабатываем клик по сжатым сообщениям
		if (!handled && event.button() == 0) { // Левая кнопка мыши
			YapChatHud hud = YapChatModClient.getHud();
			if (hud != null) {
				if (hud.handleMessageClick(event.x(), event.y())) {
					// Клик обработан, отменяем дальнейшую обработку
					cir.setReturnValue(true);
					cir.cancel();
				}
			}
		}
	}
	
	/** Hover text of the message under the mouse (item stats, player info, hints from the server). */
	@Inject(at = @At("TAIL"), method = "extractRenderState")
	private void yapchat$hoverTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		YapChatHud hud = YapChatModClient.getHud();
		if (hud == null) return;
		Style style = ChatHudClickDispatch.styleUnderMouse(hud, mouseX, mouseY);
		HoverEvent hover = style == null ? null : style.getHoverEvent();
		if (hover == null) return;
		try {
			if (hover instanceof HoverEvent.ShowText text) {
				g.setTooltipForNextFrame(this.font, text.value(), mouseX, mouseY);
			} else if (hover instanceof HoverEvent.ShowItem item) {
				g.setTooltipForNextFrame(this.font, item.item().create(), mouseX, mouseY);
			} else if (hover instanceof HoverEvent.ShowEntity entity) {
				if (this.minecraft.options.advancedItemTooltips) {
					g.setComponentTooltipForNextFrame(this.font, entity.entity().getTooltipLines(), mouseX, mouseY);
				}
			}
		} catch (RuntimeException ignored) {
			// a broken item from the server must not crash the chat screen
		}
	}

	@Inject(at = @At("HEAD"), method = "onClose")
	private void onChatClose(CallbackInfo ci) {
		// Сворачиваем развернутые сообщения при закрытии чата
		YapChatHud hud = YapChatModClient.getHud();
		if (hud != null) {
			hud.collapseMessagesOnChatClose();
		}
	}
}

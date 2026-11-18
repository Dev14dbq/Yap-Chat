package com.ddev14.yapchat.mixin.client;

import com.ddev14.yapchat.ui.YapChatSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Whenever the game opens vanilla "Chat Settings", our screen opens instead (vanilla options are inside it). */
@Mixin(Minecraft.class)
public class GuiSetScreenMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen yapchat$replaceChatOptions(Screen screen) {
		if (screen != null && screen.getClass() == ChatOptionsScreen.class) {
			return new YapChatSettingsScreen(((OptionsSubScreenAccessor) screen).yapchat$lastScreen());
		}
		return screen;
	}
}

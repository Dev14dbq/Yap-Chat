package com.ddev14.yapchat.mixin.client;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla's own handling of a click on chat text: insertion (shift-click), commands with
 * their confirmation screens, links, dialogs and custom server actions.
 */
@Mixin(ChatScreen.class)
public interface ChatScreenInvoker {
	@Invoker("handleComponentClicked")
	boolean yapchat$handleComponentClicked(Style style, boolean insert);

	@Invoker("insertionClickMode")
	boolean yapchat$insertionClickMode();
}

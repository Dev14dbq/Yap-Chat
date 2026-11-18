package com.ddev14.yapchat.mixin.client;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The list of vanilla chat options, so our screen can show them. */
@Mixin(ChatOptionsScreen.class)
public interface ChatOptionsScreenAccessor {
	@Invoker("options")
	static OptionInstance<?>[] yapchat$options(Options options) {
		throw new AssertionError();
	}
}

package com.ddev14.yapchat.mixin.client;

import net.minecraft.client.Option;
import net.minecraft.client.gui.screens.SimpleOptionsSubScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The list of vanilla chat options, so our screen can show them. */
@Mixin(SimpleOptionsSubScreen.class)
public interface ChatOptionsScreenAccessor {
	@Accessor("smallOptions")
	Option[] yapchat$smallOptions();
}

package com.ddev14.yapchat.mixin.client;

import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

/** Caption, value label and tooltip of a vanilla option, to draw it with our own controls. */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
	@Accessor("caption")
	Component yapchat$caption();

	@Accessor("toString")
	Function<Object, Component> yapchat$toString();

	@Accessor("tooltip")
	OptionInstance.TooltipSupplier<Object> yapchat$tooltip();
}

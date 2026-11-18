package com.ddev14.yapchat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Hover tooltips of chat text (show text / item / entity), drawn by the open screen. */
@Mixin(Screen.class)
public interface ScreenAccessor {
	@Invoker("renderComponentHoverEffect")
	void yapchat$renderComponentHoverEffect(PoseStack poseStack, Style style, int x, int y);
}

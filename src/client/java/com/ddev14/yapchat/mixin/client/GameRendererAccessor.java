package com.ddev14.yapchat.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Field of view of the current frame, to place chat bubbles over heads. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Invoker("getFov")
	float yapchat$getFov(Camera camera, float partialTick, boolean useFovSetting);
}

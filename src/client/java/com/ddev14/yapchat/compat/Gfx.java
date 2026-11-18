package com.ddev14.yapchat.compat;

import com.ddev14.yapchat.mixin.client.ScreenAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The drawing calls Yap Chat uses, in one place. Each Minecraft version has its own
 * implementation of this class; the rest of the mod only talks to it.
 * (Minecraft 1.18.2: no GuiGraphics yet — PoseStack plus GuiComponent/Font/ItemRenderer calls;
 * tints go through the shader color; sprites are nine-sliced by {@link NineSlice}.)
 */
public final class Gfx {
	public final PoseStack raw;
	private final Pose pose = new Pose();
	private final List<Runnable> tooltips = new ArrayList<>();
	private final Deque<int[]> scissors = new ArrayDeque<>();
	private int strata;

	private Gfx(PoseStack raw) {
		this.raw = raw;
	}

	public static Gfx of(PoseStack raw) {
		return new Gfx(raw);
	}

	private static Minecraft mc() { return Minecraft.getInstance(); }

	public int guiWidth() { return mc().getWindow().getGuiScaledWidth(); }
	public int guiHeight() { return mc().getWindow().getGuiScaledHeight(); }

	public void fill(int x1, int y1, int x2, int y2, int color) {
		if (Argb.alpha(color) == 0) return;
		GuiComponent.fill(raw, x1, y1, x2, y2, color);
	}

	/** Old fonts draw alpha 0 as opaque; nearly transparent text is simply skipped. */
	private static boolean invisible(int color) {
		return Argb.alpha(color) < 4;
	}

	public void text(Font font, String s, int x, int y, int color, boolean shadow) {
		if (invisible(color)) return;
		if (shadow) font.drawShadow(raw, s, x, y, color);
		else font.draw(raw, s, x, y, color);
	}

	public void text(Font font, Component s, int x, int y, int color, boolean shadow) {
		if (invisible(color)) return;
		if (shadow) font.drawShadow(raw, s, x, y, color);
		else font.draw(raw, s, x, y, color);
	}

	public void text(Font font, FormattedCharSequence s, int x, int y, int color, boolean shadow) {
		if (invisible(color)) return;
		if (shadow) font.drawShadow(raw, s, x, y, color);
		else font.draw(raw, s, x, y, color);
	}

	/** Chat lines in the open chat: hovering a part with a hover event shows its tooltip. */
	public void hoverableLines(Font font, List<FormattedCharSequence> lines, int x, int y, float alpha) {
		int color = Argb.white(alpha);
		for (int i = 0; i < lines.size(); i++) text(font, lines.get(i), x, y + i * font.lineHeight, color, true);
		int[] m = mouse();
		int line = Math.floorDiv(m[1] - y, font.lineHeight);
		if (line < 0 || line >= lines.size() || m[0] < x) return;
		Style style = font.getSplitter().componentStyleAtWidth(lines.get(line), m[0] - x);
		Screen screen = mc().screen;
		if (style != null && style.getHoverEvent() != null && screen != null) {
			tooltips.add(() -> ((ScreenAccessor) screen).yapchat$renderComponentHoverEffect(raw, style, m[0], m[1]));
			flushTooltips();
		}
	}

	/** Mouse position in GUI pixels. */
	public static int[] mouse() {
		Minecraft mc = mc();
		double sx = mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
		double sy = mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
		return new int[] {(int) (mc.mouseHandler.xpos() * sx), (int) (mc.mouseHandler.ypos() * sy)};
	}

	/** Part of a texture: region (u, v, regionW, regionH) drawn into (x, y, w, h). A negative regionH flips it. */
	public void blit(ResourceLocation tex, int x, int y, float u, float v, int w, int h, int regionW, int regionH,
			int texW, int texH, int color) {
		if (Argb.alpha(color) == 0) return;
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, tex);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f,
				(color & 0xFF) / 255f, Argb.alpha(color) / 255f);
		GuiComponent.blit(raw, x, y, w, h, u, v, regionW, regionH, texW, texH);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	public void blit(ResourceLocation tex, int x, int y, float u, float v, int w, int h, int texW, int texH, int color) {
		blit(tex, x, y, u, v, w, h, w, h, texW, texH, color);
	}

	/** GUI sprite (textures/gui/sprites/...), nine-sliced or stretched as its .mcmeta says. */
	public void blitSprite(ResourceLocation sprite, int x, int y, int w, int h, int color) {
		if (Argb.alpha(color) == 0) return;
		NineSlice.draw(this, sprite, x, y, w, h, color);
	}

	/** Items are drawn through the model-view stack here, so our pose is applied to it. */
	public void item(ItemStack stack, int x, int y) {
		PoseStack mv = RenderSystem.getModelViewStack();
		mv.pushPose();
		mv.mulPoseMatrix(raw.last().pose());
		RenderSystem.applyModelViewMatrix();
		mc().getItemRenderer().renderGuiItem(stack, x, y);
		mv.popPose();
		RenderSystem.applyModelViewMatrix();
	}

	public void enableScissor(int x1, int y1, int x2, int y2) {
		int[] r = {x1, y1, x2, y2};
		int[] outer = scissors.peek();
		if (outer != null) {
			r = new int[] {Math.max(x1, outer[0]), Math.max(y1, outer[1]), Math.min(x2, outer[2]), Math.min(y2, outer[3])};
			if (r[2] < r[0]) r[2] = r[0];
			if (r[3] < r[1]) r[3] = r[1];
		}
		scissors.push(r);
		scissor(r);
	}

	public void disableScissor() {
		scissors.poll();
		int[] r = scissors.peek();
		if (r == null) RenderSystem.disableScissor();
		else scissor(r);
	}

	private static void scissor(int[] r) {
		var w = mc().getWindow();
		double s = w.getGuiScale();
		RenderSystem.enableScissor((int) (r[0] * s), (int) (w.getHeight() - r[3] * s),
				Math.max(0, (int) ((r[2] - r[0]) * s)), Math.max(0, (int) ((r[3] - r[1]) * s)));
	}

	/** Everything drawn after this goes above what was drawn before (popovers). */
	public void nextStratum() {
		raw.pushPose();
		raw.translate(0, 0, 300);
		strata++;
	}

	public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
		Screen screen = mc().screen;
		if (screen != null) tooltips.add(() -> screen.renderTooltip(raw, text, x, y));
	}

	public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> lines, int x, int y) {
		Screen screen = mc().screen;
		if (screen != null) tooltips.add(() -> screen.renderTooltip(raw, lines, x, y));
	}

	/** Draws the tooltips of this frame on top and closes the layers opened by {@link #nextStratum}. */
	public void flushTooltips() {
		for (Runnable r : tooltips) r.run();
		tooltips.clear();
		while (strata > 0) {
			raw.popPose();
			strata--;
		}
	}

	public Pose pose() { return pose; }

	/** 2D transform stack. */
	public final class Pose {
		public void pushMatrix() { raw.pushPose(); }
		public void popMatrix() { raw.popPose(); }
		public void translate(float x, float y) { raw.translate(x, y, 0); }
		public void scale(float x, float y) { raw.scale(x, y, 1); }
	}
}

package com.ddev14.yapchat.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The drawing calls Yap Chat uses, in one place. Each Minecraft version has its own
 * implementation of this class; the rest of the mod only talks to it.
 * (Minecraft 1.20.1: GuiGraphics with a 3D pose stack; tints go through the shader color;
 * no GUI sprite atlas, so sprites are nine-sliced by {@link NineSlice}.)
 */
public final class Gfx {
	public final GuiGraphics raw;
	private final Pose pose = new Pose();
	private final List<Runnable> tooltips = new ArrayList<>();
	private int strata;

	private Gfx(GuiGraphics raw) {
		this.raw = raw;
	}

	public static Gfx of(GuiGraphics raw) {
		return new Gfx(raw);
	}

	public int guiWidth() { return raw.guiWidth(); }
	public int guiHeight() { return raw.guiHeight(); }

	public void fill(int x1, int y1, int x2, int y2, int color) {
		if (Argb.alpha(color) == 0) return;
		raw.fill(x1, y1, x2, y2, color);
	}

	/** Old fonts draw alpha 0 as opaque; nearly transparent text is simply skipped. */
	private static boolean invisible(int color) {
		return Argb.alpha(color) < 4;
	}

	public void text(Font font, String s, int x, int y, int color, boolean shadow) {
		if (!invisible(color)) raw.drawString(font, s, x, y, color, shadow);
	}

	public void text(Font font, Component s, int x, int y, int color, boolean shadow) {
		if (!invisible(color)) raw.drawString(font, s, x, y, color, shadow);
	}

	public void text(Font font, FormattedCharSequence s, int x, int y, int color, boolean shadow) {
		if (!invisible(color)) raw.drawString(font, s, x, y, color, shadow);
	}

	/** Chat lines in the open chat: hovering a part with a hover event shows its tooltip. */
	public void hoverableLines(Font font, List<FormattedCharSequence> lines, int x, int y, float alpha) {
		int color = Argb.white(alpha);
		for (int i = 0; i < lines.size(); i++) text(font, lines.get(i), x, y + i * font.lineHeight, color, true);
		int[] m = mouse();
		int line = Math.floorDiv(m[1] - y, font.lineHeight);
		if (line < 0 || line >= lines.size() || m[0] < x) return;
		Style style = font.getSplitter().componentStyleAtWidth(lines.get(line), m[0] - x);
		if (style != null && style.getHoverEvent() != null) {
			tooltips.add(() -> raw.renderComponentHoverEffect(font, style, m[0], m[1]));
			flushTooltips();
		}
	}

	/** Mouse position in GUI pixels. */
	public static int[] mouse() {
		Minecraft mc = Minecraft.getInstance();
		double sx = mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
		double sy = mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
		return new int[] {(int) (mc.mouseHandler.xpos() * sx), (int) (mc.mouseHandler.ypos() * sy)};
	}

	private static void tint(int color) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f,
				(color & 0xFF) / 255f, Argb.alpha(color) / 255f);
	}

	private static void untint() {
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	/** Part of a texture: region (u, v, regionW, regionH) drawn into (x, y, w, h). A negative regionH flips it. */
	public void blit(ResourceLocation tex, int x, int y, float u, float v, int w, int h, int regionW, int regionH,
			int texW, int texH, int color) {
		if (Argb.alpha(color) == 0) return;
		tint(color);
		raw.blit(tex, x, y, w, h, u, v, regionW, regionH, texW, texH);
		untint();
	}

	public void blit(ResourceLocation tex, int x, int y, float u, float v, int w, int h, int texW, int texH, int color) {
		blit(tex, x, y, u, v, w, h, w, h, texW, texH, color);
	}

	/** GUI sprite (textures/gui/sprites/...), nine-sliced or stretched as its .mcmeta says. */
	public void blitSprite(ResourceLocation sprite, int x, int y, int w, int h, int color) {
		if (Argb.alpha(color) == 0) return;
		NineSlice.draw(this, sprite, x, y, w, h, color);
	}

	public void item(ItemStack stack, int x, int y) {
		raw.renderItem(stack, x, y);
	}

	public void enableScissor(int x1, int y1, int x2, int y2) { raw.enableScissor(x1, y1, x2, y2); }
	public void disableScissor() { raw.disableScissor(); }

	/** Everything drawn after this goes above what was drawn before (popovers). */
	public void nextStratum() {
		raw.pose().pushPose();
		raw.pose().translate(0, 0, 300);
		strata++;
	}

	public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
		tooltips.add(() -> raw.renderTooltip(font, text, x, y));
	}

	public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> lines, int x, int y) {
		tooltips.add(() -> raw.renderTooltip(font, lines, x, y));
	}

	/** Draws the tooltips of this frame on top and closes the layers opened by {@link #nextStratum}. */
	public void flushTooltips() {
		for (Runnable r : tooltips) r.run();
		tooltips.clear();
		while (strata > 0) {
			raw.pose().popPose();
			strata--;
		}
	}

	public Pose pose() { return pose; }

	/** 2D transform stack. */
	public final class Pose {
		public void pushMatrix() { raw.pose().pushPose(); }
		public void popMatrix() { raw.pose().popPose(); }
		public void translate(float x, float y) { raw.pose().translate(x, y, 0); }
		public void scale(float x, float y) { raw.pose().scale(x, y, 1); }
	}
}

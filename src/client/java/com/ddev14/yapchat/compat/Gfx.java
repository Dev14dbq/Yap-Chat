package com.ddev14.yapchat.compat;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The drawing calls Yap Chat uses, in one place. Each Minecraft version has its own
 * implementation of this class; the rest of the mod only talks to it.
 * (Minecraft 1.21.11: GuiGraphics with render pipelines and a 2D pose stack.)
 */
public final class Gfx {
	public final GuiGraphics raw;
	private final Pose pose = new Pose();

	private Gfx(GuiGraphics raw) {
		this.raw = raw;
	}

	public static Gfx of(GuiGraphics raw) {
		return new Gfx(raw);
	}

	public int guiWidth() { return raw.guiWidth(); }
	public int guiHeight() { return raw.guiHeight(); }

	public void fill(int x1, int y1, int x2, int y2, int color) {
		raw.fill(x1, y1, x2, y2, color);
	}

	public void text(Font font, String s, int x, int y, int color, boolean shadow) {
		raw.drawString(font, s, x, y, color, shadow);
	}

	public void text(Font font, Component s, int x, int y, int color, boolean shadow) {
		raw.drawString(font, s, x, y, color, shadow);
	}

	public void text(Font font, FormattedCharSequence s, int x, int y, int color, boolean shadow) {
		raw.drawString(font, s, x, y, color, shadow);
	}

	/**
	 * Chat lines in the open chat: hovering shows the text's hover events and the cursor changes
	 * over clickable parts.
	 */
	public void hoverableLines(Font font, List<FormattedCharSequence> lines, int x, int y, float alpha) {
		ActiveTextCollector renderer = raw.textRenderer(GuiGraphics.HoveredTextEffects.TOOLTIP_AND_CURSOR);
		ActiveTextCollector.Parameters params = renderer.defaultParameters().withOpacity(alpha);
		for (int i = 0; i < lines.size(); i++) {
			renderer.accept(TextAlignment.LEFT, x, y + i * font.lineHeight, params, lines.get(i));
		}
	}

	/** Part of a texture: region (u, v, regionW, regionH) drawn into (x, y, w, h). A negative regionH flips it. */
	public void blit(Identifier tex, int x, int y, float u, float v, int w, int h, int regionW, int regionH,
			int texW, int texH, int color) {
		raw.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, regionW, regionH, texW, texH, color);
	}

	public void blit(Identifier tex, int x, int y, float u, float v, int w, int h, int texW, int texH, int color) {
		raw.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, texW, texH, color);
	}

	/** GUI sprite (textures/gui/sprites/...), nine-sliced or stretched as its .mcmeta says. */
	public void blitSprite(Identifier sprite, int x, int y, int w, int h, int color) {
		raw.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, color);
	}

	public void item(ItemStack stack, int x, int y) {
		raw.renderItem(stack, x, y);
	}

	public void enableScissor(int x1, int y1, int x2, int y2) { raw.enableScissor(x1, y1, x2, y2); }
	public void disableScissor() { raw.disableScissor(); }

	/** Everything drawn after this goes above what was drawn before (popovers). */
	public void nextStratum() { raw.nextStratum(); }

	public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
		raw.setTooltipForNextFrame(font, text, x, y);
	}

	public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> lines, int x, int y) {
		raw.setTooltipForNextFrame(font, lines, x, y);
	}

	/** Tooltips of this frame (versions without deferred tooltips draw them here). */
	public void flushTooltips() {}

	public Pose pose() { return pose; }

	/** 2D transform stack. */
	public final class Pose {
		public void pushMatrix() { raw.pose().pushMatrix(); }
		public void popMatrix() { raw.pose().popMatrix(); }
		public void translate(float x, float y) { raw.pose().translate(x, y); }
		public void scale(float x, float y) { raw.pose().scale(x, y); }
	}
}

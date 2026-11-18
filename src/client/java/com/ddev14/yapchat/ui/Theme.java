package com.ddev14.yapchat.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Colors and drawing helpers of the Yap Chat settings UI (red, matching the mod icon). */
public final class Theme {
	private Theme() {}

	// Neutral, vanilla-friendly palette (translucent black over the blurred world); red only as an accent.
	public static final int PANEL        = 0xC0101010;
	public static final int PANEL_EDGE   = 0xFF505050;
	public static final int HEADER       = 0x90000000;
	public static final int SIDEBAR      = 0x90000000;
	public static final int ROW          = 0x90000000;
	public static final int ROW_ACTIVE   = 0xD0000000;
	public static final int SEPARATOR    = 0x30FFFFFF;

	/** Accent colors follow the "Accent color" setting (Advanced tab); see {@link #setAccent}. */
	public static final int DEFAULT_ACCENT = 0xE0564B;
	public static int ACCENT       = 0xFFE0564B;
	public static int ACCENT_HOVER = 0xFFFF6F63;
	public static int ACCENT_DARK  = 0xFFA83A32;

	/** Danger buttons stay red whatever the accent is. */
	public static final int DANGER       = 0xFFE0564B;
	public static final int DANGER_HOVER = 0xFFFF6F63;
	public static final int DANGER_DARK  = 0xFFA83A32;
	public static final int ACCENT_FAINT = 0x28FFFFFF;

	public static final int TEXT         = 0xFFFFFFFF;
	public static final int TEXT_DIM     = 0xFFAAAAAA;
	public static final int TEXT_FAINT   = 0xFF777777;

	public static final int CONTROL      = 0xFF252525;
	public static final int CONTROL_HOVER= 0xFF353535;
	public static final int CONTROL_EDGE = 0xFF5A5A5A;
	public static final int ROW_HOVER    = 0x18FFFFFF;
	public static final int SHADOW       = 0x50000000;

	/** Filled rectangle (square corners, like vanilla and Sodium). */
	public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + h, color);
	}

	/** Box with a 1px border in another color. */
	public static void framed(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int edge) {
		box(g, x, y, w, h, edge);
		box(g, x + 1, y + 1, w - 2, h - 2, fill);
	}

	/** Sets the accent from an RGB color; hover and dark shades are derived from it. */
	public static void setAccent(int rgb) {
		int base = 0xFF000000 | (rgb & 0xFFFFFF);
		ACCENT = base;
		ACCENT_HOVER = lerpColor(base, 0xFFFFFFFF, 0.2f);
		ACCENT_DARK = lerpColor(base, 0xFF000000, 0.28f);
	}

	public static int withAlpha(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static int lerpColor(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int ra = (a >> 16) & 0xFF, ga = (a >> 8) & 0xFF, ba = a & 0xFF, aa = (a >>> 24);
		int rb = (b >> 16) & 0xFF, gb = (b >> 8) & 0xFF, bb = b & 0xFF, ab = (b >>> 24);
		return (Math.round(aa + (ab - aa) * t) << 24) | (Math.round(ra + (rb - ra) * t) << 16)
				| (Math.round(ga + (gb - ga) * t) << 8) | Math.round(ba + (bb - ba) * t);
	}
}

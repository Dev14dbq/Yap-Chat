package com.ddev14.yapchat.compat;

/** ARGB color math (plain Java, same on every Minecraft version). */
public final class Argb {
	private Argb() {}

	public static int white(float alpha) {
		return color(Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f), 0xFFFFFF);
	}

	public static int color(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	public static int alpha(int argb) {
		return argb >>> 24;
	}
}

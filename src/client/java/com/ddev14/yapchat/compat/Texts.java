package com.ddev14.yapchat.compat;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/** Creating chat components (this file: 1.18.2 — before Component.literal/translatable existed). */
public final class Texts {
	private Texts() {}

	public static MutableComponent literal(String text) {
		return new TextComponent(text);
	}

	public static MutableComponent translatable(String key, Object... args) {
		return new TranslatableComponent(key, args);
	}

	public static MutableComponent empty() {
		return new TextComponent("");
	}
}

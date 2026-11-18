package com.ddev14.yapchat.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * GUI sprites for Minecraft versions without the GUI sprite atlas (before 1.20.2):
 * {@code <ns>:textures/gui/sprites/<path>.png} is drawn straight from the resources, nine-sliced or
 * stretched as its {@code .png.mcmeta} {@code gui.scaling} says, like newer versions do.
 */
public final class NineSlice {
	private NineSlice() {}

	/** Texture and its scaling; border 0 = stretch the whole texture. */
	public record Sprite(ResourceLocation texture, int texW, int texH, int left, int top, int right, int bottom) {}

	private static final Map<ResourceLocation, Optional<Sprite>> CACHE = new HashMap<>();

	/** Forget everything (resource packs changed). */
	public static void clear() {
		CACHE.clear();
	}

	public static Optional<Sprite> get(ResourceLocation sprite) {
		return CACHE.computeIfAbsent(sprite, NineSlice::load);
	}

	private static Optional<Sprite> load(ResourceLocation sprite) {
		ResourceLocation tex = Compat.id(sprite.getNamespace(), "textures/gui/sprites/" + sprite.getPath() + ".png");
		InputStream res = Compat.open(tex);
		if (res == null) return Optional.empty();
		int w, h;
		try (InputStream in = res; NativeImage img = NativeImage.read(in)) {
			w = img.getWidth();
			h = img.getHeight();
		} catch (Exception e) {
			return Optional.empty();
		}
		int l = 0, t = 0, r = 0, b = 0;
		InputStream meta = Compat.open(Compat.id(tex.getNamespace(), tex.getPath() + ".mcmeta"));
		if (meta != null) {
			try (Reader reader = new InputStreamReader(meta, StandardCharsets.UTF_8)) {
				JsonObject scaling = JsonParser.parseReader(reader).getAsJsonObject()
						.getAsJsonObject("gui").getAsJsonObject("scaling");
				if ("nine_slice".equals(scaling.get("type").getAsString())) {
					// border is given in sprite units (the "width"/"height" of the sprite); scale to pixels
					float sx = w / (float) scaling.get("width").getAsInt();
					float sy = h / (float) scaling.get("height").getAsInt();
					JsonElement border = scaling.get("border");
					if (border.isJsonPrimitive()) {
						int v = border.getAsInt();
						l = r = Math.round(v * sx);
						t = b = Math.round(v * sy);
					} else {
						JsonObject o = border.getAsJsonObject();
						l = Math.round(o.get("left").getAsInt() * sx);
						r = Math.round(o.get("right").getAsInt() * sx);
						t = Math.round(o.get("top").getAsInt() * sy);
						b = Math.round(o.get("bottom").getAsInt() * sy);
					}
				}
			} catch (Exception ignored) {
				// bad metadata: stretch
			}
		}
		return Optional.of(new Sprite(tex, w, h, l, t, r, b));
	}

	/** Draws the sprite into (x, y, w, h); borders keep their size, the rest stretches. */
	public static void draw(Gfx g, ResourceLocation sprite, int x, int y, int w, int h, int color) {
		Optional<Sprite> o = get(sprite);
		if (o.isEmpty()) return;
		Sprite s = o.get();
		int l = Math.min(s.left(), w / 2), r = Math.min(s.right(), w - l);
		int t = Math.min(s.top(), h / 2), b = Math.min(s.bottom(), h - t);
		int cw = w - l - r, ch = h - t - b;
		int tcw = s.texW() - s.left() - s.right(), tch = s.texH() - s.top() - s.bottom();
		int[] dx = {x, x + l, x + w - r}, dw = {l, cw, r};
		int[] dy = {y, y + t, y + h - b}, dh = {t, ch, b};
		int[] su = {0, s.left(), s.texW() - s.right()}, sw = {s.left(), tcw, s.right()};
		int[] sv = {0, s.top(), s.texH() - s.bottom()}, sh = {s.top(), tch, s.bottom()};
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				if (dw[col] <= 0 || dh[row] <= 0 || sw[col] <= 0 || sh[row] <= 0) continue;
				g.blit(s.texture(), dx[col], dy[row], su[col], sv[row], dw[col], dh[row], sw[col], sh[row],
						s.texW(), s.texH(), color);
			}
		}
	}
}

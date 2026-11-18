package com.ddev14.yapchat.bubble;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.Reader;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Bubble looks come from resources, so resource packs can restyle them:
 * <ul>
 *   <li>{@code assets/yap-chat/textures/gui/sprites/bubble/<style>.png} — body, nine-sliced
 *       (border set in the {@code .png.mcmeta}, like vanilla GUI sprites);</li>
 *   <li>{@code assets/yap-chat/textures/gui/sprites/bubble/<style>_tail.png} — the tail under the body;</li>
 *   <li>{@code assets/yap-chat/bubbles/<style>.json} — text color, shadow, padding, tail size.</li>
 * </ul>
 * {@code <style>} is dark, white, tooltip or nametag.
 */
public final class BubbleSkins implements SimpleSynchronousResourceReloadListener {
	private static final Logger LOG = LogManager.getLogger("yap-chat");
	public static final BubbleSkins INSTANCE = new BubbleSkins();

	/** Everything needed to draw one style. */
	public record Skin(ResourceLocation body, ResourceLocation tailSprite, int textColor, boolean textShadow,
			int padX, int padY, float minOpacity, boolean tail, int tailWidth, int tailHeight,
			int tailAnchor, int tailOverlap) {}

	private final Map<BubbleStyle, Skin> cache = new EnumMap<>(BubbleStyle.class);

	private BubbleSkins() {}

	public static Skin get(BubbleStyle style) {
		return INSTANCE.cache.computeIfAbsent(style, s -> load(s, Minecraft.getInstance().getResourceManager()));
	}

	@Override
	public ResourceLocation getFabricId() {
		return com.ddev14.yapchat.compat.Compat.id("yap-chat", "bubble_styles");
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		cache.clear();
		com.ddev14.yapchat.compat.NineSlice.clear();
	}

	private static Skin load(BubbleStyle style, ResourceManager manager) {
		String key = style.name().toLowerCase(Locale.ROOT);
		JsonObject o = new JsonObject();
		ResourceLocation json = com.ddev14.yapchat.compat.Compat.id("yap-chat", "bubbles/" + key + ".json");
		java.io.InputStream res = com.ddev14.yapchat.compat.Compat.open(json);
		if (res != null) {
			try (Reader r = new java.io.InputStreamReader(res, java.nio.charset.StandardCharsets.UTF_8)) {
				o = new JsonParser().parse(r).getAsJsonObject();
			} catch (Exception e) {
				LOG.warn("Bad bubble style file {}: {}", json, e.getMessage());
			}
		}
		return new Skin(
				com.ddev14.yapchat.compat.Compat.id("yap-chat", "bubble/" + key),
				com.ddev14.yapchat.compat.Compat.id("yap-chat", "bubble/" + key + "_tail"),
				color(o, "text_color", 0xFFFFFF),
				bool(o, "text_shadow", false),
				num(o, "padding_x", 4), num(o, "padding_y", 3),
				(float) dbl(o, "min_opacity", 0.0),
				bool(o, "tail", true),
				num(o, "tail_width", 6), num(o, "tail_height", 3),
				num(o, "tail_anchor", 3), num(o, "tail_overlap", 0));
	}

	private static int num(JsonObject o, String k, int def) {
		try { return o.has(k) ? o.get(k).getAsInt() : def; } catch (Exception e) { return def; }
	}

	private static double dbl(JsonObject o, String k, double def) {
		try { return o.has(k) ? o.get(k).getAsDouble() : def; } catch (Exception e) { return def; }
	}

	private static boolean bool(JsonObject o, String k, boolean def) {
		try { return o.has(k) ? o.get(k).getAsBoolean() : def; } catch (Exception e) { return def; }
	}

	private static int color(JsonObject o, String k, int def) {
		try {
			if (!o.has(k)) return def;
			String s = o.get(k).getAsString().trim().replace("#", "");
			return Integer.parseInt(s, 16) & 0xFFFFFF;
		} catch (Exception e) {
			return def;
		}
	}
}

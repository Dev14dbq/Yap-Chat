package com.ddev14.yapchat.render;

import com.ddev14.yapchat.compat.Argb;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import com.ddev14.yapchat.compat.Gfx;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Item icons that are safe everywhere. Since 26.3 items get their data components only when a
 * world is loaded, so creating an ItemStack on the title screen (e.g. settings opened from Mod Menu)
 * crashes the game. Outside a world we draw the item's flat texture straight from the resources.
 */
public final class ItemIcons {
	private ItemIcons() {}

	private record Flat(Identifier texture, int width, int height) {}

	private static final Map<Item, Optional<Flat>> FLAT = new HashMap<>();

	/** Item by id ("minecraft:paper"), or the fallback if the id is empty or unknown. */
	public static Item item(String id, Item fallback) {
		if (id != null && !id.isBlank()) {
			try {
				Item item = com.ddev14.yapchat.compat.Compat.item(com.ddev14.yapchat.compat.Compat.parseId(id.trim()));
				if (item != Items.AIR) return item;
			} catch (Exception ignored) {
				// invalid id: fall through
			}
		}
		return fallback;
	}

	/** True when a real ItemStack can be created for the item. */
	public static boolean ready(Item item) {
		return true; // items have their components from the start before Minecraft 26.3
	}

	/** A stack when possible, otherwise EMPTY (never throws). */
	public static ItemStack stack(Item item) {
		return item != Items.AIR && ready(item) ? new ItemStack(item) : ItemStack.EMPTY;
	}

	/** Display name that works without a world. */
	public static Component name(Item item) {
		return Component.translatable(item.getDescriptionId());
	}

	public static void draw(Gfx g, Item item, int x, int y) {
		draw(g, item, x, y, 0xFFFFFFFF);
	}

	public static void draw(Gfx g, Item item, int x, int y, int color) {
		if (item == null || item == Items.AIR) return;
		if (ready(item)) {
			if (Argb.alpha(color) < 8) return;
			g.item(new ItemStack(item), x, y);
			return;
		}
		flat(item).ifPresent(f -> g.blit(f.texture, x, y, 0f, 0f, 16, 16,
				f.width, f.height, color));
	}

	private static Optional<Flat> flat(Item item) {
		return FLAT.computeIfAbsent(item, it -> {
			Identifier id = com.ddev14.yapchat.compat.Compat.itemId(it);
			String ns = id.getNamespace(), path = id.getPath();
			String[] candidates = {
					"textures/item/" + path + ".png",
					"textures/block/" + path + ".png",
					"textures/block/" + path + "_front.png",
					"textures/block/" + path + "_side.png",
					"textures/block/" + path + "_top.png",
			};
			for (String c : candidates) {
				Identifier tex = com.ddev14.yapchat.compat.Compat.id(ns, c);
				Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(tex);
				if (res.isEmpty()) continue;
				try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
					// animated textures are vertical strips: draw the first 16x16 frame
					int scale = Math.max(1, img.getWidth() / 16);
					return Optional.of(new Flat(tex, img.getWidth() / scale, img.getHeight() / scale));
				} catch (Exception e) {
					return Optional.empty();
				}
			}
			return Optional.empty();
		});
	}
}

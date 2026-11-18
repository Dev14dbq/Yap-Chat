package com.ddev14.yapchat.render;

import com.ddev14.yapchat.chat.ChatLayout;
import com.ddev14.yapchat.skin.MojangSkinCache;
import com.ddev14.yapchat.skin.PlayerInfoLookup;
import net.minecraft.client.Minecraft;
import com.ddev14.yapchat.compat.Gfx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import com.ddev14.yapchat.compat.Argb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Головы игроков и декоративные иконки слева от строки чата.
 */
public final class ChatHudAvatarRenderer {
	private ChatHudAvatarRenderer() {}


	public static void renderPlayerHead(Gfx guiGraphics, int x, int y, PlayerInfo owner, float opacity) {
		if (owner == null) {
			return;
		}

		try {
			ResourceLocation skinLocation = com.ddev14.yapchat.compat.Compat.skin(owner);
			int color = Argb.white(opacity);

			ClientLevel level = Minecraft.getInstance().level;
			Player player = level != null ? level.getPlayerByUUID(com.ddev14.yapchat.compat.Compat.uuid(owner.getProfile())) : null;
			boolean upsideDown = player != null && com.ddev14.yapchat.compat.Compat.upsideDown(player);

			boolean showHat = player == null || com.ddev14.yapchat.compat.Compat.showHat(player);

			int yOffset = (upsideDown ? 8 : 0);
			int yDirection = (upsideDown ? -1 : 1);

			int av = ChatLayout.AVATAR_SIZE;
			guiGraphics.blit(skinLocation, x, y,
					8.0f, 8 + yOffset,
					av, av,
					8, yDirection * 8,
					64, 64, color);

			if (showHat) {
				guiGraphics.blit(skinLocation, x, y,
						40.0f, 8 + yOffset,
						av, av,
						8, yDirection * 8,
						64, 64, color);
			}
		} catch (Exception ignored) {
		}
	}

	public static void renderPlayerHeadByName(Gfx guiGraphics, int x, int y, String playerName, float opacity) {
		if (playerName == null || playerName.isEmpty()) {
			renderDefaultSteveHead(guiGraphics, x, y, opacity);
			return;
		}

		PlayerInfo info = PlayerInfoLookup.getPlayerInfoByName(playerName);
		if (info != null) {
			renderPlayerHead(guiGraphics, x, y, info, opacity);
			return;
		}

		if (MojangSkinCache.getCachedSkinUrl(playerName) != null) {
			renderDefaultSteveHead(guiGraphics, x, y, opacity);
		} else {
			MojangSkinCache.requestSkinLoad(playerName);
			renderDefaultSteveHead(guiGraphics, x, y, opacity);
		}
	}

	public static void renderDefaultSteveHead(Gfx guiGraphics, int x, int y, float opacity) {
		ResourceLocation steveSkin = com.ddev14.yapchat.compat.Compat.id("yap-chat", "steve.png");
		int color = Argb.white(opacity);
		int av = ChatLayout.AVATAR_SIZE;

		guiGraphics.blit(steveSkin, x, y,
				8.0f, 8.0f,
				av, av,
				8, 8,
				64, 64, color);

		guiGraphics.blit(steveSkin, x, y,
				40.0f, 8.0f,
				av, av,
				8, 8,
				64, 64, color);
	}

	public static void renderBarrierIcon(Gfx guiGraphics, int x, int y, float alpha) {
		int iconY = y - 1;
		int color = Argb.white(alpha);
		ItemIcons.draw(guiGraphics, Items.BARRIER, x, iconY, color);
	}

	public static void renderEmeraldIcon(Gfx guiGraphics, int x, int y, float alpha) {
		int iconY = y - 1;
		int color = Argb.white(alpha);
		ItemIcons.draw(guiGraphics, Items.EMERALD, x, iconY, color);
	}

	public static void renderNetheriteIcon(Gfx guiGraphics, int x, int y, float alpha) {
		int iconY = y - 1;
		int color = Argb.white(alpha);
		ItemIcons.draw(guiGraphics, Items.NETHERITE_INGOT, x, iconY, color);
	}

	public static void renderClockIcon(Gfx guiGraphics, int x, int y, float alpha) {
		int iconY = y - 1;
		int color = Argb.white(alpha);
		ItemIcons.draw(guiGraphics, Items.CLOCK, x, iconY, color);
	}

	public static void renderStickIcon(Gfx guiGraphics, int x, int y, float alpha) {
		int iconY = y - 1;
		int color = Argb.white(alpha);
		ItemIcons.draw(guiGraphics, Items.STICK, x, iconY, color);
	}

	public static void renderBedIcon(Gfx guiGraphics, int x, int y, float alpha) {
		int iconY = y - 1;
		int color = Argb.white(alpha);
		ItemIcons.draw(guiGraphics, Items.RED_BED, x, iconY, color);
	}

	public static void renderCommandBlockIcon(Gfx guiGraphics, int x, int y, float alpha) {
		int iconY = y - 1;
		int color = Argb.white(alpha);
		ItemIcons.draw(guiGraphics, Items.COMMAND_BLOCK, x, iconY, color);
	}

	/**
	 * Рендерит иконку по Minecraft item ID (например "minecraft:diamond").
	 * Если ID невалиден или предмет не найден — рендерит fallback.
	 */
	public static void renderIconById(Gfx guiGraphics, int x, int y, float alpha,
			String itemId, Item fallback) {
		ItemIcons.draw(guiGraphics, ItemIcons.item(itemId, fallback), x, y - 1, Argb.white(alpha));
	}

	public static void renderCameraIcon(Gfx guiGraphics, int x, int y, float alpha) {
		ResourceLocation cameraTexture = com.ddev14.yapchat.compat.Compat.id("yap-chat", "camera.png");
		int color = Argb.white(alpha);
		int av = ChatLayout.AVATAR_SIZE;
		guiGraphics.blit(cameraTexture, x, y,
				0.0f, 0.0f,
				av, av,
				av, av,
				av, av, color);
	}
}

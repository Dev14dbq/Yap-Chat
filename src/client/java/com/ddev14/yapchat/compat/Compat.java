package com.ddev14.yapchat.compat;

import com.ddev14.yapchat.mixin.client.GameRendererAccessor;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Small game calls whose names differ between Minecraft versions (this file: 1.18.2). */
public final class Compat {
	private Compat() {}

	public static Screen screen() {
		return Minecraft.getInstance().screen;
	}

	public static void setScreen(Screen screen) {
		Minecraft.getInstance().setScreen(screen);
	}

	public static ChatComponent chat() {
		Minecraft mc = Minecraft.getInstance();
		return mc.gui == null ? null : mc.gui.getChat();
	}

	public static void openFolder(Path path) {
		Util.getPlatform().openFile(path.toFile());
	}

	public static ResourceLocation id(String namespace, String path) {
		return new ResourceLocation(namespace, path);
	}

	public static ResourceLocation parseId(String id) {
		return new ResourceLocation(id);
	}

	public static Item item(ResourceLocation id) {
		return Registry.ITEM.get(id);
	}

	public static ResourceLocation itemId(Item item) {
		return Registry.ITEM.getKey(item);
	}

	public static Set<ResourceLocation> itemIds() {
		return Registry.ITEM.keySet();
	}

	public static String name(GameProfile profile) {
		return profile.getName();
	}

	public static UUID uuid(GameProfile profile) {
		return profile.getId();
	}

	public static Vec3 cameraPos() {
		return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
	}

	public static ResourceLocation skin(PlayerInfo info) {
		return info.getSkinLocation();
	}

	public static boolean upsideDown(Player player) {
		return LivingEntityRenderer.isEntityUpsideDown(player);
	}

	/** The vanilla chat options (shown on our Minecraft tab). */
	public static net.minecraft.client.Option[] chatOptions(net.minecraft.client.Options options) {
		return ((com.ddev14.yapchat.mixin.client.ChatOptionsScreenAccessor)
				new net.minecraft.client.gui.screens.ChatOptionsScreen(null, options)).yapchat$smallOptions();
	}

	/** A resource's data, or null if it does not exist. */
	public static java.io.InputStream open(ResourceLocation id) {
		try {
			var manager = Minecraft.getInstance().getResourceManager();
			return manager.hasResource(id) ? manager.getResource(id).getInputStream() : null;
		} catch (java.io.IOException e) {
			return null;
		}
	}

	public static boolean chatLinks() {
		return Minecraft.getInstance().options.chatLinks;
	}

	public static boolean showHat(Player player) {
		return player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT);
	}

	/** Sends a chat command; a leading slash is optional. */
	public static void sendCommand(String command) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		mc.player.chat(command.startsWith("/") ? command : "/" + command);
	}

	public static void drawPlayer(Gfx g, int x1, int y1, int x2, int y2, int size, float yOffset,
			float mouseX, float mouseY, LivingEntity entity) {
		int x = (x1 + x2) / 2;
		InventoryScreen.renderEntityInInventory(x, y2, size, x - mouseX, y2 - size * 1.4f - mouseY, entity);
	}

	/**
	 * Screen position (GUI pixels) of a point in the world, or null if it is behind the camera.
	 * Plain math, the same view rotation as LevelRenderer (pitch, then yaw + 180) and a perspective
	 * projection with the current field of view.
	 */
	public static float[] project(Vec3 world, float partialTick, int screenW, int screenH) {
		Minecraft mc = Minecraft.getInstance();
		Camera cam = mc.gameRenderer.getMainCamera();
		double fov = ((GameRendererAccessor) mc.gameRenderer).yapchat$getFov(cam, partialTick, true);
		Vec3 cp = cam.getPosition();
		double x = world.x - cp.x, y = world.y - cp.y, z = world.z - cp.z;
		double yaw = Math.toRadians(cam.getYRot() + 180f), pitch = Math.toRadians(cam.getXRot());
		double x1 = Math.cos(yaw) * x + Math.sin(yaw) * z;
		double z1 = -Math.sin(yaw) * x + Math.cos(yaw) * z;
		double y2 = Math.cos(pitch) * y - Math.sin(pitch) * z1;
		double z2 = Math.sin(pitch) * y + Math.cos(pitch) * z1;
		double depth = -z2;
		if (depth < 0.05) return null;
		double f = 1.0 / Math.tan(Math.toRadians(fov) / 2.0);
		double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
		double nx = f / aspect * x1 / depth, ny = f * y2 / depth;
		return new float[] {(float) ((nx + 1) * 0.5 * screenW), (float) ((1 - ny) * 0.5 * screenH)};
	}
}

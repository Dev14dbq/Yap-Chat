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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/** Small game calls whose names differ between Minecraft versions (this file: 1.21.1). */
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
		Util.getPlatform().openPath(path);
	}

	public static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	public static ResourceLocation parseId(String id) {
		return ResourceLocation.parse(id);
	}

	public static Item item(ResourceLocation id) {
		return BuiltInRegistries.ITEM.get(id);
	}

	public static ResourceLocation itemId(Item item) {
		return BuiltInRegistries.ITEM.getKey(item);
	}

	public static Set<ResourceLocation> itemIds() {
		return BuiltInRegistries.ITEM.keySet();
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
		return info.getSkin().texture();
	}

	public static boolean upsideDown(Player player) {
		return LivingEntityRenderer.isEntityUpsideDown(player);
	}

	public static boolean showHat(Player player) {
		return player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT);
	}

	/** Sends a chat command; a leading slash is optional. */
	public static void sendCommand(String command) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
	}

	public static void drawPlayer(Gfx g, int x1, int y1, int x2, int y2, int size, float yOffset,
			float mouseX, float mouseY, LivingEntity entity) {
		InventoryScreen.renderEntityInInventoryFollowsMouse(g.raw, x1, y1, x2, y2, size, yOffset, mouseX, mouseY, entity);
	}

	/**
	 * Screen position (GUI pixels) of a point in the world, or null if it is behind the camera.
	 */
	public static float[] project(Vec3 world, float partialTick, int screenW, int screenH) {
		Minecraft mc = Minecraft.getInstance();
		Camera cam = mc.gameRenderer.getMainCamera();
		double fov = ((GameRendererAccessor) mc.gameRenderer).yapchat$getFov(cam, partialTick, true);
		Matrix4f proj = mc.gameRenderer.getProjectionMatrix(fov);
		Matrix4f view = new Matrix4f().rotation(cam.rotation().conjugate(new Quaternionf()));
		Vec3 cp = cam.getPosition();
		Vector4f v = new Vector4f((float) (world.x - cp.x), (float) (world.y - cp.y), (float) (world.z - cp.z), 1f);
		v.mul(view).mul(proj);
		if (v.w < 0.05f) return null;
		return new float[] {(v.x / v.w + 1f) * 0.5f * screenW, (1f - v.y / v.w) * 0.5f * screenH};
	}
}

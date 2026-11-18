package com.ddev14.yapchat.bubble;

import com.ddev14.yapchat.YapChatHud;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Speech bubbles above players who just wrote in public chat.
 * Bubbles are drawn on the HUD at the projected head position, so they need no world-render hooks.
 */
public final class ChatBubbles {
	private ChatBubbles() {}

	private record Bubble(Component text, long time) {}

	private static final int MAX_PER_PLAYER = 3;
	private static final long FADE_IN_MS = 150, FADE_OUT_MS = 400;
	private static final Map<UUID, Deque<Bubble>> BUBBLES = new HashMap<>();

	/** Adds a bubble for a player. The id may be null; then the player is looked up by name. */
	public static void add(UUID id, String name, Component body) {
		if (body == null || body.getString().isBlank()) return;
		Minecraft mc = Minecraft.getInstance();
		if (id == null && name != null && mc.level != null) {
			for (Player p : mc.level.players()) {
				if (p.getGameProfile().name().equalsIgnoreCase(name)) {
					id = p.getUUID();
					break;
				}
			}
		}
		if (id == null) return;
		Deque<Bubble> list = BUBBLES.computeIfAbsent(id, k -> new ArrayDeque<>());
		list.addLast(new Bubble(body, System.currentTimeMillis()));
		while (list.size() > MAX_PER_PLAYER) list.removeFirst();
	}

	public static void clear() {
		BUBBLES.clear();
	}

	private record Placed(float x, float y, float scale, double dist, Deque<Bubble> bubbles) {}

	public static void render(YapChatHud hud, GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		long life = hud.getBubbleSeconds() * 1000L;
		BUBBLES.values().forEach(d -> d.removeIf(b -> now - b.time > life));
		BUBBLES.values().removeIf(Deque::isEmpty);
		if (BUBBLES.isEmpty() || !hud.isEnabled() || !hud.isBubblesEnabled()) return;
		if (mc.level == null || mc.player == null) return; // HUD layers are skipped anyway when the GUI is hidden (F1)

		Camera cam = mc.gameRenderer.getMainCamera();
		Matrix4f viewProj = cam.getViewRotationProjectionMatrix(new Matrix4f());
		Vec3 cp = cam.position();
		float pt = delta.getGameTimeDeltaPartialTick(false);
		int sw = g.guiWidth(), sh = g.guiHeight();

		List<Placed> placed = new ArrayList<>();
		for (Map.Entry<UUID, Deque<Bubble>> e : BUBBLES.entrySet()) {
			Player p = mc.level.getPlayerByUUID(e.getKey());
			if (p == null) continue;
			boolean self = p == mc.player;
			if (self && (!hud.isBubblesShowOwn() || mc.options.getCameraType().isFirstPerson())) continue;
			if (!self && p.isInvisibleTo(mc.player)) continue;
			Vec3 head = p.getPosition(pt).add(0, p.getBbHeight() + (self ? 0.35 : 0.75), 0);
			double dist = head.distanceTo(cp);
			if (dist > hud.getBubbleDistance()) continue;

			Vector4f v = new Vector4f((float) (head.x - cp.x), (float) (head.y - cp.y), (float) (head.z - cp.z), 1f).mul(viewProj);
			if (v.w < 0.05f) continue; // behind the camera
			float x = (v.x / v.w + 1f) * 0.5f * sw;
			float y = (1f - v.y / v.w) * 0.5f * sh;
			if (x < -200 || x > sw + 200 || y < -100 || y > sh + 100) continue;
			float scale = Mth.clamp(8f / (float) Math.max(dist, 1.0), 0.45f, 1f) * hud.getBubbleScale() / 100f;
			placed.add(new Placed(x, y, scale, dist, e.getValue()));
		}
		placed.sort(Comparator.comparingDouble(Placed::dist).reversed()); // far first, near on top

		for (Placed pl : placed) {
			g.pose().pushMatrix();
			g.pose().translate(pl.x, pl.y);
			g.pose().scale(pl.scale, pl.scale);
			drawStack(g, mc.font, pl.bubbles, now, life, hud.getBubbleMaxWidth(), hud.getBubbleOpacity(), hud.getBubbleStyle());
			g.pose().popMatrix();
		}
	}

	/** Newest bubble at the bottom (with the tail), older ones stacked above and dimmer. */
	private static void drawStack(GuiGraphicsExtractor g, Font font, Deque<Bubble> bubbles, long now, long life,
			int maxWidth, float bgOpacity, BubbleStyle style) {
		int bottom = 0;
		boolean first = true;
		for (Iterator<Bubble> it = bubbles.descendingIterator(); it.hasNext(); ) {
			Bubble b = it.next();
			long age = now - b.time;
			float alpha = Math.min(1f, age / (float) FADE_IN_MS) * Math.min(1f, (life - age) / (float) FADE_OUT_MS);
			if (!first) alpha *= 0.7f;
			int h = drawBubble(g, font, 0, bottom, b.text, maxWidth, alpha, bgOpacity, first, style);
			bottom -= h + 2;
			first = false;
		}
	}

	/**
	 * Draws one bubble whose bottom (tail tip) is at (cx, bottomY), using the style's sprites
	 * and settings from the resource packs (see {@link BubbleSkins}).
	 *
	 * @return the height used, tail included
	 */
	public static int drawBubble(GuiGraphicsExtractor g, Font font, int cx, int bottomY, Component text,
			int maxWidth, float alpha, float bgOpacity, boolean tail, BubbleStyle style) {
		BubbleSkins.Skin skin = BubbleSkins.get(style);
		List<FormattedCharSequence> lines = font.split(text, maxWidth);
		int textW = 0;
		for (FormattedCharSequence l : lines) textW = Math.max(textW, font.width(l));
		int w = textW + skin.padX() * 2;
		int h = lines.size() * 9 - 1 + skin.padY() * 2;
		boolean hasTail = tail && skin.tail() && skin.tailHeight() > 0;
		int tailH = hasTail ? skin.tailHeight() - skin.tailOverlap() : 0;
		int x = cx - w / 2, y = bottomY - tailH - h;
		if (alpha < 0.03f) return h + tailH;

		float op = Mth.clamp(bgOpacity, 0f, 1f);
		float min = Mth.clamp(skin.minOpacity(), 0f, 1f);
		int tint = ARGB.white(Mth.clamp(min + (1f - min) * op, 0.03f, 1f) * alpha);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, skin.body(), x, y, w, h, tint);
		if (hasTail) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, skin.tailSprite(), cx - skin.tailAnchor(),
					y + h - skin.tailOverlap(), skin.tailWidth(), skin.tailHeight(), tint);
		}
		int textColor = ARGB.color(Math.max(8, Math.round(alpha * 255)), skin.textColor());
		for (int i = 0; i < lines.size(); i++) {
			FormattedCharSequence l = lines.get(i);
			g.text(font, l, cx - font.width(l) / 2, y + skin.padY() + i * 9, textColor, skin.textShadow());
		}
		return h + tailH;
	}
}

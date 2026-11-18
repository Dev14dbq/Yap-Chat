package com.ddev14.yapchat.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Minimal widget base for the settings screen. The screen owns layout and routes input;
 * widgets only draw themselves and react to events inside their bounds.
 */
public abstract class UiWidget {
	public int x, y, w, h;
	public boolean visible = true;
	public boolean enabled = true;
	/** Shown when hovered for a moment; null = none. */
	public Component tooltip;

	protected UiWidget(int w, int h) {
		this.w = w;
		this.h = h;
	}

	public UiWidget at(int x, int y) {
		this.x = x;
		this.y = y;
		return this;
	}

	public boolean isHovered(double mx, double my) {
		return visible && mx >= x && my >= y && mx < x + w && my < y + h;
	}

	public abstract void render(GuiGraphicsExtractor g, Font font, int mx, int my);

	/** @return true if the click was consumed */
	public boolean mouseClicked(double mx, double my, int button) { return false; }

	public boolean mouseDragged(double mx, double my) { return false; }

	public void mouseReleased() {}

	public boolean mouseScrolled(double mx, double my, double amount) { return false; }

	public boolean keyPressed(int key, int modifiers) { return false; }

	public boolean charTyped(String text) { return false; }

	/** Called when another widget takes keyboard focus. */
	public void blur() {}
}

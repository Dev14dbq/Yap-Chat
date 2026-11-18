package com.ddev14.yapchat;

import com.ddev14.yapchat.ui.YapChatSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu opens the Yap Chat settings screen directly. */
public class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return YapChatSettingsScreen::new;
	}
}

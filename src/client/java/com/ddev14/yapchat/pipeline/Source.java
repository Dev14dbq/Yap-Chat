package com.ddev14.yapchat.pipeline;

/** Which channel a message arrived through. */
public enum Source {
	/** Player chat packet (signed or disguised); carries a vanilla chat type. */
	PLAYER_CHAT,
	/** System message sent by the server (plugins, vanilla game events, command output). */
	SERVER_SYSTEM,
	/** Message created on the client (other mods, screenshots, F3 debug). */
	CLIENT_SYSTEM
}

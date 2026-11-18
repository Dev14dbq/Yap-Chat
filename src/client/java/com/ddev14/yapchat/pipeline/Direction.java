package com.ddev14.yapchat.pipeline;

/** Direction of a private message relative to the local player. */
public enum Direction {
	/** Not a directed message (ordinary chat, system output). */
	NONE,
	/** Someone wrote to the local player. */
	INCOMING,
	/** The local player wrote to someone. */
	OUTGOING
}

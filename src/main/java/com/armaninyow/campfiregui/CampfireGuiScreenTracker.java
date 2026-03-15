package com.armaninyow.campfiregui;

import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which campfire positions currently have a CampfireManagementScreen open on this client.
 */
public final class CampfireGuiScreenTracker {
	private CampfireGuiScreenTracker() {}

	/** Positions of campfires whose GUI is currently open on this client. */
	public static final Set<BlockPos> openScreenPositions =
		Collections.newSetFromMap(new ConcurrentHashMap<>());
}
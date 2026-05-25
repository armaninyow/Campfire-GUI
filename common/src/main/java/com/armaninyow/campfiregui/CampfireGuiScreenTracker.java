package com.armaninyow.campfiregui;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which campfire positions currently have a CampfireManagementScreen open on this client.
 */
public final class CampfireGuiScreenTracker {
	private CampfireGuiScreenTracker() {}

	public static final Set<BlockPos> openScreenPositions =
		Collections.newSetFromMap(new ConcurrentHashMap<>());
}
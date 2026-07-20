package com.armaninyow.campfiregui.client;

import com.armaninyow.campfiregui.network.CampfireGuiPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CampfireGUIClient implements ClientModInitializer {

	public static final Map<BlockPos, Long> recentlyClosedScreens = new ConcurrentHashMap<>();
	private static final long CLOSE_GRACE_MS = 150;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(CampfireGuiPacket.Payload.ID, (payload, context) -> {
			context.client().execute(() -> {
				Minecraft client = context.client();
				if (client.gui.screen() instanceof CampfireManagementScreen screen
					&& screen.getPos().equals(payload.pos())) {
					screen.updateData(payload.isLit(), payload.isSoulCampfire(), payload.slots());
				} else {
					Long closedAt = recentlyClosedScreens.get(payload.pos());
					if (closedAt != null && System.currentTimeMillis() - closedAt < CLOSE_GRACE_MS) return;
					recentlyClosedScreens.remove(payload.pos());
					client.gui.setScreen(new CampfireManagementScreen(
						payload.isLit(),
						payload.isSoulCampfire(),
						payload.slots(),
						payload.pos()
					));
				}
			});
		});
	}
}
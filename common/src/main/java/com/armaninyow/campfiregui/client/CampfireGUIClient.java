package com.armaninyow.campfiregui.client;

import com.armaninyow.campfiregui.network.CampfireGuiPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

public class CampfireGUIClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// S2C: when server sends updated campfire data, update the open screen in-place
		ClientPlayNetworking.registerGlobalReceiver(CampfireGuiPacket.Payload.ID, (payload, context) -> {
			context.client().execute(() -> {
				MinecraftClient client = context.client();
				if (client.currentScreen instanceof CampfireManagementScreen screen
					&& screen.getPos().equals(payload.pos())) {
					// Screen already open for this campfire — just refresh the data
					screen.updateData(payload.isLit(), payload.isSoulCampfire(), payload.slots());
				} else {
					// First open, or a different campfire — open fresh screen
					client.setScreen(new CampfireManagementScreen(
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
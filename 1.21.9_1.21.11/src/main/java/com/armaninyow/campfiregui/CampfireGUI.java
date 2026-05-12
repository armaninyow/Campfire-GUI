package com.armaninyow.campfiregui;

import com.armaninyow.campfiregui.network.CampfireGuiPacket;
import com.armaninyow.campfiregui.network.CampfireGuiRefreshPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CampfireGUI implements ModInitializer {
	public static final String MOD_ID = "campfiregui";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CampfireGuiPacket.registerServerPackets();

		// Register the C2S refresh packet
		PayloadTypeRegistry.playC2S().register(CampfireGuiRefreshPacket.ID, CampfireGuiRefreshPacket.CODEC);

		// When the client requests a refresh, re-send the campfire data
		ServerPlayNetworking.registerGlobalReceiver(CampfireGuiRefreshPacket.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			var pos   = payload.pos();
			var world = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
			var state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof CampfireBlock)) return;
			var be = world.getBlockEntity(pos);
			if (!(be instanceof CampfireBlockEntity campfire)) return;
			CampfireGuiPacket.sendToClient(player, campfire, state, pos);
		});

		// Open GUI on shift + right-click
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
			if (!player.isSneaking()) return ActionResult.PASS;

			var pos   = hitResult.getBlockPos();
			var state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof CampfireBlock)) return ActionResult.PASS;

			// Cancel vanilla interaction on both sides
			if (world.isClient()) return ActionResult.SUCCESS;

			var blockEntity = world.getBlockEntity(pos);
			if (!(blockEntity instanceof CampfireBlockEntity campfire)) return ActionResult.PASS;

			if (player instanceof ServerPlayerEntity serverPlayer) {
				CampfireGuiPacket.sendToClient(serverPlayer, campfire, state, pos);
			}
			return ActionResult.SUCCESS;
		});
	}
}
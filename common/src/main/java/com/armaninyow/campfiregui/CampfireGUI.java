package com.armaninyow.campfiregui;

import com.armaninyow.campfiregui.network.CampfireGuiPacket;
import com.armaninyow.campfiregui.network.CampfireGuiRefreshPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CampfireGUI implements ModInitializer {
	public static final String MOD_ID = "campfiregui";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CampfireGuiPacket.registerServerPackets();

		PayloadTypeRegistry.serverboundPlay().register(CampfireGuiRefreshPacket.ID, CampfireGuiRefreshPacket.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(CampfireGuiRefreshPacket.ID, (payload, context) -> {
			ServerPlayer player = context.player();
			var pos   = payload.pos();
			var world = (net.minecraft.server.level.ServerLevel) player.level();
			var state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof CampfireBlock)) return;
			var be = world.getBlockEntity(pos);
			if (!(be instanceof CampfireBlockEntity campfire)) return;
			CampfireGuiPacket.sendToClient(player, campfire, state, pos);
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (!player.isShiftKeyDown()) return InteractionResult.PASS;

			var pos   = hitResult.getBlockPos();
			var state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof CampfireBlock)) return InteractionResult.PASS;

			if (world.isClientSide()) return InteractionResult.SUCCESS;

			var blockEntity = world.getBlockEntity(pos);
			if (!(blockEntity instanceof CampfireBlockEntity campfire)) return InteractionResult.PASS;

			if (player instanceof ServerPlayer serverPlayer) {
				CampfireGuiPacket.sendToClient(serverPlayer, campfire, state, pos);
			}
			return InteractionResult.SUCCESS;
		});
	}
}
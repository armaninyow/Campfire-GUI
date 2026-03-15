package com.armaninyow.campfiregui.network;

import com.armaninyow.campfiregui.CampfireGUI;
import com.armaninyow.campfiregui.mixin.CampfireBlockEntityAccessor;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class CampfireGuiPacket {

	public static final Identifier OPEN_GUI_ID = Identifier.of(CampfireGUI.MOD_ID, "open_gui");

	/**
	 * Slim over-the-wire representation of a campfire slot.
	 * We avoid ItemStack.PACKET_CODEC (requires RegistryByteBuf) by sending
	 * only the item's registry ID string and count — enough to reconstruct a
	 * display-only ItemStack on the client.
	 */
	public record SlotInfo(String itemId, int count, int cookingTime, int cookingTotalTime) {

		/** Reconstruct a display-only ItemStack from the received data. */
		public ItemStack toItemStack() {
			if (itemId.isEmpty()) return ItemStack.EMPTY;
			try {
				Item item = Registries.ITEM.get(Identifier.of(itemId));
				return new ItemStack(item, count);
			} catch (Exception ignored) {
				return ItemStack.EMPTY;
			}
		}
	}

	public record Payload(
		boolean isLit,
		boolean isSoulCampfire,
		List<SlotInfo> slots,
		BlockPos pos
	) implements CustomPayload {
		public static final Id<Payload> ID = new Id<>(OPEN_GUI_ID);

		public static final PacketCodec<PacketByteBuf, Payload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeBoolean(value.isLit);
				buf.writeBoolean(value.isSoulCampfire);
				buf.writeInt(value.slots.size());
				for (SlotInfo slot : value.slots) {
					buf.writeString(slot.itemId());
					buf.writeInt(slot.count());
					buf.writeInt(slot.cookingTime());
					buf.writeInt(slot.cookingTotalTime());
				}
				buf.writeBlockPos(value.pos);
			},
			buf -> {
				boolean isLit          = buf.readBoolean();
				boolean isSoulCampfire = buf.readBoolean();
				int count = buf.readInt();
				List<SlotInfo> slots = new ArrayList<>();
				for (int i = 0; i < count; i++) {
					String itemId        = buf.readString();
					int    itemCount     = buf.readInt();
					int    cookingTime   = buf.readInt();
					int    cookingTotal  = buf.readInt();
					slots.add(new SlotInfo(itemId, itemCount, cookingTime, cookingTotal));
				}
				return new Payload(isLit, isSoulCampfire, slots, buf.readBlockPos());
			}
		);

		@Override
		public Id<? extends CustomPayload> getId() { return ID; }
	}

	public static void registerServerPackets() {
		PayloadTypeRegistry.playS2C().register(Payload.ID, Payload.CODEC);
	}

	public static void sendToClient(ServerPlayerEntity player, CampfireBlockEntity campfire, BlockState state, BlockPos pos) {
		boolean isLit          = false;
		boolean isSoulCampfire = state.getBlock() == net.minecraft.block.Blocks.SOUL_CAMPFIRE;
		try { isLit = state.get(CampfireBlock.LIT); } catch (Exception ignored) {}

		CampfireBlockEntityAccessor accessor = (CampfireBlockEntityAccessor) campfire;
		int[] cookingTimes      = accessor.campfiregui$getCookingTimes();
		int[] cookingTotalTimes = accessor.campfiregui$getCookingTotalTimes();
		var   items             = campfire.getItemsBeingCooked();

		List<SlotInfo> slots = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			ItemStack stack = (items != null && i < items.size()) ? items.get(i) : ItemStack.EMPTY;
			int time      = (cookingTimes != null && i < cookingTimes.length)           ? cookingTimes[i]      : 0;
			int totalTime = (cookingTotalTimes != null && i < cookingTotalTimes.length) ? cookingTotalTimes[i] : 0;
			String itemId = stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).toString();
			slots.add(new SlotInfo(itemId, stack.getCount(), time, totalTime));
		}

		ServerPlayNetworking.send(player, new Payload(isLit, isSoulCampfire, slots, pos));
	}
}
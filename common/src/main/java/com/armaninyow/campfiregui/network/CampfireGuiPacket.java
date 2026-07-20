package com.armaninyow.campfiregui.network;

import com.armaninyow.campfiregui.CampfireGUI;
import com.armaninyow.campfiregui.mixin.CampfireBlockEntityAccessor;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CampfireGuiPacket {

	public static final Identifier OPEN_GUI_ID = Identifier.fromNamespaceAndPath(CampfireGUI.MOD_ID, "open_gui");

	public record SlotInfo(String itemId, int count, int cookingTime, int cookingTotalTime) {

		public ItemStack toItemStack() {
			if (itemId.isEmpty()) return ItemStack.EMPTY;
			try {
				Optional<? extends net.minecraft.core.Holder<Item>> holder =
					BuiltInRegistries.ITEM.get(Identifier.parse(itemId));
				if (holder.isEmpty()) return ItemStack.EMPTY;
				return new ItemStack(holder.get().value(), count);
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
	) implements CustomPacketPayload {
		public static final Type<Payload> ID = new Type<>(OPEN_GUI_ID);

		public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeBoolean(value.isLit);
				buf.writeBoolean(value.isSoulCampfire);
				buf.writeInt(value.slots.size());
				for (SlotInfo slot : value.slots) {
					buf.writeUtf(slot.itemId());
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
					String itemId       = buf.readUtf();
					int    itemCount    = buf.readInt();
					int    cookingTime  = buf.readInt();
					int    cookingTotal = buf.readInt();
					slots.add(new SlotInfo(itemId, itemCount, cookingTime, cookingTotal));
				}
				return new Payload(isLit, isSoulCampfire, slots, buf.readBlockPos());
			}
		);

		@Override
		public Type<? extends CustomPacketPayload> type() { return ID; }
	}

	public static void registerServerPackets() {
		PayloadTypeRegistry.clientboundPlay().register(Payload.ID, Payload.CODEC);
	}

	public static void sendToClient(ServerPlayer player, CampfireBlockEntity campfire, BlockState state, BlockPos pos) {
		boolean isLit          = false;
		boolean isSoulCampfire = state.getBlock() == Blocks.SOUL_CAMPFIRE;
		try { isLit = state.getValue(CampfireBlock.LIT); } catch (Exception ignored) {}

		CampfireBlockEntityAccessor accessor = (CampfireBlockEntityAccessor) campfire;
		int[]       cookingTimes      = accessor.campfiregui$getCookingTimes();
		int[]       cookingTotalTimes = accessor.campfiregui$getCookingTotalTimes();
		NonNullList<ItemStack> items = accessor.campfiregui$getItems();

		List<SlotInfo> slots = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			ItemStack stack = (items != null && i < items.size()) ? items.get(i) : ItemStack.EMPTY;
			int time      = (cookingTimes != null && i < cookingTimes.length)           ? cookingTimes[i]      : 0;
			int totalTime = (cookingTotalTimes != null && i < cookingTotalTimes.length) ? cookingTotalTimes[i] : 0;
			String itemId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			slots.add(new SlotInfo(itemId, stack.getCount(), time, totalTime));
		}

		ServerPlayNetworking.send(player, new Payload(isLit, isSoulCampfire, slots, pos));
	}
}
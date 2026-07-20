package com.armaninyow.campfiregui.network;

import com.armaninyow.campfiregui.CampfireGUI;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;

public record CampfireGuiRefreshPacket(BlockPos pos) implements CustomPacketPayload {

	public static final Identifier REFRESH_ID = Identifier.fromNamespaceAndPath(CampfireGUI.MOD_ID, "refresh_gui");
	public static final Type<CampfireGuiRefreshPacket> ID = new Type<>(REFRESH_ID);

	public static final StreamCodec<FriendlyByteBuf, CampfireGuiRefreshPacket> CODEC = StreamCodec.of(
		(buf, value) -> buf.writeBlockPos(value.pos),
		buf -> new CampfireGuiRefreshPacket(buf.readBlockPos())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() { return ID; }
}
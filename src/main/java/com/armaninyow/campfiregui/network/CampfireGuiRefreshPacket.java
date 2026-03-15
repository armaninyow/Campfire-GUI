package com.armaninyow.campfiregui.network;

import com.armaninyow.campfiregui.CampfireGUI;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Sent client→server to request a fresh CampfireGuiPacket.Payload for the given pos.
 * The screen sends this every second while it is open.
 */
public record CampfireGuiRefreshPacket(BlockPos pos) implements CustomPayload {

	public static final Identifier REFRESH_ID = Identifier.of(CampfireGUI.MOD_ID, "refresh_gui");
	public static final Id<CampfireGuiRefreshPacket> ID = new Id<>(REFRESH_ID);

	public static final PacketCodec<PacketByteBuf, CampfireGuiRefreshPacket> CODEC = PacketCodec.of(
		(value, buf) -> buf.writeBlockPos(value.pos),
		buf -> new CampfireGuiRefreshPacket(buf.readBlockPos())
	);

	@Override
	public Id<? extends CustomPayload> getId() { return ID; }
}
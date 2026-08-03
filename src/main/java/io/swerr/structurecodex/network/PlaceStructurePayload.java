package io.swerr.structurecodex.network;

import io.netty.buffer.ByteBuf;
import io.swerr.structurecodex.StructureCodex;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PlaceStructurePayload(Identifier structure, boolean blend, int distance)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlaceStructurePayload> TYPE =
            new CustomPacketPayload.Type<>(StructureCodex.id("place_structure"));

    public static final StreamCodec<ByteBuf, PlaceStructurePayload> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, PlaceStructurePayload::structure,
            ByteBufCodecs.BOOL, PlaceStructurePayload::blend,
            ByteBufCodecs.VAR_INT, PlaceStructurePayload::distance,
            PlaceStructurePayload::new);

    public PlaceStructurePayload {
        distance = Math.clamp(distance, 0, 64);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

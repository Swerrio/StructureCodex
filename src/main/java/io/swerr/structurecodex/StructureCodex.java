package io.swerr.structurecodex;

import io.swerr.structurecodex.network.PlaceStructurePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StructureCodex implements ModInitializer {

    public static final String MOD_ID = "structurecodex";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(PlaceStructurePayload.TYPE, PlaceStructurePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PlaceStructurePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> handlePlacement(player, payload));
        });

        LOGGER.info("Structure Codex initialized");
    }

    private static void handlePlacement(ServerPlayer player, PlaceStructurePayload payload) {
        Identifier structure = payload.structure();
        StructurePlacer.Placement placement;
        try {
            placement = StructurePlacer.place(player, structure, payload.blend(), payload.distance());
        } catch (Exception exception) {
            LOGGER.error("Could not place {}", structure, exception);
            player.sendSystemMessage(Component.translatable("structurecodex.place.crashed", structure.toString()));
            return;
        }
        BlockPos pos = placement.where();

        Component message = switch (placement.result()) {
            case PLACED -> Component.translatable("structurecodex.place.success",
                    structure.toString(), pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            case NO_PERMISSION -> Component.translatable("structurecodex.place.no_permission");
            case UNKNOWN_STRUCTURE -> Component.translatable("structurecodex.place.unknown_structure",
                    structure.toString());
            case GENERATION_FAILED -> Component.translatable("structurecodex.place.failed", structure.toString());
            case CHUNKS_NOT_LOADED -> Component.translatable("structurecodex.place.not_loaded");
        };

        player.sendSystemMessage(message);
    }
}

package io.swerr.structurecodex.catalog;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Locale;

public record StructureEntry(Identifier id,
                             StructureCategory category,
                             StructureInfo info,
                             Structure structure) {

    public static StructureEntry ofRegistry(ResourceKey<Structure> key, Structure structure) {
        Identifier id = key.identifier();
        Identifier typeId = BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());

        StructureInfo info = new StructureInfo(
                typeId == null ? "" : typeId.toString(),
                structure.step().getSerializedName(),
                structure.terrainAdaptation().getSerializedName(),
                structure.spawnOverrides().size(),
                structure.biomes().size());

        return new StructureEntry(id, StructureCategory.of(id), info, structure);
    }

    public static StructureEntry ofData(Identifier id, StructureInfo info) {
        return new StructureEntry(id, StructureCategory.of(id), info, null);
    }

    public boolean canGenerate() {
        return structure != null;
    }

    public String displayName() {
        String path = id.getPath();
        StringBuilder builder = new StringBuilder(path.length());
        boolean capitalise = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_' || c == '/') {
                builder.append(' ');
                capitalise = true;
            } else if (capitalise) {
                builder.append(Character.toUpperCase(c));
                capitalise = false;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public boolean isVanilla() {
        return "minecraft".equals(id.getNamespace());
    }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT).trim();
        return id.getPath().contains(needle)
                || id.getNamespace().contains(needle)
                || displayName().toLowerCase(Locale.ROOT).contains(needle);
    }
}

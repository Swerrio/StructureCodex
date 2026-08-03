package io.swerr.structurecodex.catalog;

public record StructureInfo(String type,
                            String step,
                            String terrainAdaptation,
                            int spawnOverrides,
                            int biomeCount) {

    public static final StructureInfo UNKNOWN = new StructureInfo("", "", "", 0, 0);
}

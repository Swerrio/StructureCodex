package io.swerr.structurecodex.catalog;

import io.swerr.structurecodex.StructureCodex;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Set;

public enum StructureCategory {

    ALL("all"),
    VILLAGES("villages"),
    SURFACE("surface"),
    UNDERGROUND("underground"),
    OCEAN("ocean"),
    NETHER("nether"),
    END("end"),
    RUINED_PORTALS("ruined_portals"),
    OTHER("other");

    public static final List<StructureCategory> TABS = List.of(
            ALL, VILLAGES, SURFACE, UNDERGROUND, OCEAN, NETHER, END, RUINED_PORTALS, OTHER);

    private static final Map<String, StructureCategory> VANILLA = Map.ofEntries(
            Map.entry("village_plains", VILLAGES),
            Map.entry("village_desert", VILLAGES),
            Map.entry("village_savanna", VILLAGES),
            Map.entry("village_snowy", VILLAGES),
            Map.entry("village_taiga", VILLAGES),

            Map.entry("desert_pyramid", SURFACE),
            Map.entry("jungle_pyramid", SURFACE),
            Map.entry("igloo", SURFACE),
            Map.entry("swamp_hut", SURFACE),
            Map.entry("mansion", SURFACE),
            Map.entry("pillager_outpost", SURFACE),
            Map.entry("trail_ruins", SURFACE),

            Map.entry("mineshaft", UNDERGROUND),
            Map.entry("mineshaft_mesa", UNDERGROUND),
            Map.entry("stronghold", UNDERGROUND),
            Map.entry("ancient_city", UNDERGROUND),
            Map.entry("trial_chambers", UNDERGROUND),

            Map.entry("monument", OCEAN),
            Map.entry("shipwreck", OCEAN),
            Map.entry("shipwreck_beached", OCEAN),
            Map.entry("ocean_ruin_cold", OCEAN),
            Map.entry("ocean_ruin_warm", OCEAN),
            Map.entry("buried_treasure", OCEAN),

            Map.entry("fortress", NETHER),
            Map.entry("bastion_remnant", NETHER),
            Map.entry("nether_fossil", NETHER),

            Map.entry("end_city", END));

    private static final Set<String> UNDERGROUND_HINTS = Set.of(
            "cave", "mine", "dungeon", "catacomb", "crypt", "tunnel", "underground", "deep");

    private static final Set<String> OCEAN_HINTS = Set.of(
            "ocean", "sea", "ship", "wreck", "reef", "coral", "underwater", "aquatic");

    private static final Set<String> VILLAGE_HINTS = Set.of(
            "village", "town", "settlement", "hamlet");

    private static final Set<String> SURFACE_HINTS = Set.of(
            "outpost", "tower", "camp", "hut", "temple", "pyramid", "ruin", "mansion");

    private final String key;

    StructureCategory(String key) {
        this.key = key;
    }

    public String translationKey() {
        return StructureCodex.MOD_ID + ".category." + key;
    }

    public String key() {
        return key;
    }

    public static StructureCategory of(Identifier location) {
        String path = location.getPath();

        if (path.startsWith("ruined_portal")) {
            return RUINED_PORTALS;
        }

        if ("minecraft".equals(location.getNamespace())) {
            StructureCategory known = VANILLA.get(path);
            return known != null ? known : OTHER;
        }

        return heuristic(path);
    }

    private static StructureCategory heuristic(String path) {
        if (matchesAny(path, VILLAGE_HINTS)) {
            return VILLAGES;
        }
        if (path.contains("nether") || path.contains("bastion") || path.contains("fortress")) {
            return NETHER;
        }
        if (path.contains("end_") || path.startsWith("end") || path.contains("chorus")) {
            return END;
        }
        if (matchesAny(path, OCEAN_HINTS)) {
            return OCEAN;
        }
        if (matchesAny(path, UNDERGROUND_HINTS)) {
            return UNDERGROUND;
        }
        if (matchesAny(path, SURFACE_HINTS)) {
            return SURFACE;
        }
        return OTHER;
    }

    private static boolean matchesAny(String path, Set<String> hints) {
        for (String hint : hints) {
            if (path.contains(hint)) {
                return true;
            }
        }
        return false;
    }
}

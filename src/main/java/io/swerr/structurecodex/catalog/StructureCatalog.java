package io.swerr.structurecodex.catalog;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StructureCatalog {

    private final List<StructureEntry> all;
    private final Map<StructureCategory, List<StructureEntry>> byCategory;

    private StructureCatalog(List<StructureEntry> all, Map<StructureCategory, List<StructureEntry>> byCategory) {
        this.all = all;
        this.byCategory = byCategory;
    }

    public static Optional<StructureCatalog> fromRegistry(RegistryAccess registries) {
        Optional<Registry<Structure>> maybeRegistry = registries.lookup(Registries.STRUCTURE);
        if (maybeRegistry.isEmpty()) {
            return Optional.empty();
        }
        Registry<Structure> registry = maybeRegistry.get();

        List<StructureEntry> entries = new ArrayList<>();
        for (Map.Entry<ResourceKey<Structure>, Structure> entry : registry.entrySet()) {
            entries.add(StructureEntry.ofRegistry(entry.getKey(), entry.getValue()));
        }
        return Optional.of(assemble(entries));
    }

    public static Optional<StructureCatalog> fromBuiltInData() {
        Map<Identifier, StructureInfo> data = BuiltInStructureIndex.read();
        if (data.isEmpty()) {
            return Optional.empty();
        }

        List<StructureEntry> entries = new ArrayList<>();
        for (Map.Entry<Identifier, StructureInfo> entry : data.entrySet()) {
            entries.add(StructureEntry.ofData(entry.getKey(), entry.getValue()));
        }
        return Optional.of(assemble(entries));
    }

    private static StructureCatalog assemble(List<StructureEntry> entries) {
        entries.sort(Comparator.comparing((StructureEntry e) -> e.isVanilla() ? 0 : 1)
                .thenComparing(e -> e.id().getNamespace())
                .thenComparing(e -> e.id().getPath()));

        Map<StructureCategory, List<StructureEntry>> grouped = new EnumMap<>(StructureCategory.class);
        for (StructureCategory category : StructureCategory.TABS) {
            grouped.put(category, new ArrayList<>());
        }
        for (StructureEntry entry : entries) {
            grouped.get(StructureCategory.ALL).add(entry);
            grouped.get(entry.category()).add(entry);
        }

        Map<StructureCategory, List<StructureEntry>> frozen = new EnumMap<>(StructureCategory.class);
        for (Map.Entry<StructureCategory, List<StructureEntry>> entry : grouped.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }

        return new StructureCatalog(Collections.unmodifiableList(entries),
                Collections.unmodifiableMap(frozen));
    }

    public List<StructureEntry> all() {
        return all;
    }

    public List<StructureEntry> in(StructureCategory category) {
        return byCategory.getOrDefault(category, List.of());
    }

    public List<StructureEntry> in(StructureCategory category, String query) {
        List<StructureEntry> source = in(category);
        if (query == null || query.isBlank()) {
            return source;
        }
        List<StructureEntry> filtered = new ArrayList<>();
        for (StructureEntry entry : source) {
            if (entry.matches(query)) {
                filtered.add(entry);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    public boolean isEmpty(StructureCategory category) {
        return in(category).isEmpty();
    }

    public int count(StructureCategory category) {
        return in(category).size();
    }

    public Optional<StructureEntry> find(Identifier id) {
        for (StructureEntry entry : all) {
            if (entry.id().equals(id)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public List<StructureCategory> populatedTabs() {
        List<StructureCategory> tabs = new ArrayList<>();
        for (StructureCategory category : StructureCategory.TABS) {
            if (category == StructureCategory.ALL || !isEmpty(category)) {
                tabs.add(category);
            }
        }
        return Collections.unmodifiableList(tabs);
    }
}

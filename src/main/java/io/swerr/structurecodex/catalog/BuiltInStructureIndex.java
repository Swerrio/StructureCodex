package io.swerr.structurecodex.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swerr.structurecodex.StructureCodex;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class BuiltInStructureIndex {

    private static final String PREFIX = "worldgen/structure";
    private static final String SUFFIX = ".json";

    private BuiltInStructureIndex() {
    }

    public static Map<Identifier, StructureInfo> read() {
        Map<Identifier, StructureInfo> found = new LinkedHashMap<>();
        readVanillaPack(found);
        readModJars(found);
        return found;
    }

    private static void readModJars(Map<Identifier, StructureInfo> found) {
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            for (Path root : mod.getRootPaths()) {
                Path data = root.resolve("data");
                if (!Files.isDirectory(data)) {
                    continue;
                }
                try (Stream<Path> namespaces = Files.list(data)) {
                    namespaces.filter(Files::isDirectory).forEach(namespace -> {
                        Path folder = namespace.resolve(PREFIX);
                        if (Files.isDirectory(folder)) {
                            readStructureFolder(namespace.getFileName().toString(), folder, found);
                        }
                    });
                } catch (Exception exception) {
                    StructureCodex.LOGGER.warn("Could not scan data of mod {}", mod.getMetadata().getId(), exception);
                }
            }
        }
    }

    private static void readStructureFolder(String namespace, Path folder, Map<Identifier, StructureInfo> found) {
        try (Stream<Path> files = Files.walk(folder)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(SUFFIX))
                    .forEach(path -> {
                        String relative = folder.relativize(path).toString().replace('\\', '/');
                        String name = relative.substring(0, relative.length() - SUFFIX.length());
                        try (InputStream stream = Files.newInputStream(path)) {
                            JsonElement parsed = JsonParser.parseReader(
                                    new InputStreamReader(stream, StandardCharsets.UTF_8));
                            if (parsed.isJsonObject()) {
                                found.putIfAbsent(Identifier.fromNamespaceAndPath(namespace, name),
                                        parse(parsed.getAsJsonObject()));
                            }
                        } catch (Exception exception) {
                            StructureCodex.LOGGER.warn("Could not read structure file {}", path, exception);
                        }
                    });
        } catch (Exception exception) {
            StructureCodex.LOGGER.warn("Could not walk structure folder {}", folder, exception);
        }
    }

    private static void readVanillaPack(Map<Identifier, StructureInfo> found) {
        VanillaPackResources pack = ServerPacksSource.createVanillaPackSource();
        try {
            for (String namespace : pack.getNamespaces(PackType.SERVER_DATA)) {
                pack.listResources(PackType.SERVER_DATA, namespace, PREFIX, (id, supplier) -> {
                    if (!id.getPath().endsWith(SUFFIX)) {
                        return;
                    }
                    try (InputStream stream = supplier.get()) {
                        JsonElement parsed = JsonParser.parseReader(
                                new InputStreamReader(stream, StandardCharsets.UTF_8));
                        if (parsed.isJsonObject()) {
                            found.put(strip(id), parse(parsed.getAsJsonObject()));
                        }
                    } catch (Exception exception) {
                        StructureCodex.LOGGER.warn("Could not read structure {}", id, exception);
                    }
                });
            }
        } catch (Exception exception) {
            StructureCodex.LOGGER.error("Could not read built-in structure data", exception);
        } finally {
            pack.close();
        }
    }

    private static Identifier strip(Identifier id) {
        String path = id.getPath();
        int start = PREFIX.length() + 1;
        int end = path.length() - SUFFIX.length();
        if (start >= end) {
            return id;
        }
        return Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring(start, end));
    }

    private static StructureInfo parse(JsonObject json) {
        String type = string(json, "type", "");
        String step = string(json, "step", "");
        String terrain = string(json, "terrain_adaptation", "none");

        int spawnOverrides = 0;
        JsonElement spawns = json.get("spawn_overrides");
        if (spawns != null && spawns.isJsonObject()) {
            spawnOverrides = spawns.getAsJsonObject().size();
        }

        return new StructureInfo(type, step, terrain, spawnOverrides, countBiomes(json.get("biomes")));
    }

    private static String string(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static int countBiomes(JsonElement biomes) {
        if (biomes == null) {
            return 0;
        }
        if (biomes.isJsonArray()) {
            return biomes.getAsJsonArray().size();
        }
        return biomes.isJsonPrimitive() ? 1 : 0;
    }
}

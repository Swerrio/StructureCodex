package io.swerr.structurecodex.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.swerr.structurecodex.StructureCodex;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record CodexConfig(boolean previewInNormalOverworld,
                          int previewBlockBudget,
                          boolean blendPlacement,
                          int placeDistance) {

    public static final CodexConfig DEFAULT = new CodexConfig(true, 120000, true, 3);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve(StructureCodex.MOD_ID + ".json");

    private static volatile CodexConfig current;

    public CodexConfig {
        previewBlockBudget = Math.clamp(previewBlockBudget, 20000, 400000);
        placeDistance = Math.clamp(placeDistance, 0, 64);
    }

    public static CodexConfig get() {
        CodexConfig config = current;
        if (config == null) {
            config = load();
            current = config;
        }
        return config;
    }

    public static void set(CodexConfig config) {
        current = config;
        save(config);
    }

    private static CodexConfig load() {
        if (!Files.isRegularFile(PATH)) {
            return DEFAULT;
        }
        try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
            CodexConfig parsed = GSON.fromJson(reader, CodexConfig.class);
            return parsed == null ? DEFAULT : parsed;
        } catch (IOException | RuntimeException exception) {
            StructureCodex.LOGGER.warn("Could not read {}, using defaults", PATH, exception);
            return DEFAULT;
        }
    }

    private static void save(CodexConfig config) {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            StructureCodex.LOGGER.warn("Could not write {}", PATH, exception);
        }
    }
}

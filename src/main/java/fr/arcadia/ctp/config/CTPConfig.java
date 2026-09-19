package fr.arcadia.ctp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.arcadia.ctp.ArcadiaCTP;
import fr.arcadia.ctp.rules.Policy;
import fr.arcadia.ctp.rules.Rule;
import fr.arcadia.ctp.rules.RuleEngine;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Loads {@code config/arcadia-ctp.json} into an immutable snapshot.
 *
 * <p>Parsing is field-by-field rather than reflective binding so a typo in one rule
 * costs that rule and a log line, not the whole file. A config that fails to load
 * leaves the previous snapshot in place, which matters for {@code /arcadiactp reload}
 * on a live server.
 */
public final class CTPConfig {

    public record Snapshot(
        boolean enabled,
        boolean notifyOnDeny,
        boolean respectAdminBypass,
        boolean logDenials,
        RuleEngine engine,
        ContraptionSettings contraptions,
        WaystoneSettings waystones
    ) {
        public static Snapshot defaults() {
            return new Snapshot(true, true, true, false, RuleEngine.empty(),
                ContraptionSettings.defaults(), WaystoneSettings.defaults());
        }

        // One replaced field at a time: the panel toggles exactly one setting per click,
        // and a full constructor call at each call site invites silent argument swaps.
        public Snapshot withEnabled(boolean value) {
            return new Snapshot(value, notifyOnDeny, respectAdminBypass, logDenials,
                engine, contraptions, waystones);
        }

        public Snapshot withNotifyOnDeny(boolean value) {
            return new Snapshot(enabled, value, respectAdminBypass, logDenials,
                engine, contraptions, waystones);
        }

        public Snapshot withRespectAdminBypass(boolean value) {
            return new Snapshot(enabled, notifyOnDeny, value, logDenials,
                engine, contraptions, waystones);
        }

        public Snapshot withLogDenials(boolean value) {
            return new Snapshot(enabled, notifyOnDeny, respectAdminBypass, value,
                engine, contraptions, waystones);
        }

        public Snapshot withContraptions(ContraptionSettings value) {
            return new Snapshot(enabled, notifyOnDeny, respectAdminBypass, logDenials,
                engine, value, waystones);
        }

        public Snapshot withWaystones(WaystoneSettings value) {
            return new Snapshot(enabled, notifyOnDeny, respectAdminBypass, logDenials,
                engine, contraptions, value);
        }

        public Snapshot withDefaultPolicy(Policy value) {
            return new Snapshot(enabled, notifyOnDeny, respectAdminBypass, logDenials,
                new RuleEngine(engine.rules(), value), contraptions, waystones);
        }

        public Snapshot withRules(List<Rule> value) {
            return new Snapshot(enabled, notifyOnDeny, respectAdminBypass, logDenials,
                new RuleEngine(value, engine.defaultPolicy()), contraptions, waystones);
        }
    }

    private static final String FILE_NAME = "arcadia-ctp.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Snapshot current = Snapshot.defaults();

    private CTPConfig() {
    }

    public static Snapshot get() {
        return current;
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    /**
     * @return a human-readable summary of what happened, for the reload command
     */
    public static String load() {
        Path path = path();
        if (!Files.exists(path)) {
            String written = writeDefaults(path);
            if (written != null) {
                return written;
            }
        }

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return keepPrevious("root of " + FILE_NAME + " is not a JSON object");
            }
            root = parsed.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            return keepPrevious(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        boolean enabled = bool(root, "enabled", true);
        boolean notifyOnDeny = bool(root, "notifyOnDeny", true);
        boolean respectAdminBypass = bool(root, "respectAdminBypass", true);
        boolean logDenials = bool(root, "logDenials", false);
        Policy defaultPolicy = Policy.parse(string(root, "defaultPolicy"), Policy.CHECK);

        List<Rule> rules = new ArrayList<>();
        int skipped = 0;
        if (root.has("rules") && root.get("rules").isJsonArray()) {
            JsonArray array = root.getAsJsonArray("rules");
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                if (!element.isJsonObject()) {
                    skipped++;
                    continue;
                }
                Rule rule = readRule(element.getAsJsonObject(), i);
                if (rule == null) {
                    skipped++;
                } else {
                    rules.add(rule);
                }
            }
        }

        current = new Snapshot(enabled, notifyOnDeny, respectAdminBypass, logDenials,
            new RuleEngine(rules, defaultPolicy), readContraptions(root), readWaystones(root));

        String summary = "loaded %d rule(s), default %s, %s"
            .formatted(rules.size(), defaultPolicy, enabled ? "enabled" : "DISABLED");
        if (skipped > 0) {
            summary += " (" + skipped + " invalid entr" + (skipped == 1 ? "y" : "ies") + " skipped)";
        }
        ArcadiaCTP.LOGGER.info("[ArcadiaCTP] Config {}: {}", path, summary);
        return summary;
    }

    private static ContraptionSettings readContraptions(JsonObject root) {
        ContraptionSettings defaults = ContraptionSettings.defaults();
        if (!root.has("contraptions") || !root.get("contraptions").isJsonObject()) {
            return defaults;
        }
        JsonObject object = root.getAsJsonObject("contraptions");
        return new ContraptionSettings(
            bool(object, "enabled", defaults.enabled()),
            Policy.parse(string(object, "blockBreaking"), defaults.blockBreaking()),
            Policy.parse(string(object, "deployer"), defaults.deployer()),
            Policy.parse(string(object, "storageInterface"), defaults.storageInterface()),
            Policy.parse(string(object, "assembly"), defaults.assembly()),
            bool(object, "pilotFallback", defaults.pilotFallback())
        );
    }

    private static WaystoneSettings readWaystones(JsonObject root) {
        WaystoneSettings defaults = WaystoneSettings.defaults();
        if (!root.has("waystones") || !root.get("waystones").isJsonObject()) {
            return defaults;
        }
        JsonObject object = root.getAsJsonObject("waystones");
        return new WaystoneSettings(
            bool(object, "enabled", defaults.enabled()),
            Policy.parse(string(object, "teamVisibility"), defaults.teamVisibility()),
            Policy.parse(string(object, "claimInteraction"), defaults.claimInteraction())
        );
    }

    private static Rule readRule(JsonObject object, int index) {
        String policyRaw = string(object, "policy");
        if (policyRaw == null || policyRaw.isBlank()) {
            ArcadiaCTP.LOGGER.warn("[ArcadiaCTP] Rule #{} has no policy; skipped.", index);
            return null;
        }
        Policy policy = Policy.parse(policyRaw, null);
        if (policy == null) {
            ArcadiaCTP.LOGGER.warn("[ArcadiaCTP] Rule #{} has unknown policy '{}'; skipped.", index, policyRaw);
            return null;
        }

        String name = string(object, "name");
        if (name == null || name.isBlank()) {
            name = "rule#" + index;
        }

        List<String> packets = stringList(object, "packets");
        List<String> blocks = stringList(object, "blocks");
        if (packets.isEmpty() && blocks.isEmpty()) {
            // A rule with no criterion matches everything and shadows every rule below it.
            // That is almost never intended, and `defaultPolicy` already covers the case.
            ArcadiaCTP.LOGGER.warn(
                "[ArcadiaCTP] Rule '{}' declares neither packets nor blocks, so it would match "
                    + "everything and hide the rules under it; skipped. Use defaultPolicy instead.",
                name
            );
            return null;
        }

        return Rule.of(name, packets, blocks, policy);
    }

    private static String writeDefaults(Path path) {
        try {
            write(path, ConfigWriter.toJson(ConfigWriter.initialSnapshot()));
            ArcadiaCTP.LOGGER.info("[ArcadiaCTP] Wrote default config to {}", path);
            return null;
        } catch (IOException e) {
            ArcadiaCTP.LOGGER.error("[ArcadiaCTP] Could not write default config to {}", path, e);
            return keepPrevious("could not write default config: " + e.getMessage());
        }
    }

    /**
     * Replaces the live settings and writes them to disk.
     *
     * <p>Used by the admin panel, where every click is a change an admin expects to survive
     * a restart. The in-memory snapshot is swapped first, so a failed write costs the file,
     * not the setting the admin just made.
     *
     * @return null on success, or a message describing why the file could not be written
     */
    public static String apply(Snapshot snapshot) {
        current = snapshot;
        try {
            write(path(), ConfigWriter.toJson(snapshot));
            return null;
        } catch (IOException e) {
            ArcadiaCTP.LOGGER.error("[ArcadiaCTP] Could not save config to {}", path(), e);
            return "changes applied but NOT saved: " + e.getMessage();
        }
    }

    private static void write(Path path, com.google.gson.JsonObject json) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(json, writer);
        }
    }

    private static String keepPrevious(String reason) {
        String message = "config not reloaded (" + reason + "); previous settings kept";
        ArcadiaCTP.LOGGER.error("[ArcadiaCTP] {}", message);
        return message;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }

    private static List<String> stringList(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null) {
            return List.of();
        }
        // A single string is accepted where a list is expected: the common case is one
        // block or one packet, and quietly widening it avoids a class of config mistakes.
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return List.of(element.getAsString());
        }
        if (!element.isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                values.add(entry.getAsString());
            }
        }
        return values;
    }
}

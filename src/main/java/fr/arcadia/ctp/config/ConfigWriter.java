package fr.arcadia.ctp.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.arcadia.ctp.rules.Policy;
import fr.arcadia.ctp.rules.Rule;
import fr.arcadia.ctp.rules.RuleEngine;
import java.util.List;

/**
 * Turns a settings snapshot back into the config file.
 *
 * <p>One serializer for both jobs - writing the first-launch file and saving what the
 * admin panel changed - so a setting can never be editable in game yet absent from the
 * file it is written to. The explanatory {@code _} keys are regenerated on every write,
 * since JSON has no comments to preserve.
 */
final class ConfigWriter {

    private ConfigWriter() {
    }

    static JsonObject toJson(CTPConfig.Snapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("_comment",
            "Arcadia CTP - Claim & Team Protection: makes other mods respect FTB Teams and "
                + "FTB Chunks. The rules below apply to Create's block configuration packets "
                + "(scroll values, filters, thresholds), which reach the server without passing "
                + "through any interaction event and are therefore invisible to FTB Chunks; "
                + "contraptions and waystones have their own sections further down. "
                + "Editable in game with /arcadiactp panel.");
        root.add("_policies", array(List.of(
            "ALLOW     - always permitted, even to a player with no relation to the claim",
            "CHECK     - defers to FTB Chunks, like a normal right-click (recommended default)",
            "ALLY_ONLY - members of the claim's team plus its allies",
            "TEAM_ONLY - members of the claim's team only, ignoring the claim's ally/public settings",
            "DENY      - never permitted through this packet (admin bypass still applies)"
        )));
        root.add("_matching", array(List.of(
            "Rules are evaluated top to bottom; the first one that matches wins.",
            "'packets' matches the packet class, by simple name ('ValueSettingsPacket') or full name.",
            "'blocks' matches the block id at the target position ('create:rotation_speed_controller').",
            "Both accept * and ? wildcards; a 'blocks' entry starting with # is a block tag.",
            "An omitted or empty criterion matches everything. A rule needs at least one criterion.",
            "Unclaimed chunks have no owner, so ALLY_ONLY and TEAM_ONLY permit everyone there."
        )));

        root.addProperty("enabled", snapshot.enabled());
        root.addProperty("defaultPolicy", snapshot.engine().defaultPolicy().name());
        root.addProperty("notifyOnDeny", snapshot.notifyOnDeny());
        root.addProperty("respectAdminBypass", snapshot.respectAdminBypass());
        root.addProperty("logDenials", snapshot.logDenials());

        JsonArray rules = new JsonArray();
        for (Rule rule : snapshot.engine().rules()) {
            rules.add(toJson(rule));
        }
        root.add("rules", rules);

        ContraptionSettings contraptions = snapshot.contraptions();
        JsonObject contraptionJson = new JsonObject();
        contraptionJson.addProperty("_comment",
            "Contraption actors reach into claims with no player behind them: a drill breaks "
                + "what it rolls over, a Portable Storage Interface empties the chests it passes. "
                + "A contraption is stamped, on first use, with the team owning the claim it was "
                + "assembled in; while a player steers it, it may also borrow their rights "
                + "(pilotFallback). CHECK is read as ALLY_ONLY here - there is no player to check.");
        contraptionJson.addProperty("enabled", contraptions.enabled());
        contraptionJson.addProperty("blockBreaking", contraptions.blockBreaking().name());
        contraptionJson.addProperty("deployer", contraptions.deployer().name());
        contraptionJson.addProperty("storageInterface", contraptions.storageInterface().name());
        contraptionJson.addProperty("assembly", contraptions.assembly().name());
        contraptionJson.addProperty("pilotFallback", contraptions.pilotFallback());
        root.add("contraptions", contraptionJson);

        WaystoneSettings waystones = snapshot.waystones();
        JsonObject waystoneJson = new JsonObject();
        waystoneJson.addProperty("_comment",
            "Waystones offers a \"Visible to Team\" waystone to everyone sharing the owner's "
                + "VANILLA SCOREBOARD team, which has nothing to do with FTB Teams: any mod that "
                + "groups players on the scoreboard (a tab list sorted by rank, for instance) "
                + "turns that setting into \"visible to my whole permission group\". Resolved here "
                + "through FTB Teams instead. Policies: ALLOW leaves Waystones alone, TEAM_ONLY is "
                + "the owner's party, ALLY_ONLY adds allied teams, DENY is the owner alone.");
        waystoneJson.addProperty("enabled", waystones.enabled());
        waystoneJson.addProperty("teamVisibility", waystones.teamVisibility().name());
        waystoneJson.addProperty("_comment2",
            "claimInteraction: who may right-click a block of #arcadia_ctp:claim_interact_whitelist "
                + "(the waystones, out of the box) inside someone else's claim. FTB Chunks protects "
                + "them like any other block, so a waystone in a base cannot be registered by a "
                + "visitor; the mod whitelists them and hands the decision back here. ALLOW lets "
                + "anyone register one, ALLY_ONLY and TEAM_ONLY narrow it, CHECK gives the block "
                + "back to FTB Chunks, DENY locks it for everyone.");
        waystoneJson.addProperty("claimInteraction", waystones.claimInteraction().name());
        root.add("waystones", waystoneJson);

        return root;
    }

    private static JsonObject toJson(Rule rule) {
        JsonObject object = new JsonObject();
        object.addProperty("name", rule.name());
        if (!rule.rawPackets().isEmpty()) {
            object.add("packets", array(rule.rawPackets()));
        }
        if (!rule.rawBlocks().isEmpty()) {
            object.add("blocks", array(rule.rawBlocks()));
        }
        object.addProperty("policy", rule.policy().name());
        return object;
    }

    /** The rules shipped on first launch, as worked examples rather than an empty list. */
    static CTPConfig.Snapshot initialSnapshot() {
        List<Rule> rules = List.of(
            Rule.of("Speed controllers are team-only - the example this mod was written for",
                List.of(), List.of("create:rotation_speed_controller"), Policy.TEAM_ONLY),
            Rule.of("Filters decide where items go; keep them off-limits to visitors",
                List.of("FilteringCountUpdatePacket", "*Filter*Packet"), List.of(), Policy.TEAM_ONLY)
        );
        CTPConfig.Snapshot defaults = CTPConfig.Snapshot.defaults();
        return new CTPConfig.Snapshot(
            defaults.enabled(), defaults.notifyOnDeny(), defaults.respectAdminBypass(),
            defaults.logDenials(), new RuleEngine(rules, Policy.CHECK), defaults.contraptions(),
            defaults.waystones());
    }

    private static JsonArray array(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}

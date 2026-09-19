package fr.arcadia.ctp.rules;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One entry of the rule list. A rule matches when every criterion it declares matches;
 * an omitted or empty criterion matches everything.
 *
 * <p>Both criteria are needed in practice: {@code blocks} covers "this machine is
 * sensitive", {@code packets} covers "this kind of setting is sensitive" (a filter slot
 * is not a speed dial), and the two combined express the interesting cases.
 */
public final class Rule {

    private final String name;
    private final List<Matcher> packets;
    private final List<Matcher> blocks;
    private final List<TagKey<Block>> blockTags;
    private final Policy policy;
    // The entries exactly as written, kept so the admin panel can round-trip a rule back
    // to the config file without turning the author's globs and tags into compiled forms.
    private final List<String> rawPackets;
    private final List<String> rawBlocks;

    private Rule(String name, List<Matcher> packets, List<Matcher> blocks, List<TagKey<Block>> blockTags,
                 Policy policy, List<String> rawPackets, List<String> rawBlocks) {
        this.name = name;
        this.packets = packets;
        this.blocks = blocks;
        this.blockTags = blockTags;
        this.policy = policy;
        this.rawPackets = rawPackets;
        this.rawBlocks = rawBlocks;
    }

    /**
     * @param blockEntries block ids or globs; an entry starting with {@code #} is read as a block tag
     */
    public static Rule of(String name, List<String> packetEntries, List<String> blockEntries, Policy policy) {
        List<Matcher> packets = new ArrayList<>();
        for (String entry : orEmpty(packetEntries)) {
            if (!entry.isBlank()) {
                packets.add(Matcher.of(entry));
            }
        }

        List<Matcher> blocks = new ArrayList<>();
        List<TagKey<Block>> tags = new ArrayList<>();
        for (String entry : orEmpty(blockEntries)) {
            String trimmed = entry.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(trimmed.substring(1));
                // A malformed tag id would otherwise match nothing silently; dropping it
                // keeps the rule's remaining criteria meaningful.
                if (id != null) {
                    tags.add(TagKey.create(Registries.BLOCK, id));
                }
            } else {
                blocks.add(Matcher.of(trimmed));
            }
        }

        return new Rule(name, List.copyOf(packets), List.copyOf(blocks), List.copyOf(tags), policy,
            List.copyOf(orEmpty(packetEntries)), List.copyOf(orEmpty(blockEntries)));
    }

    /** Rebuilds the rule with one field replaced - the panel edits one thing at a time. */
    public Rule withName(String newName) {
        return of(newName, rawPackets, rawBlocks, policy);
    }

    public Rule withPolicy(Policy newPolicy) {
        return of(name, rawPackets, rawBlocks, newPolicy);
    }

    public Rule withPackets(List<String> entries) {
        return of(name, entries, rawBlocks, policy);
    }

    public Rule withBlocks(List<String> entries) {
        return of(name, rawPackets, entries, policy);
    }

    public List<String> rawPackets() {
        return rawPackets;
    }

    public List<String> rawBlocks() {
        return rawBlocks;
    }

    public boolean matches(String packetClassName, String packetSimpleName, String blockId, BlockState state) {
        return matchesPacket(packetClassName, packetSimpleName) && matchesBlock(blockId, state);
    }

    private boolean matchesPacket(String className, String simpleName) {
        if (packets.isEmpty()) {
            return true;
        }
        for (Matcher matcher : packets) {
            // Both forms are accepted so the config can stay readable ("ValueSettingsPacket")
            // without giving up the ability to disambiguate by full package.
            if (matcher.matches(className) || matcher.matches(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesBlock(String blockId, BlockState state) {
        if (blocks.isEmpty() && blockTags.isEmpty()) {
            return true;
        }
        for (Matcher matcher : blocks) {
            if (matcher.matches(blockId)) {
                return true;
            }
        }
        for (TagKey<Block> tag : blockTags) {
            if (state != null && state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    public String name() {
        return name;
    }

    public Policy policy() {
        return policy;
    }

    private static List<String> orEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }

    @Override
    public String toString() {
        return name + " -> " + policy;
    }
}

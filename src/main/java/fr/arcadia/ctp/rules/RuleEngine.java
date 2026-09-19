package fr.arcadia.ctp.rules;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An ordered rule list resolved first-match-wins, falling back to a default policy.
 *
 * <p>First-match-wins rather than most-specific-wins: the ordering is visible in the
 * config file, so an admin can read the file top to bottom and know what happens,
 * instead of having to work out which of two overlapping rules the engine considers
 * narrower.
 */
public final class RuleEngine {

    public record Decision(Policy policy, String ruleName) {
    }

    private static final RuleEngine EMPTY = new RuleEngine(List.of(), Policy.CHECK);

    private final List<Rule> rules;
    private final Policy defaultPolicy;

    public RuleEngine(List<Rule> rules, Policy defaultPolicy) {
        this.rules = List.copyOf(rules);
        this.defaultPolicy = defaultPolicy;
    }

    public static RuleEngine empty() {
        return EMPTY;
    }

    public Decision resolve(String packetClassName, String packetSimpleName, String blockId, BlockState state) {
        for (Rule rule : rules) {
            if (rule.matches(packetClassName, packetSimpleName, blockId, state)) {
                return new Decision(rule.policy(), rule.name());
            }
        }
        return new Decision(defaultPolicy, "<default>");
    }

    public List<Rule> rules() {
        return rules;
    }

    public Policy defaultPolicy() {
        return defaultPolicy;
    }
}

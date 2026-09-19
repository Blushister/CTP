package fr.arcadia.ctp.config;

import fr.arcadia.ctp.contraption.ActorKind;
import fr.arcadia.ctp.rules.Policy;

/**
 * Policies for the contraption vectors, one per way a contraption can reach into a claim.
 *
 * <p>They are plain settings rather than entries of the packet rule engine: a contraption
 * actor has no packet to match on, and the target block is whatever it happens to roll
 * over. {@code CHECK} has no meaning without a player to check, so it reads as
 * {@link Policy#ALLY_ONLY} here.
 */
public record ContraptionSettings(
    boolean enabled,
    Policy blockBreaking,
    Policy deployer,
    Policy storageInterface,
    Policy assembly,
    boolean pilotFallback
) {

    public static ContraptionSettings defaults() {
        return new ContraptionSettings(
            true, Policy.TEAM_ONLY, Policy.TEAM_ONLY, Policy.TEAM_ONLY, Policy.TEAM_ONLY, true);
    }

    public ContraptionSettings withEnabled(boolean value) {
        return new ContraptionSettings(value, blockBreaking, deployer, storageInterface, assembly, pilotFallback);
    }

    public ContraptionSettings withBlockBreaking(Policy value) {
        return new ContraptionSettings(enabled, value, deployer, storageInterface, assembly, pilotFallback);
    }

    public ContraptionSettings withDeployer(Policy value) {
        return new ContraptionSettings(enabled, blockBreaking, value, storageInterface, assembly, pilotFallback);
    }

    public ContraptionSettings withStorageInterface(Policy value) {
        return new ContraptionSettings(enabled, blockBreaking, deployer, value, assembly, pilotFallback);
    }

    public ContraptionSettings withAssembly(Policy value) {
        return new ContraptionSettings(enabled, blockBreaking, deployer, storageInterface, value, pilotFallback);
    }

    public ContraptionSettings withPilotFallback(boolean value) {
        return new ContraptionSettings(enabled, blockBreaking, deployer, storageInterface, assembly, value);
    }

    public Policy policyFor(ActorKind kind) {
        return switch (kind) {
            case BLOCK_BREAKING -> blockBreaking;
            case DEPLOYER -> deployer;
            case STORAGE_INTERFACE -> storageInterface;
        };
    }
}

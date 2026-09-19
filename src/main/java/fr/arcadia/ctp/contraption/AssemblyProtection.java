package fr.arcadia.ctp.contraption;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;
import fr.arcadia.ctp.ArcadiaCTP;
import fr.arcadia.ctp.runtime.AssemblyContext;
import fr.arcadia.ctp.runtime.ContraptionGuard;

/**
 * Keeps a forming contraption from picking up blocks that belong to a neighbouring claim.
 *
 * <p>This one vector needs no mixin: Create exposes {@code registerMovementAllowedCheck}
 * for exactly this decision. The only thing the callback lacks is who is assembling,
 * which {@link AssemblyContext} supplies.
 */
public final class AssemblyProtection {

    private AssemblyProtection() {
    }

    public static void register() {
        try {
            BlockMovementChecks.registerMovementAllowedCheck(AssemblyProtection::check);
            ArcadiaCTP.LOGGER.info("[ArcadiaCTP] Registered contraption assembly check.");
        } catch (Throwable t) {
            // Registration failing must not take the server down: without it the other
            // three vectors still hold, and only assembly-time theft stays possible.
            ArcadiaCTP.LOGGER.error("[ArcadiaCTP] Could not register the assembly check.", t);
        }
    }

    private static CheckResult check(net.minecraft.world.level.block.state.BlockState state,
                                     net.minecraft.world.level.Level level,
                                     net.minecraft.core.BlockPos pos) {
        if (!AssemblyContext.active()) {
            // Create also calls this outside assembly; PASS defers to the other checks.
            return CheckResult.PASS;
        }
        return ContraptionGuard.mayAssemble(level, AssemblyContext.anchor(), pos)
            ? CheckResult.PASS
            : CheckResult.FAIL;
    }
}

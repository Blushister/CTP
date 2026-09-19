package fr.arcadia.ctp.runtime;

import fr.arcadia.ctp.ArcadiaCTP;
import fr.arcadia.ctp.compat.ClaimRelation;
import fr.arcadia.ctp.compat.FTBChunksBridge;
import fr.arcadia.ctp.config.CTPConfig;
import fr.arcadia.ctp.contraption.ActorKind;
import fr.arcadia.ctp.contraption.ContraptionOwnerHolder;
import fr.arcadia.ctp.rules.Policy;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Decides whether a contraption actor may act on a position it just reached.
 *
 * <p>A contraption is not a player: it is normally started by redstone, keeps running
 * while its owner is offline, and travels far from where it was built. So the question
 * "may this act here" is answered against the team the contraption was stamped with at
 * assembly, not against anyone present.
 *
 * <p>The pilot fallback covers the case the stamp cannot: a contraption assembled on
 * unclaimed ground has no team, and would otherwise be unable to work inside its own
 * builder's base. While someone is steering it, it borrows their relation to the claim.
 */
public final class ContraptionGuard {

    private ContraptionGuard() {
    }

    /**
     * @param anchor the contraption's anchor, used once to stamp ownership
     * @param target the position the actor is about to work on
     */
    public static boolean mayVisit(ContraptionOwnerHolder holder, Object behaviour,
                                   Level level, BlockPos anchor, BlockPos target) {
        CTPConfig.Snapshot config = CTPConfig.get();
        if (!config.enabled() || !config.contraptions().enabled()) {
            return true;
        }
        if (level == null || level.isClientSide() || target == null || !ModPresence.hasFtbChunks()) {
            return true;
        }

        ActorKind kind = ActorKind.of(behaviour);
        Policy policy = config.contraptions().policyFor(kind);
        return isAllowed(holder, level, anchor, target, policy, kind);
    }

    /**
     * The assembly-time vector: a contraption forming next to a claim must not pick up
     * blocks that belong to it. Ownership is not stamped yet, so the anchor's claim is
     * compared directly against the block's.
     */
    public static boolean mayAssemble(Level level, BlockPos anchor, BlockPos target) {
        CTPConfig.Snapshot config = CTPConfig.get();
        if (!config.enabled() || !config.contraptions().enabled()) {
            return true;
        }
        if (level == null || level.isClientSide() || anchor == null || target == null
            || !ModPresence.hasFtbChunks()) {
            return true;
        }

        Policy policy = config.contraptions().assembly();
        if (policy == Policy.ALLOW) {
            return true;
        }
        // Short-circuited like the actor path does: without this, DENY would fall through to
        // the relation check and quietly permit the claim's own members, which is the one
        // thing DENY is supposed to rule out.
        if (policy == Policy.DENY) {
            return false;
        }

        UUID anchorTeam = FTBChunksBridge.teamIdAt(level, anchor);
        ClaimRelation relation = FTBChunksBridge.relationForTeam(level, target, anchorTeam);
        boolean allowed = evaluate(relation, policy);
        if (!allowed && config.logDenials()) {
            ArcadiaCTP.LOGGER.info(
                "[ArcadiaCTP] Blocked assembly of {} into a contraption anchored at {} ({} vs claim, policy {})",
                target, anchor, relation, policy);
        }
        return allowed;
    }

    private static boolean isAllowed(ContraptionOwnerHolder holder, Level level, BlockPos anchor,
                                     BlockPos target, Policy policy, ActorKind kind) {
        if (policy == Policy.ALLOW) {
            return true;
        }
        if (policy == Policy.DENY) {
            return false;
        }

        UUID owner = resolveOwner(holder, level, anchor);
        ClaimRelation relation = FTBChunksBridge.relationForTeam(level, target, owner);
        if (evaluate(relation, policy)) {
            return true;
        }

        if (CTPConfig.get().contraptions().pilotFallback() && borrowsPilotRights(holder, level, target, policy)) {
            return true;
        }

        if (CTPConfig.get().logDenials()) {
            ArcadiaCTP.LOGGER.info(
                "[ArcadiaCTP] Blocked contraption {} at {} (owner team {}, relation {}, policy {})",
                kind, target, owner, relation, policy);
        }
        return false;
    }

    private static boolean evaluate(ClaimRelation relation, Policy policy) {
        return switch (relation) {
            // Nothing to protect, or nothing readable to decide on.
            case UNCLAIMED, UNKNOWN, MEMBER -> true;
            case ALLY -> policy != Policy.TEAM_ONLY;
            case OUTSIDER -> false;
        };
    }

    /** Stamps ownership on first use and reuses it forever after, including across saves. */
    private static UUID resolveOwner(ContraptionOwnerHolder holder, Level level, BlockPos anchor) {
        if (holder.arcadiaCtp$isOwnerResolved()) {
            return holder.arcadiaCtp$ownerTeam();
        }
        UUID team = anchor == null ? null : FTBChunksBridge.teamIdAt(level, anchor);
        holder.arcadiaCtp$setOwnerTeam(team);
        return team;
    }

    private static boolean borrowsPilotRights(ContraptionOwnerHolder holder, Level level,
                                              BlockPos target, Policy policy) {
        Optional<UUID> pilotId = holder.arcadiaCtp$pilot();
        if (pilotId.isEmpty()) {
            return false;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return false;
        }
        ServerPlayer pilot = server.getPlayerList().getPlayer(pilotId.get());
        if (pilot == null) {
            return false;
        }
        return evaluate(FTBChunksBridge.relation(pilot, target), policy);
    }
}

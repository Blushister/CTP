package fr.arcadia.ctp.runtime;

import fr.arcadia.ctp.compat.ClaimRelation;
import fr.arcadia.ctp.compat.FTBTeamsBridge;
import fr.arcadia.ctp.config.CTPConfig;
import fr.arcadia.ctp.config.WaystoneSettings;
import fr.arcadia.ctp.rules.Policy;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneVisibility;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rebuilds the list of "Visible to Team" waystones a player is offered, from FTB Teams
 * membership instead of the vanilla scoreboard.
 *
 * <p>Only the read path is replaced. The scoreboard index Waystones maintains is left
 * alone: it stays correct for Waystones' own bookkeeping, it is simply no longer what
 * decides who gets to see what.
 */
public final class WaystoneGuard {

    private WaystoneGuard() {
    }

    /**
     * @return the targets to hand back, or {@code null} to let Waystones answer itself
     */
    public static Collection<Waystone> teamTargets(ServerPlayer player) {
        CTPConfig.Snapshot config = CTPConfig.get();
        WaystoneSettings settings = config.waystones();
        if (!config.enabled() || !settings.enabled() || settings.teamVisibility() == Policy.ALLOW) {
            return null;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return List.of();
        }
        if (!ModPresence.hasFtbTeams() || !FTBTeamsBridge.isReady()) {
            // Fail closed. Falling back to Waystones' own lookup would reinstate exactly
            // the scoreboard leak this exists to close, and a missing teleport target is
            // an inconvenience where an extra one is someone else's base on a map.
            return List.of();
        }

        UUID viewer = player.getUUID();
        Policy policy = settings.teamVisibility();
        return WaystoneManagerImpl.get(server).getWaystones()
            .filter(waystone -> waystone.getVisibility() == WaystoneVisibility.TEAM)
            .filter(waystone -> maySee(viewer, waystone.getOwnerUid(), policy))
            .toList();
    }

    private static boolean maySee(UUID viewer, UUID owner, Policy policy) {
        if (owner == null) {
            return false;
        }
        // The owner keeps their own waystone under every policy, DENY included: DENY means
        // "shared with nobody", not "taken away from the player who placed it".
        if (viewer.equals(owner)) {
            return true;
        }
        if (policy == Policy.DENY) {
            return false;
        }
        ClaimRelation relation = FTBTeamsBridge.relationBetween(viewer, owner);
        return switch (relation) {
            case MEMBER -> true;
            case ALLY -> policy == Policy.ALLY_ONLY;
            // CHECK has no interaction to defer to here and is read as TEAM_ONLY, so
            // everything else - outsider, or team data that could not be read - is out.
            case OUTSIDER, UNCLAIMED, UNKNOWN -> false;
        };
    }
}

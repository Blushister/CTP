package fr.arcadia.ctp.compat;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import fr.arcadia.ctp.ArcadiaCTP;
import java.util.Optional;
import java.util.UUID;

/**
 * Every direct call into FTB Teams lives here.
 *
 * <p>Same contract as {@link FTBChunksBridge}: callers must check
 * {@link fr.arcadia.ctp.runtime.ModPresence#hasFtbTeams()} first, because this class
 * names FTB types in its signatures and would throw {@link NoClassDefFoundError} on a
 * server without the mod.
 *
 * <p>Unlike the claim bridge, an unreadable answer here is {@code UNKNOWN} and callers
 * fail <em>closed</em>: the question this bridge answers is "may this player see
 * something of someone else's", and the safe answer to that is no.
 */
public final class FTBTeamsBridge {

    private FTBTeamsBridge() {
    }

    private static TeamManager manager() {
        return FTBTeamsAPI.api().isManagerLoaded() ? FTBTeamsAPI.api().getManager() : null;
    }

    /** True once team data exists, which on a server is from the first world load on. */
    public static boolean isReady() {
        try {
            return manager() != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * How {@code viewer} stands to {@code owner}'s team, without a claim in the picture.
     *
     * <p>{@code arePlayersInSameTeam} compares effective team ids, so a player with no
     * party is compared through their personal team and only ever matches themselves.
     */
    public static ClaimRelation relationBetween(UUID viewer, UUID owner) {
        if (viewer == null || owner == null) {
            return ClaimRelation.UNKNOWN;
        }
        if (viewer.equals(owner)) {
            return ClaimRelation.MEMBER;
        }
        TeamManager manager = manager();
        if (manager == null) {
            return ClaimRelation.UNKNOWN;
        }
        try {
            if (manager.arePlayersInSameTeam(viewer, owner)) {
                return ClaimRelation.MEMBER;
            }
            Optional<Team> ownerTeam = manager.getTeamForPlayerID(owner);
            if (ownerTeam.isPresent() && ownerTeam.get().getRankForPlayer(viewer).isAllyOrBetter()) {
                return ClaimRelation.ALLY;
            }
            return ClaimRelation.OUTSIDER;
        } catch (RuntimeException e) {
            ArcadiaCTP.LOGGER.warn("[ArcadiaCTP] FTB Teams membership lookup failed for {} / {}", viewer, owner, e);
            return ClaimRelation.UNKNOWN;
        }
    }
}

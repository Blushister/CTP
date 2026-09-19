package fr.arcadia.ctp.compat;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.Protection;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import fr.arcadia.ctp.ArcadiaCTP;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

/**
 * Every direct call into FTB Chunks lives here.
 *
 * <p>Callers must check {@link fr.arcadia.ctp.runtime.ModPresence#hasFtbChunks()} first:
 * this class references FTB types at the field level, so merely loading it on a server
 * without FTB Chunks would throw {@link NoClassDefFoundError}.
 */
public final class FTBChunksBridge {

    private FTBChunksBridge() {
    }

    private static ClaimedChunkManager manager() {
        return FTBChunksAPI.api().isManagerLoaded() ? FTBChunksAPI.api().getManager() : null;
    }

    /** Mirrors what FTB Chunks would answer for a normal right-click on that block. */
    public static boolean shouldPrevent(ServerPlayer player, BlockPos pos) {
        ClaimedChunkManager manager = manager();
        if (manager == null) {
            return false;
        }
        try {
            return manager.shouldPreventInteraction(
                player, InteractionHand.MAIN_HAND, pos, Protection.EDIT_AND_INTERACT_BLOCK, null);
        } catch (RuntimeException e) {
            // Failing open matches how the rest of the protection behaves when its data
            // is not ready; failing closed here would lock players out of their own base.
            ArcadiaCTP.LOGGER.warn("[ArcadiaCTP] FTB Chunks protection check failed at {}", pos, e);
            return false;
        }
    }

    public static ClaimRelation relation(ServerPlayer player, BlockPos pos) {
        ClaimedChunkManager manager = manager();
        if (manager == null) {
            return ClaimRelation.UNKNOWN;
        }
        try {
            ClaimedChunk chunk = manager.getChunk(new ChunkDimPos(player.level(), pos));
            if (chunk == null) {
                return ClaimRelation.UNCLAIMED;
            }
            ChunkTeamData owner = chunk.getTeamData();
            if (owner == null) {
                return ClaimRelation.UNCLAIMED;
            }
            UUID id = player.getUUID();
            if (owner.isTeamMember(id)) {
                return ClaimRelation.MEMBER;
            }
            if (owner.isAlly(id)) {
                return ClaimRelation.ALLY;
            }
            return ClaimRelation.OUTSIDER;
        } catch (RuntimeException e) {
            ArcadiaCTP.LOGGER.warn("[ArcadiaCTP] FTB Chunks claim lookup failed at {}", pos, e);
            return ClaimRelation.UNKNOWN;
        }
    }

    /**
     * Id of the team owning the claim at that position, or {@code null} when unclaimed
     * or unreadable. Used to stamp a contraption with the team it was assembled for.
     */
    public static UUID teamIdAt(Level level, BlockPos pos) {
        ClaimedChunkManager manager = manager();
        if (manager == null) {
            return null;
        }
        try {
            ClaimedChunk chunk = manager.getChunk(new ChunkDimPos(level, pos));
            if (chunk == null || chunk.getTeamData() == null || chunk.getTeamData().getTeam() == null) {
                return null;
            }
            return chunk.getTeamData().getTeam().getId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Same question as {@link #relation(ServerPlayer, BlockPos)}, asked on behalf of a team
     * rather than a player - a contraption acts with no player behind it.
     *
     * @param ownerTeamId the acting team, or {@code null} for an unowned contraption
     */
    public static ClaimRelation relationForTeam(Level level, BlockPos pos, UUID ownerTeamId) {
        ClaimedChunkManager manager = manager();
        if (manager == null) {
            return ClaimRelation.UNKNOWN;
        }
        try {
            ClaimedChunk chunk = manager.getChunk(new ChunkDimPos(level, pos));
            if (chunk == null || chunk.getTeamData() == null) {
                return ClaimRelation.UNCLAIMED;
            }
            ChunkTeamData target = chunk.getTeamData();
            if (ownerTeamId == null) {
                return ClaimRelation.OUTSIDER;
            }
            if (target.getTeam() != null && ownerTeamId.equals(target.getTeam().getId())) {
                return ClaimRelation.MEMBER;
            }
            // FTB's ally checks take a UUID that is a player id for personal teams and a
            // team id for parties; passing the owning team's id covers the common cases and
            // can only widen the answer, never narrow it.
            if (target.isTeamMember(ownerTeamId) || target.isAlly(ownerTeamId)) {
                return ClaimRelation.ALLY;
            }
            return ClaimRelation.OUTSIDER;
        } catch (RuntimeException e) {
            ArcadiaCTP.LOGGER.warn("[ArcadiaCTP] FTB Chunks team lookup failed at {}", pos, e);
            return ClaimRelation.UNKNOWN;
        }
    }

    /** True when the player has toggled the FTB Chunks admin protection bypass. */
    public static boolean hasBypass(ServerPlayer player) {
        ClaimedChunkManager manager = manager();
        if (manager == null) {
            return false;
        }
        try {
            return manager.getBypassProtection(player.getUUID());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Name of the team owning the claim, for admin-facing output. Never null. */
    public static String ownerName(ServerPlayer player, BlockPos pos) {
        ClaimedChunkManager manager = manager();
        if (manager == null) {
            return "<unknown>";
        }
        try {
            ClaimedChunk chunk = manager.getChunk(new ChunkDimPos(player.level(), pos));
            if (chunk == null || chunk.getTeamData() == null || chunk.getTeamData().getTeam() == null) {
                return "<unclaimed>";
            }
            return chunk.getTeamData().getTeam().getName().getString();
        } catch (RuntimeException e) {
            return "<unknown>";
        }
    }
}

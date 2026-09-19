package fr.arcadia.ctp.runtime;

import fr.arcadia.ctp.ArcadiaCTP;
import fr.arcadia.ctp.compat.ClaimRelation;
import fr.arcadia.ctp.compat.FTBChunksBridge;
import fr.arcadia.ctp.config.CTPConfig;
import fr.arcadia.ctp.rules.Policy;
import fr.arcadia.ctp.rules.RuleEngine;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The single decision point of the mod, called from the mixin on Create's
 * {@code BlockEntityConfigurationPacket}.
 *
 * <p>This class deliberately takes the packet as {@link Object}: it must stay loadable
 * on a server where Create is absent, since the mixin then simply never applies.
 */
public final class ConfigurationGuard {

    /** How long a player is left alone after being told, so a scroll burst is one message. */
    private static final long NOTIFY_COOLDOWN_MS = 2000L;

    private static final Map<UUID, Long> LAST_NOTIFIED = new ConcurrentHashMap<>();

    public record Verdict(boolean allowed, Policy policy, String ruleName, ClaimRelation relation, String blockId) {
    }

    private ConfigurationGuard() {
    }

    /**
     * @return true when the packet must not be applied
     */
    public static boolean shouldReject(Object packet, ServerPlayer player, BlockPos pos) {
        CTPConfig.Snapshot config = CTPConfig.get();
        if (!config.enabled() || player == null || pos == null) {
            return false;
        }
        if (!ModPresence.hasFtbChunks()) {
            // Without FTB Chunks there is no claim data to protect anything with.
            return false;
        }
        if (config.respectAdminBypass() && FTBChunksBridge.hasBypass(player)) {
            return false;
        }

        Class<?> type = packet.getClass();
        Verdict verdict = evaluate(type.getName(), type.getSimpleName(), player, pos);
        if (verdict.allowed()) {
            return false;
        }

        if (config.notifyOnDeny()) {
            notifyOnce(player);
        }
        if (config.logDenials()) {
            ArcadiaCTP.LOGGER.info(
                "[ArcadiaCTP] Rejected {} from {} at {} ({}): rule '{}' -> {}, relation {}",
                packet.getClass().getSimpleName(), player.getGameProfile().getName(), pos,
                verdict.blockId(), verdict.ruleName(), verdict.policy(), verdict.relation()
            );
        }
        return true;
    }

    /** Shared by the guard and the {@code probe} command so both report the same reasoning. */
    public static Verdict evaluate(String packetClassName, String packetSimpleName, ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        // An unloaded chunk gives no block to match on. Create already range-checks the
        // position, so letting it through here means "no rule applied", not "no protection".
        BlockState state = level.isLoaded(pos) ? level.getBlockState(pos) : null;
        String blockId = state == null
            ? "<unloaded>"
            : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        RuleEngine.Decision decision = CTPConfig.get().engine()
            .resolve(packetClassName, packetSimpleName, blockId, state);

        ClaimRelation relation = needsRelation(decision.policy())
            ? FTBChunksBridge.relation(player, pos)
            : ClaimRelation.UNKNOWN;

        boolean allowed = switch (decision.policy()) {
            case ALLOW -> true;
            case DENY -> false;
            case CHECK -> !FTBChunksBridge.shouldPrevent(player, pos);
            // An unclaimed chunk has no owner to reserve the machine for, and UNKNOWN means
            // the claim data could not be read - neither is grounds for taking control away.
            case ALLY_ONLY -> relation != ClaimRelation.OUTSIDER;
            case TEAM_ONLY -> relation == ClaimRelation.MEMBER
                || relation == ClaimRelation.UNCLAIMED
                || relation == ClaimRelation.UNKNOWN;
        };

        return new Verdict(allowed, decision.policy(), decision.ruleName(), relation, blockId);
    }

    private static boolean needsRelation(Policy policy) {
        return policy == Policy.ALLY_ONLY || policy == Policy.TEAM_ONLY;
    }

    private static void notifyOnce(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = LAST_NOTIFIED.get(player.getUUID());
        if (last != null && now - last < NOTIFY_COOLDOWN_MS) {
            return;
        }
        LAST_NOTIFIED.put(player.getUUID(), now);
        player.sendSystemMessage(
            Component.translatable("arcadia_ctp.message.denied").withStyle(ChatFormatting.RED), true);
    }

    /** Called on logout so the cooldown map does not accumulate entries for the session's lifetime. */
    public static void forget(UUID playerId) {
        LAST_NOTIFIED.remove(playerId);
    }
}

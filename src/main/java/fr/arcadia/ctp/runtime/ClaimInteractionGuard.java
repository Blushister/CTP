package fr.arcadia.ctp.runtime;

import fr.arcadia.ctp.ArcadiaCTP;
import fr.arcadia.ctp.compat.ClaimRelation;
import fr.arcadia.ctp.compat.FTBChunksBridge;
import fr.arcadia.ctp.config.CTPConfig;
import fr.arcadia.ctp.rules.Policy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Decides who may right-click a whitelisted block standing inside a claim.
 *
 * <p>The mod ships {@code #arcadia_ctp:claim_interact_whitelist} into FTB Chunks' own
 * {@code interact_whitelist}, which makes FTB Chunks return ALLOW before it ever looks at
 * the claim's team. That is deliberate - a waystone in a claim has to be reachable, or
 * nobody can register it - but it is all-or-nothing, and a datapack tag has no switch.
 * So the door is opened there and closed again here, under a policy an admin can change
 * from the panel without touching a datapack.
 */
public final class ClaimInteractionGuard {

    public static final TagKey<Block> WHITELIST = TagKey.create(
        Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("arcadia_ctp", "claim_interact_whitelist"));

    private static final long NOTIFY_COOLDOWN_MS = 2000L;
    private static final Map<UUID, Long> LAST_NOTIFIED = new ConcurrentHashMap<>();

    private ClaimInteractionGuard() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CTPConfig.Snapshot config = CTPConfig.get();
        Policy policy = config.waystones().claimInteraction();
        if (!config.enabled() || policy == Policy.ALLOW || !ModPresence.hasFtbChunks()) {
            return;
        }

        BlockPos pos = event.getPos();
        if (!event.getLevel().getBlockState(pos).is(WHITELIST)) {
            // Anything not on the whitelist was never opened up, so FTB Chunks is still
            // deciding it and this guard has no business narrowing it further.
            return;
        }
        if (config.respectAdminBypass() && FTBChunksBridge.hasBypass(player)) {
            return;
        }
        if (allows(player, pos, policy)) {
            return;
        }

        event.setCanceled(true);
        if (config.notifyOnDeny()) {
            notifyDenied(player);
        }
        if (config.logDenials()) {
            ArcadiaCTP.LOGGER.info("[ArcadiaCTP] Blocked {} from interacting with {} at {} (policy {})",
                player.getGameProfile().getName(), event.getLevel().getBlockState(pos).getBlock(), pos, policy);
        }
    }

    private static boolean allows(ServerPlayer player, BlockPos pos, Policy policy) {
        if (policy == Policy.DENY) {
            return false;
        }
        if (policy == Policy.CHECK) {
            // Hands the question straight back to FTB Chunks - the answer it would have
            // given if the block had never been whitelisted.
            return !FTBChunksBridge.shouldPrevent(player, pos);
        }
        return switch (FTBChunksBridge.relation(player, pos)) {
            case UNCLAIMED, UNKNOWN, MEMBER -> true;
            case ALLY -> policy == Policy.ALLY_ONLY;
            case OUTSIDER -> false;
        };
    }

    private static void notifyDenied(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = LAST_NOTIFIED.get(player.getUUID());
        if (last != null && now - last < NOTIFY_COOLDOWN_MS) {
            return;
        }
        LAST_NOTIFIED.put(player.getUUID(), now);
        player.displayClientMessage(
            Component.translatable("arcadia_ctp.message.denied").withStyle(ChatFormatting.RED), true);
    }

    public static void forget(UUID playerId) {
        LAST_NOTIFIED.remove(playerId);
    }
}

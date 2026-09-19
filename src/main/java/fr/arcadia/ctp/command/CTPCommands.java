package fr.arcadia.ctp.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.arcadia.ctp.config.CTPConfig;
import fr.arcadia.ctp.menu.CTPAdminMenu;
import fr.arcadia.ctp.runtime.ConfigurationGuard;
import fr.arcadia.ctp.runtime.ModPresence;
import fr.arcadia.ctp.rules.Rule;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Admin surface: reload the rules, read the current state, and ask what would happen
 * at a given position without having to find a second account to test with.
 */
public final class CTPCommands {

    /** The packet assumed by {@code probe} when none is given - the one behind scroll values. */
    private static final String DEFAULT_PROBE_PACKET =
        "com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket";

    private CTPCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arcadiactp")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("panel").executes(CTPCommands::panel))
            .then(Commands.literal("reload").executes(CTPCommands::reload))
            .then(Commands.literal("status").executes(CTPCommands::status))
            .then(Commands.literal("probe")
                // No coordinates: the block being looked at. Typing coordinates by hand is
                // how you end up diagnosing the sand block next to the machine.
                .executes(context -> probe(context, DEFAULT_PROBE_PACKET, null))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> probe(context, DEFAULT_PROBE_PACKET,
                        BlockPosArgument.getLoadedBlockPos(context, "pos")))
                    .then(Commands.argument("packet", StringArgumentType.string())
                        .executes(context -> probe(context, StringArgumentType.getString(context, "packet"),
                            BlockPosArgument.getLoadedBlockPos(context, "pos"))))));

        event.getDispatcher().register(root);
    }

    private static int panel(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal(
                "[Arcadia CTP] Le panel est une interface en jeu; lance-le en tant que joueur."));
            return 0;
        }
        CTPAdminMenu.open(player, CTPAdminMenu.Page.ROOT, -1);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        String summary = CTPConfig.load();
        context.getSource().sendSuccess(
            () -> Component.literal("[Arcadia CTP] " + summary).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CTPConfig.Snapshot config = CTPConfig.get();
        CommandSourceStack source = context.getSource();

        send(source, "Arcadia CTP", ChatFormatting.GOLD);
        send(source, "  enabled: " + config.enabled(), ChatFormatting.GRAY);
        send(source, "  FTB Chunks present: " + ModPresence.hasFtbChunks()
            + " | Create present: " + ModPresence.hasCreate(), ChatFormatting.GRAY);
        send(source, "  default policy: " + config.engine().defaultPolicy(), ChatFormatting.GRAY);
        send(source, "  notify on deny: " + config.notifyOnDeny()
            + " | admin bypass: " + config.respectAdminBypass()
            + " | log denials: " + config.logDenials(), ChatFormatting.GRAY);
        send(source, "  config: " + CTPConfig.path(), ChatFormatting.DARK_GRAY);
        send(source, "  contraptions: " + (config.contraptions().enabled() ? "on" : "off")
            + " | breaking=" + config.contraptions().blockBreaking()
            + " deployer=" + config.contraptions().deployer()
            + " storage=" + config.contraptions().storageInterface()
            + " assembly=" + config.contraptions().assembly()
            + " pilotFallback=" + config.contraptions().pilotFallback(), ChatFormatting.GRAY);
        send(source, "  waystones: " + (config.waystones().enabled() ? "on" : "off")
            + " | teamVisibility=" + config.waystones().teamVisibility()
            + " | claimInteraction=" + config.waystones().claimInteraction()
            + " | FTB Teams present: " + ModPresence.hasFtbTeams(), ChatFormatting.GRAY);
        send(source, "  rules (" + config.engine().rules().size() + ", first match wins):", ChatFormatting.GRAY);
        int index = 0;
        for (Rule rule : config.engine().rules()) {
            send(source, "    " + (index++) + ". " + rule, ChatFormatting.DARK_GRAY);
        }
        return 1;
    }

    private static int probe(CommandContext<CommandSourceStack> context, String packetName, BlockPos explicitPos) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(
                "[Arcadia CTP] probe evaluates the rules for a player; run it as one."));
            return 0;
        }

        BlockPos pos = explicitPos != null ? explicitPos : lookingAt(player);
        if (pos == null) {
            source.sendFailure(Component.literal(
                "[Arcadia CTP] Vise un bloc, ou donne des coordonnées."));
            return 0;
        }
        String simpleName = packetName.substring(packetName.lastIndexOf('.') + 1);
        ConfigurationGuard.Verdict verdict =
            ConfigurationGuard.evaluate(packetName, simpleName, player, pos);

        send(source, "Arcadia CTP probe @ " + pos.toShortString(), ChatFormatting.GOLD);
        send(source, "  block: " + verdict.blockId(), ChatFormatting.GRAY);
        send(source, "  packet: " + simpleName, ChatFormatting.GRAY);
        send(source, "  matched rule: " + verdict.ruleName() + " -> " + verdict.policy(), ChatFormatting.GRAY);
        send(source, "  claim relation: " + verdict.relation(), ChatFormatting.GRAY);
        send(source, "  result: " + (verdict.allowed() ? "ALLOWED" : "REJECTED"),
            verdict.allowed() ? ChatFormatting.GREEN : ChatFormatting.RED);
        if (CTPConfig.get().respectAdminBypass() && !verdict.allowed()) {
            // The guard checks the bypass before evaluating, so probe's verdict is the
            // rule outcome alone; without this line an admin with bypass on would read
            // REJECTED and then watch the packet go through.
            send(source, "  note: your own FTB Chunks admin bypass is applied before this check.",
                ChatFormatting.DARK_GRAY);
        }
        return 1;
    }

    /** The block the player is aiming at, or null if they are looking at the sky. */
    private static BlockPos lookingAt(ServerPlayer player) {
        HitResult hit = player.pick(10.0D, 0.0F, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
            ? block.getBlockPos()
            : null;
    }

    private static void send(CommandSourceStack source, String message, ChatFormatting color) {
        source.sendSuccess(() -> Component.literal(message).withStyle(color), false);
    }
}

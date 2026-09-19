package fr.arcadia.ctp.menu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.ServerChatEvent;

/**
 * Lets a panel button ask for a line of text, since a vanilla chest screen has no text field.
 *
 * <p>The menu closes, the next thing the admin types is swallowed instead of being sent to
 * chat, and the panel reopens where it was. Only the player who opened the prompt is
 * affected, and only for their next message.
 */
public final class ChatPrompts {

    /** A forgotten prompt must not keep eating an admin's chat forever. */
    private static final long TIMEOUT_MS = 120_000L;

    private record Prompt(String description, Consumer<String> onInput, Runnable onCancel, long expiresAt) {
    }

    private static final Map<UUID, Prompt> PENDING = new ConcurrentHashMap<>();

    private ChatPrompts() {
    }

    /**
     * @param onInput  run on the server thread with the typed line, already trimmed
     * @param onCancel run on the server thread when the admin types {@code cancel}
     */
    public static void ask(ServerPlayer player, String description, String currentValue,
                           Consumer<String> onInput, Runnable onCancel) {
        player.closeContainer();
        PENDING.put(player.getUUID(),
            new Prompt(description, onInput, onCancel, System.currentTimeMillis() + TIMEOUT_MS));

        player.sendSystemMessage(Component.literal("[Arcadia CTP] " + description)
            .withStyle(ChatFormatting.GOLD));
        if (currentValue != null && !currentValue.isBlank()) {
            player.sendSystemMessage(Component.literal("  actuel : " + currentValue)
                .withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(Component.literal(
            "  Tape la nouvelle valeur dans le chat, ou 'cancel' pour revenir.")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        Prompt prompt = PENDING.get(player.getUUID());
        if (prompt == null) {
            return;
        }

        PENDING.remove(player.getUUID());
        if (System.currentTimeMillis() > prompt.expiresAt()) {
            // Expired: let the message through as normal chat rather than eating it.
            player.sendSystemMessage(Component.literal(
                "[Arcadia CTP] Saisie expirée, message envoyé normalement.")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        event.setCanceled(true);
        String raw = event.getRawText().trim();

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        // The event fires on the network thread; everything the callbacks touch - config,
        // menus, the player - belongs to the server thread.
        server.execute(() -> {
            if (raw.equalsIgnoreCase("cancel") || raw.equalsIgnoreCase("annuler")) {
                prompt.onCancel().run();
            } else {
                prompt.onInput().accept(raw);
            }
        });
    }

    /** Called on logout so a disconnected admin leaves no pending prompt behind. */
    public static void forget(UUID playerId) {
        PENDING.remove(playerId);
    }
}

package fr.arcadia.ctp;

import fr.arcadia.ctp.command.CTPCommands;
import fr.arcadia.ctp.config.CTPConfig;
import fr.arcadia.ctp.contraption.AssemblyProtection;
import fr.arcadia.ctp.menu.ChatPrompts;
import fr.arcadia.ctp.runtime.ClaimInteractionGuard;
import fr.arcadia.ctp.runtime.ConfigurationGuard;
import fr.arcadia.ctp.runtime.ModPresence;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Claim &amp; Team Protection: makes other mods respect FTB Teams and FTB Chunks.
 *
 * <p>FTB Chunks guards what reaches it through a player interaction event. Anything that
 * goes around one - Create's configuration packets, a contraption acting on its own, a
 * mod with its own idea of what a "team" is, like Waystones and the vanilla scoreboard -
 * is invisible to it. Each of those paths is closed here, behind a policy an admin sets
 * from the in-game panel.
 *
 * <p>CTP originally stood for Create Team Protection; the id is kept so configs, commands
 * and saved data carry over.
 */
@Mod(ArcadiaCTP.MOD_ID)
public class ArcadiaCTP {

    public static final String MOD_ID = "arcadia_ctp";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public ArcadiaCTP(IEventBus modEventBus) {
        modEventBus.addListener(ArcadiaCTP::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(ArcadiaCTP::onServerStarting);
        NeoForge.EVENT_BUS.addListener(CTPCommands::register);
        NeoForge.EVENT_BUS.addListener(ArcadiaCTP::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ChatPrompts::onChat);
        NeoForge.EVENT_BUS.addListener(ClaimInteractionGuard::onRightClickBlock);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // Checked here and not inside register(): AssemblyProtection's own signatures
        // name Create types, so loading the class at all would fail without Create,
        // before any try block in it could run.
        if (ModPresence.hasCreate()) {
            event.enqueueWork(AssemblyProtection::register);
        }
    }

    private static void onServerStarting(ServerStartingEvent event) {
        // Loaded here rather than in the constructor: the config directory is settled by
        // now, and reload uses the same path, so there is one code path for both.
        CTPConfig.load();
        if (!ModPresence.hasCreate()) {
            LOGGER.info("[ArcadiaCTP] Create is not installed - Create protections are inactive.");
        }
        if (!ModPresence.hasFtbChunks()) {
            LOGGER.warn("[ArcadiaCTP] FTB Chunks is not installed - no claim data, every check is a no-op.");
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ConfigurationGuard.forget(event.getEntity().getUUID());
        ChatPrompts.forget(event.getEntity().getUUID());
        ClaimInteractionGuard.forget(event.getEntity().getUUID());
    }
}

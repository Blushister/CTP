package fr.arcadia.ctp.mixin;

import fr.arcadia.ctp.runtime.WaystoneGuard;
import java.util.Collection;
import net.blay09.mods.waystones.api.Waystone;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Resolves "Visible to Team" waystones through FTB Teams.
 *
 * <p>{@code getTeamTargets} reads {@code ServerPlayer.getTeam()} - the vanilla scoreboard
 * team - and returns every TEAM waystone indexed under that name. A scoreboard team is
 * not a party: anything that groups players for its own reasons (a tab list sorted by
 * permission group, a nametag colour) silently becomes the sharing list.
 *
 * <p>Head-injected and cancelled rather than redirected: the answer is rebuilt from the
 * waystone list, so none of the original body is relevant once the guard has a verdict.
 */
@Pseudo
@Mixin(targets = "net.blay09.mods.waystones.core.WaystoneIndexManager", remap = false)
public abstract class MixinWaystoneIndexManager {

    @Inject(
        method = "getTeamTargets(Lnet/minecraft/server/level/ServerPlayer;)Ljava/util/Collection;",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void arcadiaCtp$resolveTeamTargets(ServerPlayer player,
                                                      CallbackInfoReturnable<Collection<Waystone>> cir) {
        Collection<Waystone> resolved = WaystoneGuard.teamTargets(player);
        if (resolved != null) {
            cir.setReturnValue(resolved);
        }
    }
}

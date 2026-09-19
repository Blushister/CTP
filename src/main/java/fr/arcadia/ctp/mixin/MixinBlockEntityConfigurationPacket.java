package fr.arcadia.ctp.mixin;

import fr.arcadia.ctp.runtime.ConfigurationGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts Create's block configuration packets behind the claim protection they currently skip.
 *
 * <p>Changing a scroll value, a filter or a threshold does not go through
 * {@code PlayerInteractEvent}: the client sends a {@code BlockEntityConfigurationPacket}
 * naming a {@link BlockPos}, and {@code handle} validates only the distance to that
 * position before applying the change. FTB Chunks never sees it, so a player standing
 * outside someone else's claim can retune their machines.
 *
 * <p>Injecting at the head of the base class covers the whole family in one place -
 * {@code ValueSettingsPacket} and every other subclass Create ships or an addon adds -
 * instead of chasing roughly a hundred packet types individually.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket", remap = false)
public abstract class MixinBlockEntityConfigurationPacket {

    @Shadow
    @Final
    protected BlockPos pos;

    @Inject(
        method = "handle(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void arcadiaCtp$guardConfiguration(ServerPlayer player, CallbackInfo ci) {
        if (ConfigurationGuard.shouldReject(this, player, this.pos)) {
            // Cancelling before applySettings leaves the block entity untouched. The client
            // keeps its optimistic value until the next sync, which Create sends on its own.
            ci.cancel();
        }
    }
}

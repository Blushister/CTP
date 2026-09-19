package fr.arcadia.ctp.mixin;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import fr.arcadia.ctp.contraption.ContraptionOwnerHolder;
import fr.arcadia.ctp.runtime.ContraptionGuard;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops a moving Portable Storage Interface from latching onto a stationary one it has
 * no rights to.
 *
 * <p>Guarding {@code visitNewPosition} alone is not enough here: a PSI that has already
 * connected keeps transferring from {@code tick}, and both paths go through
 * {@code findInterface}. Refusing the connection is also the least disruptive place to
 * stop it - the contraption simply passes by without stalling.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceMovement",
    remap = false)
public abstract class MixinPortableStorageInterfaceMovement {

    @Inject(method = "findInterface", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void arcadiaCtp$guardInterface(MovementContext context, BlockPos pos,
                                           CallbackInfoReturnable<Boolean> cir) {
        Contraption contraption = context.contraption;
        if (contraption == null || !(contraption.entity instanceof ContraptionOwnerHolder holder)) {
            return;
        }
        if (!ContraptionGuard.mayVisit(holder, this, context.world, contraption.anchor, pos)) {
            cir.setReturnValue(false);
        }
    }
}

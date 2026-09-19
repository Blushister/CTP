package fr.arcadia.ctp.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import fr.arcadia.ctp.contraption.ContraptionOwnerHolder;
import fr.arcadia.ctp.runtime.ContraptionGuard;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives a contraption an owning team, and stops its actors from working inside claims
 * that team has no rights to.
 *
 * <p>A contraption assembled outside a claim and driven into one can currently break
 * blocks, deploy into them and empty their chests: none of that goes through a player,
 * so FTB Chunks never gets asked. Every actor - drill, saw, harvester, deployer, PSI,
 * and whatever addons register - reaches its target through one call,
 * {@code MovementBehaviour.visitNewPosition}, and that call has exactly two sites, both
 * here. Wrapping it covers the whole surface without touching a single actor class.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.AbstractContraptionEntity", remap = false)
public abstract class MixinAbstractContraptionEntity implements ContraptionOwnerHolder {

    @Shadow
    public abstract Optional<UUID> getControllingPlayer();

    @Unique
    private UUID arcadiaCtp$ownerTeam;

    @Unique
    private boolean arcadiaCtp$ownerResolved;

    @Override
    public UUID arcadiaCtp$ownerTeam() {
        return arcadiaCtp$ownerTeam;
    }

    @Override
    public void arcadiaCtp$setOwnerTeam(UUID teamId) {
        arcadiaCtp$ownerTeam = teamId;
        arcadiaCtp$ownerResolved = true;
    }

    @Override
    public boolean arcadiaCtp$isOwnerResolved() {
        return arcadiaCtp$ownerResolved;
    }

    @Override
    public Optional<UUID> arcadiaCtp$pilot() {
        Optional<UUID> controlling = getControllingPlayer();
        if (controlling.isPresent()) {
            return controlling;
        }
        // Create only fills getControllingPlayer() for contraptions with actual controls
        // (trains, contraption controls). Someone riding a bearing rig or a minecart
        // contraption steers it just as much, and that is the case the fallback exists for.
        for (Entity passenger : ((Entity) (Object) this).getPassengers()) {
            if (passenger instanceof Player rider) {
                return Optional.of(rider.getUUID());
            }
        }
        return Optional.empty();
    }

    @WrapOperation(
        method = {"tickActors", "refreshPSIs"},
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/api/behaviour/movement/MovementBehaviour;"
                + "visitNewPosition(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;"
                + "Lnet/minecraft/core/BlockPos;)V"
        ),
        require = 0,
        remap = false
    )
    private void arcadiaCtp$guardActor(MovementBehaviour behaviour, MovementContext context, BlockPos pos,
                                       Operation<Void> original) {
        Contraption contraption = context.contraption;
        BlockPos anchor = contraption == null ? null : contraption.anchor;
        if (ContraptionGuard.mayVisit(this, behaviour, context.world, anchor, pos)) {
            original.call(behaviour, context, pos);
        }
        // Skipping the visit leaves the actor idle for this position only; the contraption
        // keeps moving, so a machine crossing a claim simply does nothing while inside it.
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"), require = 0, remap = false)
    private void arcadiaCtp$saveOwner(CompoundTag tag, CallbackInfo ci) {
        // Resolution is stored too: "assembled outside any claim" is a real answer, and
        // re-deriving it after a restart would read the claim wherever the machine is now.
        tag.putBoolean("ArcadiaCtpOwnerResolved", arcadiaCtp$ownerResolved);
        if (arcadiaCtp$ownerTeam != null) {
            tag.putUUID("ArcadiaCtpOwnerTeam", arcadiaCtp$ownerTeam);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), require = 0, remap = false)
    private void arcadiaCtp$loadOwner(CompoundTag tag, CallbackInfo ci) {
        arcadiaCtp$ownerResolved = tag.getBoolean("ArcadiaCtpOwnerResolved");
        arcadiaCtp$ownerTeam = tag.hasUUID("ArcadiaCtpOwnerTeam") ? tag.getUUID("ArcadiaCtpOwnerTeam") : null;
    }
}

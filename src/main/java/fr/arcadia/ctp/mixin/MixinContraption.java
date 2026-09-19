package fr.arcadia.ctp.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.contraptions.AssemblyException;
import fr.arcadia.ctp.runtime.AssemblyContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Records the anchor while a contraption gathers its blocks, so the registered
 * {@code BlockMovementChecks} callback can tell whose contraption is asking.
 *
 * <p>The alternative - vetoing inside {@code moveBlock} - would mean shadowing Create's
 * traversal state; the public check exists precisely for this, it just lacks the caller's
 * identity.
 *
 * <p>Wrapping the whole method rather than injecting at HEAD and RETURN: assembly failing
 * with {@link AssemblyException} is a normal outcome (chunk unloaded, block limit reached),
 * and a RETURN injection would leave the thread-local set on that path.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.Contraption", remap = false)
public abstract class MixinContraption {

    @WrapMethod(method = "searchMovedStructure", remap = false)
    private boolean arcadiaCtp$wrapAssembly(Level level, BlockPos pos, Direction direction,
                                            Operation<Boolean> original) throws AssemblyException {
        AssemblyContext.begin(level, pos);
        try {
            return original.call(level, pos, direction);
        } finally {
            AssemblyContext.end();
        }
    }
}

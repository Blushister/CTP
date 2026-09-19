package fr.arcadia.ctp.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Marks the stretch of work during which a contraption is gathering its blocks.
 *
 * <p>Create's veto point for "may this block be moved" is
 * {@code BlockMovementChecks.isMovementAllowed(state, level, pos)}, which is a public
 * extension point but says nothing about which contraption is asking. Assembly runs
 * synchronously on the server thread inside {@code searchMovedStructure}, so recording
 * the anchor for the duration is enough to give the check the missing half.
 */
public final class AssemblyContext {

    private static final ThreadLocal<BlockPos> ANCHOR = new ThreadLocal<>();
    private static final ThreadLocal<Level> LEVEL = new ThreadLocal<>();

    private AssemblyContext() {
    }

    public static void begin(Level level, BlockPos anchor) {
        LEVEL.set(level);
        ANCHOR.set(anchor);
    }

    public static void end() {
        LEVEL.remove();
        ANCHOR.remove();
    }

    /** True when the current thread is inside an assembly whose anchor is known. */
    public static boolean active() {
        return ANCHOR.get() != null && LEVEL.get() != null;
    }

    public static BlockPos anchor() {
        return ANCHOR.get();
    }

    public static Level level() {
        return LEVEL.get();
    }
}

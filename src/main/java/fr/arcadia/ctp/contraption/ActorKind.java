package fr.arcadia.ctp.contraption;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a contraption actor is about to do, so each vector can carry its own policy.
 *
 * <p>Classification is by class name rather than {@code instanceof}: the point of
 * guarding the single shared call site is to cover addon actors too, and those do not
 * extend Create's classes in any predictable way.
 */
public enum ActorKind {

    /** Drill, saw, harvester - anything that removes blocks as the contraption passes. */
    BLOCK_BREAKING,

    /** Deployer: places, breaks and uses items at the visited position. */
    DEPLOYER,

    /** Portable Storage Interface: moves items and fluids in and out of stationary inventories. */
    STORAGE_INTERFACE;

    private static final Map<Class<?>, ActorKind> CACHE = new ConcurrentHashMap<>();

    public static ActorKind of(Object behaviour) {
        return CACHE.computeIfAbsent(behaviour.getClass(), ActorKind::classify);
    }

    private static ActorKind classify(Class<?> type) {
        String name = type.getName();
        if (name.contains("Deployer")) {
            return DEPLOYER;
        }
        if (name.contains("PortableStorageInterface") || name.contains("StorageInterface")) {
            return STORAGE_INTERFACE;
        }
        // Everything else that visits a position is treated as potentially destructive.
        // Erring this way means a new addon actor is covered by default rather than
        // silently exempt until someone notices the hole.
        return BLOCK_BREAKING;
    }
}

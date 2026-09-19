package fr.arcadia.ctp.runtime;

import net.neoforged.fml.ModList;

/**
 * Whether the mods this bridge sits between are actually installed.
 *
 * <p>Resolved once, lazily: {@link ModList} is not populated when this class may first
 * be touched, and the answer cannot change afterwards.
 */
public final class ModPresence {

    private static Boolean ftbChunks;
    private static Boolean ftbTeams;
    private static Boolean create;

    private ModPresence() {
    }

    public static boolean hasFtbChunks() {
        Boolean cached = ftbChunks;
        if (cached == null) {
            cached = ModList.get() != null && ModList.get().isLoaded("ftbchunks");
            ftbChunks = cached;
        }
        return cached;
    }

    /**
     * FTB Teams ships with FTB Chunks but is a separate mod, and the waystone guard reads
     * team membership without any claim involved - so it is asked for separately.
     */
    /**
     * Create is optional since CTP also covers Waystones. The mixins gate themselves on
     * Create's bytecode, but the assembly check is registered through Create's API from
     * plain code, and that class must not even be loaded without Create.
     */
    public static boolean hasCreate() {
        Boolean cached = create;
        if (cached == null) {
            cached = ModList.get() != null && ModList.get().isLoaded("create");
            create = cached;
        }
        return cached;
    }

    public static boolean hasFtbTeams() {
        Boolean cached = ftbTeams;
        if (cached == null) {
            cached = ModList.get() != null && ModList.get().isLoaded("ftbteams");
            ftbTeams = cached;
        }
        return cached;
    }
}

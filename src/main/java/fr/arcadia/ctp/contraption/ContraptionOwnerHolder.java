package fr.arcadia.ctp.contraption;

import java.util.Optional;
import java.util.UUID;

/**
 * The team a contraption acts on behalf of, carried on the contraption entity itself.
 *
 * <p>Implemented by the mixin on {@code AbstractContraptionEntity}, so every contraption
 * entity can be cast to this. A contraption has no player behind it - it is usually
 * started by redstone - so ownership has to be stamped once and persisted rather than
 * derived from whoever happens to be nearby.
 */
public interface ContraptionOwnerHolder {

    /** Owning team, or {@code null} when the contraption was assembled outside any claim. */
    UUID arcadiaCtp$ownerTeam();

    void arcadiaCtp$setOwnerTeam(UUID teamId);

    /** False until ownership has been worked out once; stamping happens on first use. */
    boolean arcadiaCtp$isOwnerResolved();

    /** The player currently steering the contraption, if any - the fallback for unowned ones. */
    Optional<UUID> arcadiaCtp$pilot();
}

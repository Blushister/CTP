package fr.arcadia.ctp.compat;

/**
 * How the acting player relates to the claim covering the target block.
 *
 * <p>Kept free of any FTB type so the guard can reason about it even on a server
 * where FTB Chunks is not installed.
 */
public enum ClaimRelation {

    /** No claim on that chunk, so there is nobody to protect. */
    UNCLAIMED,

    /** Member of the team owning the claim. */
    MEMBER,

    /** Not a member, but allied to the owning team. */
    ALLY,

    /** No relation to the owning team. */
    OUTSIDER,

    /** FTB Chunks is absent or its manager is not loaded yet; nothing can be decided. */
    UNKNOWN
}

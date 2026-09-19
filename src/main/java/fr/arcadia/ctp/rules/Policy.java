package fr.arcadia.ctp.rules;

import java.util.Locale;

/**
 * What a rule decides once it matches a configuration packet.
 *
 * <p>The names are deliberately expressed in terms of the claim owner rather than
 * "allowed"/"denied", because the whole point of the rule engine is to move a given
 * Create setting between protection tiers, not just to toggle it on and off.
 */
public enum Policy {

    /** Always allowed, even for a player with no relation to the claim. */
    ALLOW,

    /**
     * Defers to FTB Chunks, exactly as a normal right-click would.
     * This is the tier that closes the bypass without changing anyone's expectations.
     */
    CHECK,

    /** Only members of the team owning the claim. Ignores the claim's ally/public settings. */
    TEAM_ONLY,

    /** Members of the team owning the claim, plus its allies. */
    ALLY_ONLY,

    /** Never allowed through this packet, whoever asks. Admin bypass still applies. */
    DENY;

    public static Policy parse(String raw, Policy fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}

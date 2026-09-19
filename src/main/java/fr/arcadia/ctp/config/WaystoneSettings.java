package fr.arcadia.ctp.config;

import fr.arcadia.ctp.rules.Policy;

/**
 * Who a "Visible to Team" waystone is offered to.
 *
 * <p>Waystones has no notion of FTB Teams: it indexes TEAM waystones under the owner's
 * <em>vanilla scoreboard team</em> and hands them to everyone sharing that team name.
 * Any mod that buckets players into scoreboard teams for an unrelated purpose - a tab
 * list sorted by permission group, for instance - therefore turns "my team" into "my
 * permission group", and a private waystone lands in strangers' teleport lists.
 *
 * <p>{@link Policy} is read here as an audience rather than a claim decision, because
 * there is no block position to check:
 * <ul>
 *   <li>{@code ALLOW} - leave Waystones alone, scoreboard behaviour and all</li>
 *   <li>{@code CHECK} - no player interaction to defer to, read as {@code TEAM_ONLY}</li>
 *   <li>{@code ALLY_ONLY} - the owner's FTB party, plus teams allied with it</li>
 *   <li>{@code TEAM_ONLY} - the owner's FTB party only (recommended)</li>
 *   <li>{@code DENY} - nobody but the owner, whatever their team</li>
 * </ul>
 *
 * <p>{@code claimInteraction} answers the opposite question - not who sees a waystone,
 * but who may walk up to one standing in a claim and right-click it to register it. A
 * waystone is placed to be used, so this one defaults to {@code ALLOW}; tightening it to
 * {@code ALLY_ONLY} or {@code TEAM_ONLY} turns a base's waystone into a private one, and
 * {@code CHECK} gives the block back to FTB Chunks as if it had never been whitelisted.
 */
public record WaystoneSettings(boolean enabled, Policy teamVisibility, Policy claimInteraction) {

    public static WaystoneSettings defaults() {
        return new WaystoneSettings(true, Policy.TEAM_ONLY, Policy.ALLOW);
    }

    public WaystoneSettings withEnabled(boolean value) {
        return new WaystoneSettings(value, teamVisibility, claimInteraction);
    }

    public WaystoneSettings withTeamVisibility(Policy value) {
        return new WaystoneSettings(enabled, value, claimInteraction);
    }

    public WaystoneSettings withClaimInteraction(Policy value) {
        return new WaystoneSettings(enabled, teamVisibility, value);
    }
}

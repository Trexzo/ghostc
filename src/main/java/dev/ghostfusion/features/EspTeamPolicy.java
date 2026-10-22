package dev.ghostfusion.features;

/**
 * Scoreboard-backed team admission for optional Player ESP suppression.
 * Forge passes live Team objects; identity matters, not display colors,
 * prefixes, untrusted player names or equal-looking labels.
 *
 * A missing team is never considered a shared team, even if both are null.
 * No Minecraft class, server traffic, allocation or world mutation.
 */
public final class EspTeamPolicy {
    private EspTeamPolicy() {}

    public static boolean exclude(Object localTeam,Object targetTeam) {
        return localTeam!=null && localTeam==targetTeam;
    }
}

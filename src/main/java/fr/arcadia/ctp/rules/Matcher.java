package fr.arcadia.ctp.rules;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Glob matching for the string lists in a rule.
 *
 * <p>Patterns support {@code *} (any run of characters) and {@code ?} (one character).
 * Everything else is literal, so a plain {@code create:rotation_speed_controller}
 * behaves like an equality test. Matching is case-insensitive: block ids are already
 * lowercase, but packet class names are not.
 */
public final class Matcher {

    private final String raw;
    private final Pattern pattern;

    private Matcher(String raw, Pattern pattern) {
        this.raw = raw;
        this.pattern = pattern;
    }

    public static Matcher of(String glob) {
        String trimmed = glob.trim();
        return new Matcher(trimmed, Pattern.compile(toRegex(trimmed), Pattern.CASE_INSENSITIVE));
    }

    public boolean matches(String value) {
        return value != null && pattern.matcher(value).matches();
    }

    public String raw() {
        return raw;
    }

    private static String toRegex(String glob) {
        StringBuilder sb = new StringBuilder(glob.length() + 8);
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' || c == '?') {
                if (literal.length() > 0) {
                    sb.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                sb.append(c == '*' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        if (literal.length() > 0) {
            sb.append(Pattern.quote(literal.toString()));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return raw.toLowerCase(Locale.ROOT);
    }
}

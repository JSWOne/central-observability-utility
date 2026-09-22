package com.jswone.observability.masking;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single source of truth for PII redaction in log output.
 *
 * <p>Applied at the appender layer rather than at call sites, so every log statement is covered
 * without each one having to remember to mask. Patterns are deliberately partial-preserving (for
 * example {@code j***@gmail.com}) so logs stay usable for debugging while no longer carrying the
 * full identifier.
 *
 * <p>Framework-neutral: consumed by {@link com.jswone.observability.masking.logback.PiiMaskingMessageConverter}
 * for Logback and {@link com.jswone.observability.masking.log4j2.PiiMaskingRewritePolicy} for Log4j2.
 */
public final class PiiMasker {

    private static final String MASK = "***";

    /** Ordered because the more specific patterns must win over the generic digit runs. */
    private static final List<Rule> RULES =
            Arrays.asList(
                    // JWT / JWS compact serialisation.
                    rule("eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*", "eyJ" + MASK),

                    // Authorization-style bearer / basic credentials.
                    rule("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9\\-._~+/]{8,}={0,2}", "$1 " + MASK),

                    // Email: keep first character and the full domain.
                    rule(
                            "([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})",
                            "$1" + MASK + "$2"),

                    // GSTIN: 15 chars, keep the state code and the check digit.
                    rule(
                            "\\b(\\d{2})[A-Z]{5}\\d{4}[A-Z][A-Z\\d]Z([A-Z\\d])\\b",
                            "$1" + MASK + "$2"),

                    // PAN: 10 chars.
                    rule("\\b[A-Z]{5}\\d{4}[A-Z]\\b", MASK),

                    // Card / Aadhaar style runs of 12-19 digits: keep first 6 and last 4.
                    // The word/hyphen boundaries matter: without them a digit-only UUID group such
                    // as the tail of b1f2c3d4-1111-2222-3333-444455556666 is treated as a card
                    // number, which would corrupt every cart and order id in the logs.
                    rule("(?<![\\w-])(\\d{6})\\d{2,9}(\\d{4})(?![\\w-])", "$1" + MASK + "$2"),

                    // Indian mobile, optionally +91 prefixed: keep leading 3 and trailing 2.
                    rule(
                            "(?<![\\w-])(\\+?91[- ]?)?([6-9]\\d{2})\\d{5}(\\d{2})(?![\\w-])",
                            "$1$2" + MASK + "$3"));

    private PiiMasker() {}

    /** Returns {@code value} with every known PII shape redacted. Null-safe. */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String masked = value;
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern.matcher(masked);
            if (matcher.find()) {
                masked = matcher.reset().replaceAll(rule.replacement);
            }
        }
        return masked;
    }

    private static Rule rule(String regex, String replacement) {
        return new Rule(Pattern.compile(regex), replacement);
    }

    private static final class Rule {
        private final Pattern pattern;
        private final String replacement;

        private Rule(Pattern pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }
    }
}

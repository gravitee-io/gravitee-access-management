/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.model.webprotection;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A single Content Security Policy directive.
 * <p>
 * A trailing {@code ;} is optional and is discarded on parse. Directive names are case-insensitive
 * per the CSP specification, so comparisons should use {@link #canonicalName()}; the name as typed
 * by the operator is preserved in {@link #name()}.
 * <p>
 * Source-list values are not interpreted: everything after the first whitespace run is kept verbatim.
 *
 * @author GraviteeSource Team
 */
public record CspDirective(String name, String value) {

    private static final Pattern VALID_NAME = Pattern.compile("^[a-z0-9-]+$");

    /**
     * Directives that carry no value at all, e.g. {@code upgrade-insecure-requests}.
     */
    private static final Set<String> NO_VALUE_DIRECTIVES = Set.of(
            "upgrade-insecure-requests",
            "block-all-mixed-content"
    );

    /**
     * Directives whose value is optional, e.g. a bare {@code sandbox} is the most restrictive form.
     */
    private static final Set<String> OPTIONAL_VALUE_DIRECTIVES = Set.of(
            "sandbox",
            "trusted-types"
    );

    public static final String REPORT_URI = "report-uri";
    public static final String REPORT_TO = "report-to";

    /**
     * Parses a stored directive entry.
     *
     * @return the parsed directive, or {@code null} if the entry is null, blank, or only a semicolon
     */
    public static CspDirective parse(String entry) {
        if (entry == null) {
            return null;
        }
        final String stripped = stripTrailingSemicolon(entry);
        if (stripped.isEmpty()) {
            return null;
        }
        final String[] parts = stripped.split("[ \t]+", 2);
        return new CspDirective(parts[0], parts.length == 2 ? parts[1].trim() : "");
    }

    /**
     * Trims the entry and discards a single trailing {@code ;}. Any remaining semicolon is an
     * embedded one, which callers may reject.
     */
    public static String stripTrailingSemicolon(String entry) {
        final String trimmed = entry.trim();
        if (trimmed.endsWith(";")) {
            return trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /**
     * The directive name lowercased, for comparison and for keying the policy map.
     */
    public String canonicalName() {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Renders back to the {@code "name value"} storage form, without a trailing semicolon.
     */
    public String render() {
        return value.isEmpty() ? name : name + " " + value;
    }

    public boolean hasValue() {
        return !value.isEmpty();
    }

    /**
     * Whether this directive is one of the report targets Vert.x requires in report-only mode.
     */
    public boolean isReportTarget() {
        final String canonical = canonicalName();
        return REPORT_URI.equals(canonical) || REPORT_TO.equals(canonical);
    }

    /**
     * Whether a value carries a character that cannot be written to an HTTP header.
     */
    public static boolean hasIllegalHeaderCharacter(String value) {
        return value != null && value.chars().anyMatch(c -> (c < 0x20 && c != '\t') || c == 0x7f);
    }

    public static boolean isValidName(String canonicalName) {
        return canonicalName != null && VALID_NAME.matcher(canonicalName).matches();
    }

    /**
     * Whether a value must be supplied. False for directives that take no value, and for those
     * where it is optional.
     */
    public static boolean requiresValue(String canonicalName) {
        return !NO_VALUE_DIRECTIVES.contains(canonicalName) && !OPTIONAL_VALUE_DIRECTIVES.contains(canonicalName);
    }
}

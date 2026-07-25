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
package io.gravitee.am.reporter.elasticsearch.audit;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.IsoFields;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * How often the reporter starts a new index.
 * <p>
 * Shard count follows the retention window rather than audit volume — retaining two years at daily
 * rollover is roughly 730 indices whether the domain wrote 600 million audits or 600 — and every
 * shard costs heap in cluster state and segment metadata. A longer period cuts that count roughly
 * proportionally: weekly by about seven, monthly by about thirty.
 * <p>
 * Every suffix stays date-derived on purpose. AM's audit console always sends a bounded date range,
 * so Elasticsearch can pre-filter almost every shard away before doing real work — measured at 731
 * indices, a 24-hour query skipped 729 of them. Undated rollover indices, as APIM's ILM mode uses,
 * are fewer but wider in time and lose that pruning.
 *
 * @author GraviteeSource Team
 */
public enum IndexRolloverPeriod {

    DAILY(DateTimeFormatter.ofPattern("yyyy.MM.dd")),

    /**
     * ISO-8601 week, paired with the <em>week-based</em> year rather than the calendar year: 1 January
     * 2027 falls in week 53 of 2026, and pairing it with 2027 would name an index for a week that year
     * does not have.
     */
    WEEKLY(new DateTimeFormatterBuilder()
            .appendValue(IsoFields.WEEK_BASED_YEAR, 4)
            .appendLiteral(".w")
            .appendValue(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 2)
            .toFormatter()),

    MONTHLY(DateTimeFormatter.ofPattern("yyyy.MM"));

    /**
     * Pinned to UTC on purpose: the system default zone would scatter the same audit across different
     * indices depending on which node happened to write it.
     */
    private final DateTimeFormatter formatter;

    IndexRolloverPeriod(DateTimeFormatter formatter) {
        this.formatter = formatter.withZone(ZoneOffset.UTC);
    }

    public String suffix(java.time.Instant timestamp) {
        return formatter.format(timestamp);
    }

    /**
     * Resolves the configured value, which reaches us as free text from the reporter's configuration
     * rather than as an enum constant. An unrecognised period fails the reporter rather than silently
     * falling back to daily, because the two would write to different indices.
     */
    public static IndexRolloverPeriod parse(String configured) {
        if (configured == null || configured.isBlank()) {
            return DAILY;
        }
        String normalized = configured.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(period -> period.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "'%s' is not a supported index rollover period. Use one of: %s.".formatted(
                                configured,
                                Arrays.stream(values())
                                        .map(period -> period.name().toLowerCase(Locale.ROOT))
                                        .collect(Collectors.joining(", ")))));
    }
}

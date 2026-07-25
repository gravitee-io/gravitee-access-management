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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The suffix decides which index an audit lands in, so a period whose boundary is off by an instant
 * scatters audits across two indices — or worse, reuses a name a later period would also produce.
 *
 * @author GraviteeSource Team
 */
class IndexRolloverPeriodTest {

    @Test
    void dailySuffixIsTheUtcDate() {
        assertThat(IndexRolloverPeriod.DAILY.suffix(Instant.parse("2026-07-25T13:45:00Z"))).isEqualTo("2026.07.25");
    }

    @Test
    void weeklySuffixIsTheIsoWeek() {
        assertThat(IndexRolloverPeriod.WEEKLY.suffix(Instant.parse("2026-07-25T13:45:00Z"))).isEqualTo("2026.w30");
    }

    @Test
    void monthlySuffixIsTheUtcMonth() {
        assertThat(IndexRolloverPeriod.MONTHLY.suffix(Instant.parse("2026-07-25T13:45:00Z"))).isEqualTo("2026.07");
    }

    @Test
    void dailyRollsOverAtMidnightUtc() {
        assertThat(IndexRolloverPeriod.DAILY.suffix(Instant.parse("2026-07-25T23:59:59.999Z"))).isEqualTo("2026.07.25");
        assertThat(IndexRolloverPeriod.DAILY.suffix(Instant.parse("2026-07-26T00:00:00Z"))).isEqualTo("2026.07.26");
    }

    @Test
    void weeklyRollsOverAtMondayMidnightUtc() {
        // Sunday the 26th closes ISO week 30; Monday the 27th opens week 31
        assertThat(IndexRolloverPeriod.WEEKLY.suffix(Instant.parse("2026-07-26T23:59:59.999Z"))).isEqualTo("2026.w30");
        assertThat(IndexRolloverPeriod.WEEKLY.suffix(Instant.parse("2026-07-27T00:00:00Z"))).isEqualTo("2026.w31");
    }

    /**
     * The case a plain calendar year gets wrong. 1 January 2027 falls in ISO week 53 of week-based
     * year 2026, so pairing the calendar year with an ISO week number would name it {@code 2027.w53} —
     * a week that does not exist in 2027, and one that sorts before the {@code 2027.w01} written days
     * later.
     */
    @Test
    void weeklyUsesTheWeekBasedYearAcrossTheNewYear() {
        assertThat(IndexRolloverPeriod.WEEKLY.suffix(Instant.parse("2026-12-31T12:00:00Z"))).isEqualTo("2026.w53");
        assertThat(IndexRolloverPeriod.WEEKLY.suffix(Instant.parse("2027-01-01T12:00:00Z"))).isEqualTo("2026.w53");
        assertThat(IndexRolloverPeriod.WEEKLY.suffix(Instant.parse("2027-01-04T00:00:00Z"))).isEqualTo("2027.w01");
    }

    @Test
    void monthlyRollsOverAtTheFirstOfTheMonthUtc() {
        assertThat(IndexRolloverPeriod.MONTHLY.suffix(Instant.parse("2026-07-31T23:59:59.999Z"))).isEqualTo("2026.07");
        assertThat(IndexRolloverPeriod.MONTHLY.suffix(Instant.parse("2026-08-01T00:00:00Z"))).isEqualTo("2026.08");
    }

    @Test
    void suffixesResolveInUtcRatherThanTheSystemZone() {
        // 09:30 in Sydney on the 26th is still the 25th in UTC, so nodes in different zones have to agree
        Instant morningInSydney = Instant.parse("2026-07-25T23:30:00Z");
        assertThat(IndexRolloverPeriod.DAILY.suffix(morningInSydney)).isEqualTo("2026.07.25");
        assertThat(IndexRolloverPeriod.WEEKLY.suffix(morningInSydney)).isEqualTo("2026.w30");
        assertThat(IndexRolloverPeriod.MONTHLY.suffix(morningInSydney)).isEqualTo("2026.07");
    }

    /** No two periods may ever produce the same suffix, or a period change would write into old indices. */
    @Test
    void periodsCannotProduceTheSameSuffix() {
        Instant instant = Instant.parse("2026-07-25T13:45:00Z");
        assertThat(Arrays.stream(IndexRolloverPeriod.values()).map(period -> period.suffix(instant)).distinct())
                .hasSize(IndexRolloverPeriod.values().length);
    }

    @ParameterizedTest
    @ValueSource(strings = {"daily", "DAILY", "Daily", " daily "})
    void parsesTheConfiguredValueLeniently(String configured) {
        assertThat(IndexRolloverPeriod.parse(configured)).isEqualTo(IndexRolloverPeriod.DAILY);
    }

    @Test
    void defaultsToDailyWhenNothingIsConfigured() {
        assertThat(IndexRolloverPeriod.parse(null)).isEqualTo(IndexRolloverPeriod.DAILY);
        assertThat(IndexRolloverPeriod.parse("  ")).isEqualTo(IndexRolloverPeriod.DAILY);
    }

    @Test
    void rejectsAnUnknownPeriodByName() {
        assertThatThrownBy(() -> IndexRolloverPeriod.parse("hourly"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hourly")
                .hasMessageContaining("daily");
    }
}

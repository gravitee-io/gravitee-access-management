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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author GraviteeSource Team
 */
class AuditIndexNamesTest {

    @Test
    void derivesTheDailySuffixFromTheAuditTimestamp() {
        assertThat(AuditIndexNames.writeIndex("gravitee-audit", IndexRolloverPeriod.DAILY, Instant.parse("2026-07-25T13:45:00Z")))
                .isEqualTo("gravitee-audit-2026.07.25");
    }

    @Test
    void namesTheWriteIndexForTheConfiguredPeriod() {
        Instant timestamp = Instant.parse("2026-07-25T13:45:00Z");
        assertThat(AuditIndexNames.writeIndex("gravitee-audit", IndexRolloverPeriod.WEEKLY, timestamp))
                .isEqualTo("gravitee-audit-2026.w30");
        assertThat(AuditIndexNames.writeIndex("gravitee-audit", IndexRolloverPeriod.MONTHLY, timestamp))
                .isEqualTo("gravitee-audit-2026.07");
    }

    @Test
    void resolvesTheSuffixInUtcRatherThanTheSystemZone() {
        // 22:30 in Sydney on the 26th is still the 25th in UTC; a node there must not scatter this
        // audit into a different daily index from a node in London
        Instant lateInSydney = Instant.parse("2026-07-25T22:30:00Z");
        assertThat(AuditIndexNames.writeIndex("gravitee-audit", IndexRolloverPeriod.DAILY, lateInSydney))
                .isEqualTo("gravitee-audit-2026.07.25");

        Instant justAfterMidnightUtc = Instant.parse("2026-07-26T00:00:01Z");
        assertThat(AuditIndexNames.writeIndex("gravitee-audit", IndexRolloverPeriod.DAILY, justAfterMidnightUtc))
                .isEqualTo("gravitee-audit-2026.07.26");
    }

    @Test
    void readsAcrossEveryIndex() {
        assertThat(AuditIndexNames.readPattern("gravitee-audit")).isEqualTo("gravitee-audit-*");
    }

    /**
     * The read wildcard does not depend on the period, which is what lets an operator change the
     * period on a deployment that already has data without hiding what was written under the old one.
     */
    @Test
    void readsIndicesWrittenUnderEveryPeriod() {
        Instant timestamp = Instant.parse("2026-07-25T13:45:00Z");
        String prefix = AuditIndexNames.readPattern("gravitee-audit").replace("*", "");

        for (IndexRolloverPeriod period : IndexRolloverPeriod.values()) {
            assertThat(AuditIndexNames.writeIndex("gravitee-audit", period, timestamp))
                    .describedAs("index written under %s must still match the read wildcard", period)
                    .startsWith(prefix);
        }
    }

    @Test
    void acceptsALegalIndexName() {
        assertThatCode(() -> AuditIndexNames.validate("gravitee-audit_2.x+eu")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Gravitee-Audit", "gravitee audit", "-gravitee", "gravitee/audit", "gravitee*audit", ""})
    void rejectsAnIndexNameElasticsearchWouldRefuse(String name) {
        assertThatThrownBy(() -> AuditIndexNames.validate(name))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be used as an Elasticsearch index name")
                .hasMessageContaining("lowercase");
    }

    @Test
    void rejectsAMissingIndexName() {
        assertThatThrownBy(() -> AuditIndexNames.validate(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theReadPatternMatchesTheWriteIndex() {
        String pattern = AuditIndexNames.readPattern("gravitee-audit");
        String written = AuditIndexNames.writeIndex("gravitee-audit", IndexRolloverPeriod.DAILY, Instant.parse("2026-07-25T13:45:00Z"));
        assertThat(written).startsWith(pattern.substring(0, pattern.length() - 1));
    }
}

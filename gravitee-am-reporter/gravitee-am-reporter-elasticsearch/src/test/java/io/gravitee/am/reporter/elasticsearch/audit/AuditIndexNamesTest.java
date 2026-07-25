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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class AuditIndexNamesTest {

    @Test
    void derivesTheDailySuffixFromTheAuditTimestamp() {
        assertThat(AuditIndexNames.writeIndex("gravitee-audit", Instant.parse("2026-07-25T13:45:00Z")))
                .isEqualTo("gravitee-audit-2026.07.25");
    }

    @Test
    void resolvesTheSuffixInUtcRatherThanTheSystemZone() {
        // 22:30 in Sydney on the 26th is still the 25th in UTC; a node there must not scatter this
        // audit into a different daily index from a node in London
        Instant lateInSydney = Instant.parse("2026-07-25T22:30:00Z");
        assertThat(AuditIndexNames.writeIndex("gravitee-audit", lateInSydney)).isEqualTo("gravitee-audit-2026.07.25");

        Instant justAfterMidnightUtc = Instant.parse("2026-07-26T00:00:01Z");
        assertThat(AuditIndexNames.writeIndex("gravitee-audit", justAfterMidnightUtc)).isEqualTo("gravitee-audit-2026.07.26");
    }

    @Test
    void readsAcrossEveryDailyIndex() {
        assertThat(AuditIndexNames.readPattern("gravitee-audit")).isEqualTo("gravitee-audit-*");
    }

    @Test
    void theReadPatternMatchesTheWriteIndex() {
        String pattern = AuditIndexNames.readPattern("gravitee-audit");
        String written = AuditIndexNames.writeIndex("gravitee-audit", Instant.parse("2026-07-25T13:45:00Z"));
        assertThat(written).startsWith(pattern.substring(0, pattern.length() - 1));
    }
}

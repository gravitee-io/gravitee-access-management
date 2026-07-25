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
package io.gravitee.am.service.reporter;

import io.gravitee.am.model.Reporter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ordering has to depend only on reporter data, never on how any one instance happened to build
 * its map, or two management instances can serve the same user from different stores.
 *
 * @author GraviteeSource Team
 */
class AuditReporterSelectionTest {

    @Test
    void ordersOldestFirst() {
        Reporter older = reporter("b", daysAgo(30));
        Reporter newer = reporter("a", daysAgo(1));

        assertThat(sorted(List.of(newer, older))).containsExactly(older, newer);
        assertThat(sorted(List.of(older, newer))).containsExactly(older, newer);
    }

    @Test
    void breaksCreationTimeTiesById() {
        Date sameInstant = daysAgo(30);
        Reporter first = reporter("aaa", sameInstant);
        Reporter second = reporter("bbb", sameInstant);

        assertThat(sorted(List.of(second, first))).containsExactly(first, second);
    }

    @Test
    void reportersWithoutACreationTimeSortLast() {
        Reporter dated = reporter("a", daysAgo(1));
        Reporter undated = reporter("b", null);

        assertThat(sorted(List.of(undated, dated))).containsExactly(dated, undated);
    }

    private static List<Reporter> sorted(List<Reporter> reporters) {
        List<Reporter> copy = new ArrayList<>(reporters);
        copy.sort(AuditReporterSelection.ORDER);
        return copy;
    }

    private static Reporter reporter(String id, Date createdAt) {
        Reporter reporter = new Reporter();
        reporter.setId(id);
        reporter.setCreatedAt(createdAt);
        return reporter;
    }

    private static Date daysAgo(int days) {
        return Date.from(Instant.now().minus(days, ChronoUnit.DAYS));
    }
}

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

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Audits are written to one index per {@link IndexRolloverPeriod} so an operator can expire them with
 * a lifecycle policy, and read back across all of them with a wildcard.
 * <p>
 * Both names come from here, so the write index and the read wildcard cannot drift apart. That is
 * also what lets the rollover period be changed on a deployment that already has data: the wildcard
 * does not mention the period, so indices written under the old one keep answering reads.
 *
 * @author GraviteeSource Team
 */
public final class AuditIndexNames {

    /**
     * Elasticsearch rejects index names that are not lowercase, or that use characters it reserves.
     * Checked up front so an operator who mistypes the index gets told exactly that, rather than a
     * template failure whose message points somewhere else.
     */
    private static final Pattern LEGAL_INDEX_NAME = Pattern.compile("[a-z0-9][a-z0-9_.+-]*");

    private AuditIndexNames() {
    }

    public static void validate(String baseIndex) {
        if (baseIndex == null || baseIndex.isBlank() || !LEGAL_INDEX_NAME.matcher(baseIndex).matches()) {
            throw new IllegalStateException(("'%s' cannot be used as an Elasticsearch index name. Names must be " +
                    "lowercase, start with a letter or digit, and contain only letters, digits, and the characters " +
                    "_ . + and -.").formatted(baseIndex));
        }
    }

    /**
     * The index an audit belongs in, derived from the audit's own timestamp rather than from the
     * clock at index time. Document ids are unique per index, so an audit retried across a period
     * boundary would otherwise land in a second index and duplicate.
     */
    public static String writeIndex(String baseIndex, IndexRolloverPeriod period, Instant auditTimestamp) {
        return baseIndex + "-" + period.suffix(auditTimestamp);
    }

    /** The wildcard every read goes through, covering every index whatever period wrote it. */
    public static String readPattern(String baseIndex) {
        return baseIndex + "-*";
    }
}

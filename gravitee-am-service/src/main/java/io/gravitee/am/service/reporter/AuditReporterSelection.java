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

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;

import java.util.Comparator;
import java.util.Date;
import java.util.function.Predicate;

/**
 * Audit reads resolve to a single reporter, so when a reference has several searchable reporters the
 * winner has to be defined rather than left to map iteration order — otherwise the same user
 * refreshing the audit screen can be served from a different store on each instance.
 * <p>
 * A reporter an administrator added outranks the one AM provisioned with the reference. Every
 * reference is born with a database reporter, created at the same instant as the reference itself, so
 * ordering on age alone means an added reporter can never win a read: audits would be written to both
 * stores while the audit screen kept reading from the database, which is more load and no relief.
 * Choosing to add a searchable reporter is the intent; being handed one at creation is not.
 * <p>
 * An inherited organization reporter can serve a domain's reads, and a reporter added to the domain
 * itself is the more specific expression of intent, so it wins between the two. That tie-break sits
 * below the one above rather than above it: a domain whose only reporters are its auto-provisioned
 * database one and an inherited organization one must resolve to the inherited one, which is the whole
 * point of inheriting a searchable reporter.
 * <p>
 * Beyond that the order is oldest first, tie-broken by id, so a reference with several added
 * reporters still resolves the same way on every instance.
 *
 * @author GraviteeSource Team
 */
public final class AuditReporterSelection {

    /** Order for a set of candidates that all belong to the same reference. */
    public static final Comparator<Reporter> ORDER = order(reporter -> false);

    /**
     * Order for resolving {@code reference}'s reads, where a candidate may belong to the reference
     * itself or be inherited from the organization above it.
     */
    public static Comparator<Reporter> orderFor(Reference reference) {
        return order(reporter -> !reference.equals(reporter.getReference()));
    }

    private static Comparator<Reporter> order(Predicate<Reporter> inherited) {
        return Comparator
                .comparing(Reporter::isSystem)
                .thenComparing(inherited::test)
                .thenComparing(Reporter::getCreatedAt, Comparator.nullsLast(Comparator.<Date>naturalOrder()))
                .thenComparing(Reporter::getId, Comparator.nullsLast(Comparator.<String>naturalOrder()));
    }

    private AuditReporterSelection() {
    }
}

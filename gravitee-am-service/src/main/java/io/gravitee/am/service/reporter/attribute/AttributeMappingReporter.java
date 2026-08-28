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
package io.gravitee.am.service.reporter.attribute;

import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.ReporterAttributeMapping;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.Reportable;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.provider.ReportableCriteria;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.gravitee.common.component.Lifecycle;
import lombok.CustomLog;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Applies one reporter's configured attribute mappings to each audit on its way to that reporter.
 *
 * <p>Wraps a single reporter provider, so reporters on the same reference each export only their own
 * fields.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public class AttributeMappingReporter<R extends Reportable, C extends ReportableCriteria> implements Reporter<R, C> {

    private final Reporter<R, C> delegate;
    private final List<ReporterAttributeMapping> mappings;
    private final ReporterAttributeResolver resolver;

    public static <R extends Reportable, C extends ReportableCriteria> Reporter<R, C> decorate(
            Reporter<R, C> delegate, List<ReporterAttributeMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return delegate;
        }
        return new AttributeMappingReporter<>(delegate, mappings);
    }

    public AttributeMappingReporter(Reporter<R, C> delegate, List<ReporterAttributeMapping> mappings) {
        this(delegate, mappings, new ReporterAttributeResolver());
    }

    AttributeMappingReporter(Reporter<R, C> delegate, List<ReporterAttributeMapping> mappings, ReporterAttributeResolver resolver) {
        this.delegate = delegate;
        this.mappings = mappings;
        this.resolver = resolver;
    }

    @Override
    public void report(io.gravitee.reporter.api.Reportable reportable) {
        if (reportable instanceof Audit audit) {
            Map<String, Object> resolved;
            // Enrichment must never cost the event.
            try {
                resolved = resolver.resolve(mappings, audit);
            } catch (Exception ex) {
                log.warn("Unable to resolve attribute mappings for audit {}, reporting it unenriched", audit.getId(), ex);
                delegate.report(reportable);
                return;
            }
            if (!resolved.isEmpty()) {
                // The event bus hands the same Audit instance to every reporter on the reference.
                Audit enriched = new Audit(audit);
                enriched.setCustomAttributes(resolved);
                delegate.report(enriched);
                return;
            }
        }
        delegate.report(reportable);
    }

    @Override
    public boolean canHandle(io.gravitee.reporter.api.Reportable reportable) {
        return delegate.canHandle(reportable);
    }

    @Override
    public Single<Page<R>> search(ReferenceType referenceType, String referenceId, C criteria, int page, int size) {
        return delegate.search(referenceType, referenceId, criteria, page, size);
    }

    @Override
    public Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId, C criteria, Type analyticsType) {
        return delegate.aggregate(referenceType, referenceId, criteria, analyticsType);
    }

    @Override
    public Maybe<R> findById(ReferenceType referenceType, String referenceId, String id) {
        return delegate.findById(referenceType, referenceId, id);
    }

    @Override
    public boolean canSearch() {
        return delegate.canSearch();
    }

    @Override
    public Completable purgeExpiredData() {
        return delegate.purgeExpiredData();
    }

    @Override
    public Completable purgeExpiredData(Instant deadline) {
        return delegate.purgeExpiredData(deadline);
    }

    @Override
    public Lifecycle.State lifecycleState() {
        return delegate.lifecycleState();
    }

    @Override
    public Reporter<R, C> start() throws Exception {
        delegate.start();
        return this;
    }

    @Override
    public Reporter<R, C> stop() throws Exception {
        delegate.stop();
        return this;
    }
}

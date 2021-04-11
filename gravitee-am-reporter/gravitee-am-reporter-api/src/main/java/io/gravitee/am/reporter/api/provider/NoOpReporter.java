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
package io.gravitee.am.reporter.api.provider;

import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.common.component.Lifecycle;
import io.gravitee.reporter.api.Reportable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class NoOpReporter implements AuditReporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoOpReporter.class);

    @Override
    public Single<Page<Audit>> search(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, int page, int size) {
        LOGGER.debug("NoOp Reporter call, real reporter not yet bootstrapped");
        return Single.just(new Page<>(Collections.emptyList(), page, size));
    }

    @Override
    public Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, Type analyticsType) {
        LOGGER.debug("NoOp Reporter call, real reporter not yet bootstrapped");
        switch (analyticsType) {
            case DATE_HISTO:
                // just fill with default values mainly for UI purpose
                String fieldSuccess = (criteria.types().get(0) + "_" + Status.SUCCESS).toLowerCase();
                String fieldFailure = (criteria.types().get(0) + "_" + Status.FAILURE).toLowerCase();
                Map<Object, Object> result = new HashMap<>();
                result.put(fieldSuccess, new ArrayList<>(Collections.nCopies(25, 0l)));
                result.put(fieldFailure, new ArrayList<>(Collections.nCopies(25, 0l)));
                return Single.just(result);
            case GROUP_BY:
                return Single.just(Collections.emptyMap());
            case COUNT:
                return Single.just(Collections.singletonMap("data", 0l));
            default:
                return Single.error(new IllegalArgumentException("Analytics [" + analyticsType + "] cannot be calculated"));
        }
    }

    @Override
    public Maybe<Audit> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("NoOp Reporter call, real reporter not yet bootstrapped");
        return Maybe.empty();
    }

    @Override
    public boolean canSearch() {
        return true;
    }

    @Override
    public void report(Reportable reportable) {
        LOGGER.debug("NoOp Reporter call, real reporter not yet bootstrapped");
    }

    @Override
    public Lifecycle.State lifecycleState() {
        return Lifecycle.State.INITIALIZED;
    }

    @Override
    public Object start() throws Exception {
        return this;
    }

    @Override
    public Object stop() throws Exception {
        return this;
    }
}

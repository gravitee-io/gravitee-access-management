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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.management.service.AuditReporterManager;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;

import java.util.Collection;
import java.util.List;

/**
 * A reporter's stored configuration says nothing about which one is answering audit queries or whether
 * it started successfully. Both are resolved per request against the running reporters, and both are
 * needed by the domain and organization list endpoints as well as their single-reporter GETs — which is
 * why this lives in one place rather than four.
 *
 * @author GraviteeSource Team
 */
public final class ReporterRuntimeState {

    private ReporterRuntimeState() {
    }

    public static void mark(AuditReporterManager auditReporterManager, Reference reference, Reporter reporter) {
        mark(auditReporterManager, reference, List.of(reporter));
    }

    public static void mark(AuditReporterManager auditReporterManager, Reference reference, Collection<Reporter> reporters) {
        String readSourceId = auditReporterManager.getReadSourceId(reference).orElse(null);
        reporters.forEach(reporter -> {
            reporter.setReadSource(reporter.getId() != null && reporter.getId().equals(readSourceId));
            reporter.setStatus(auditReporterManager.getStatus(reporter.getId()));
        });
    }
}

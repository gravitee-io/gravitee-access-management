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
package io.gravitee.am.management.service;

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.gravitee.common.service.Service;
import io.reactivex.rxjava3.core.Maybe;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AuditReporterManager extends Service<AuditReporterManager> {

    default Maybe<Reporter> getReporter(ReferenceType referenceType, String referenceId) {
        return getReporter(new Reference(referenceType, referenceId));
    }

    Maybe<Reporter> getReporter(Reference domain);
}
